package com.fdzaki.adshield.vpn.dns

import android.os.ParcelFileDescriptor
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.DnsCache
import com.fdzaki.adshield.util.Constants
import com.fdzaki.adshield.vpn.DnsPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService

/**
 * Reads raw DNS packets off the tun interface and routes each one to
 * block / cache-hit / upstream-forward. Extracted from AdBlockVpnService
 * (v3.17.0 God-Class refactor, see PROJECT_STATE.md) — ZERO behavior
 * change from the pre-refactor inline `runPacketLoop()`.
 *
 * MUST be invoked on the dedicated `loopExecutor` thread (single-thread,
 * never shared with [forwardExecutor]) — see PROJECT_STATE.md incident log
 * 2026-08-03 / decision #11 for why a shared executor here silently broke
 * DNS resolution for every non-blocked domain.
 */
class DnsPacketLoop(
    private val blocklist: BlocklistManager,
    private val whitelistChecker: AppUidWhitelistChecker,
    private val forwarder: UpstreamForwarder,
    private val forwardExecutor: ExecutorService,
    private val isRunning: () -> Boolean,
    // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): plain lambda
    // reading ScreenStateMonitor.isScreenOff, same @Volatile-read idiom as
    // isRunning above — zero cost on the hot path when screen is on.
    private val isScreenOff: () -> Boolean,
    private val onQueryHandled: (domain: String, blocked: Boolean, backgroundApp: String?) -> Unit,
) {

    /** Blocks the calling thread until [isRunning] goes false or the tun fd errors out. */
    fun run(iface: ParcelFileDescriptor) {
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buffer = ByteArray(Constants.VPN_MTU)

        while (isRunning()) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (isRunning()) continue else break
            }
            if (length <= 0) continue

            val packetCopy = buffer.copyOf(length)
            val query = DnsPacket.parse(packetCopy, length) ?: continue

            // Whitelisted apps bypass blocking entirely. Only pay the UID
            // lookup cost when at least one app is actually whitelisted —
            // this keeps the hot path cheap for the common case (no whitelist).
            val bypassForWhitelistedApp = blocklist.hasWhitelistedApps() && whitelistChecker.isFromWhitelistedApp(query)

            val blocked = !bypassForWhitelistedApp && blocklist.isBlocked(query.queryDomain)

            // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): only pay
            // the uid->package resolution cost when the screen is actually
            // off — that's the only case this feature cares about, and it
            // keeps the common (screen-on) hot path exactly as cheap as
            // before this feature existed.
            val backgroundApp = if (isScreenOff()) whitelistChecker.resolvePackageName(query) else null

            if (blocked) {
                writeBlockedResponse(output, query)
                onQueryHandled(query.queryDomain, true, backgroundApp)
            } else {
                // v3.7.0 DNS cache: serve straight from the packet-loop thread
                // on a hit — no executor hop, no socket round-trip. Falls
                // through to the normal async-forward path on a miss.
                val cached = DnsCache.get(query.queryDomain, DnsPacket.qtypeOf(query))
                if (cached != null) {
                    writeCachedResponse(output, query, cached)
                    onQueryHandled(query.queryDomain, false, backgroundApp)
                } else {
                    // Fire-and-forget async forward so a slow upstream lookup never
                    // stalls the packet loop for other concurrent queries. Must use
                    // forwardExecutor (NOT loopExecutor) — see field comment above.
                    forwardExecutor.execute { forwarder.forwardToUpstream(query, output) }
                    onQueryHandled(query.queryDomain, false, backgroundApp)
                }
            }
        }

        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    private fun writeBlockedResponse(output: FileOutputStream, query: DnsPacket.ParsedQuery) {
        try {
            val response = DnsPacket.buildBlockedResponse(query)
            synchronized(output) { output.write(response) }
        } catch (_: Exception) {
            // Dropping the query is an acceptable fallback: the requesting app
            // simply sees the DNS lookup time out, same net effect as blocked.
        }
    }

    /** Writes a [DnsCache] hit back to the app, re-stamped with THIS query's transaction ID
     *  (the cached bytes were captured under whichever query first populated that cache entry). */
    private fun writeCachedResponse(output: FileOutputStream, query: DnsPacket.ParsedQuery, cachedMessage: ByteArray) {
        try {
            val restamped = DnsPacket.withTransactionId(cachedMessage, query.dnsTransactionId)
            val responsePacket = DnsPacket.wrapUpstreamReply(query, restamped)
            synchronized(output) { output.write(responsePacket) }
        } catch (_: Exception) {
            // Fall back to a real upstream forward rather than dropping the query outright.
            forwardExecutor.execute { forwarder.forwardToUpstream(query, output) }
        }
    }
}

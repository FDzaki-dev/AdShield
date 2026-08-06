package com.fdzaki.adshield.vpn.dns

import android.net.VpnService
import com.fdzaki.adshield.data.DnsCache
import com.fdzaki.adshield.util.Constants
import com.fdzaki.adshield.vpn.DnsPacket
import com.fdzaki.adshield.vpn.DohClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * DNS prefetch / cache warm-up (v3.9.0 — Internet Surfing Optimization,
 * batch 2). Extracted from AdBlockVpnService (v3.17.0 God-Class refactor,
 * see PROJECT_STATE.md) — ZERO behavior change, same delay/gap timing,
 * same dedicated protect()'d socket separate from [UpstreamForwarder]'s
 * pooled sockets, same silent-failure-per-domain semantics.
 *
 * Resolves [Constants.POPULAR_PREFETCH_DOMAINS] in the background shortly
 * after startup and seeds [DnsCache] with the answers, so the first REAL
 * query for any of them from an app is already a cache hit instead of a
 * cold upstream round-trip.
 *
 * Deliberately does NOT check the blocklist first: skipping that check
 * removes a startup-ordering dependency on the blocklist-load coroutine in
 * AdBlockVpnService.startVpn(), and is harmless either way — a cached
 * answer for a domain that turns out to be blocked is simply never read,
 * because [DnsPacketLoop] always checks the blocklist BEFORE it ever looks
 * at [DnsCache].
 */
class DnsPrefetcher(
    private val vpnService: VpnService,
    private val scope: CoroutineScope,
    private val isRunning: () -> Boolean,
) {

    /** Kicks off the background prefetch pass. Fire-and-forget, same as the
     *  original inline call site in AdBlockVpnService.startVpn(). */
    fun start() {
        scope.launch(Dispatchers.IO) {
            delay(Constants.PREFETCH_START_DELAY_MS)
            if (!isRunning()) return@launch
            val socket = try {
                DatagramSocket().also { vpnService.protect(it); it.soTimeout = 1500 }
            } catch (_: Exception) {
                return@launch
            }
            try {
                for (domain in Constants.POPULAR_PREFETCH_DOMAINS) {
                    if (!isRunning()) break
                    if (DnsCache.get(domain, DNS_QTYPE_A) == null) {
                        runCatching { prefetchOne(socket, domain) }
                    }
                    delay(Constants.PREFETCH_QUERY_GAP_MS)
                }
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    /** One prefetch lookup for a single domain, reusing the same resolver-fallback
     *  order as real queries. Silent on any failure — a missed prefetch just means
     *  that domain's first real query pays the normal upstream cost, same as today. */
    private fun prefetchOne(socket: DatagramSocket, domain: String) {
        // Fixed marker transaction ID: this reply is only ever consumed by
        // extractCacheableTtlSeconds()/DnsCache.put() below, never written back
        // to any app on the tun interface, so it doesn't need to match anything.
        val txId = byteArrayOf(0x50, 0x50)
        val request = DnsPacket.buildQueryMessage(domain, DNS_QTYPE_A, txId)

        // v3.11.0: try DoH first here too, same reasoning as UpstreamForwarder
        // -- on a network where plain UDP:53 is dead, the plain-UDP loop below
        // would otherwise silently fail every prefetch attempt forever.
        try {
            val dohReply = DohClient.resolve(vpnService, request)
            if (dohReply != null) {
                DnsPacket.extractCacheableTtlSeconds(dohReply)?.let { ttl ->
                    DnsCache.put(domain, DNS_QTYPE_A, dohReply, ttl)
                }
                return
            }
        } catch (_: Exception) {
            // fall through to plain-UDP below
        }

        val replyBuf = ByteArray(1500)
        for (server in Constants.UPSTREAM_DNS_SERVERS) {
            try {
                socket.send(DatagramPacket(request, request.size, InetSocketAddress(server, Constants.DNS_PORT)))
                val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                socket.receive(replyPacket)
                val message = replyBuf.copyOf(replyPacket.length)
                DnsPacket.extractCacheableTtlSeconds(message)?.let { ttl ->
                    DnsCache.put(domain, DNS_QTYPE_A, message, ttl)
                }
                return
            } catch (_: java.net.SocketTimeoutException) {
                // try next resolver
            } catch (_: java.io.IOException) {
                // try next resolver
            }
        }
    }

    companion object {
        /** DNS QTYPE A (IPv4 host address) — used by the prefetch pass, which only ever asks for A records. */
        private const val DNS_QTYPE_A = 1
    }
}

package com.fdzaki.adshield.vpn.dns

import android.net.VpnService
import com.fdzaki.adshield.data.DnsCache
import com.fdzaki.adshield.util.Constants
import com.fdzaki.adshield.vpn.DnsPacket
import com.fdzaki.adshield.vpn.DohClient
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Resolves DNS queries against upstream (non-blocked) — extracted from
 * AdBlockVpnService (v3.17.0 God-Class refactor, see PROJECT_STATE.md).
 * ZERO behavior change from the pre-refactor inline implementation: same
 * DoH-first-then-plain-UDP order, same per-worker-thread socket pooling,
 * same resolver fallback chain, same caching rules.
 *
 * [vpnService] is held only to call [VpnService.protect] and to satisfy
 * [DohClient.resolve]'s signature — this class is created fresh in
 * AdBlockVpnService.onCreate() and lives exactly as long as the Service
 * instance that owns it, same lifetime the old inline fields had.
 */
class UpstreamForwarder(private val vpnService: VpnService) {

    // Perf (v3.6.0, see PROJECT_STATE.md decision #11b): one persistent UDP
    // socket per forwardExecutor worker thread instead of creating/protect()ing/
    // destroying a new DatagramSocket for every single forwarded DNS query.
    // Safe without any demux logic because each worker thread only ever
    // touches its OWN thread-local socket, synchronously, one query at a
    // time — no cross-thread sharing, so no risk of mismatched replies.
    // openUpstreamSockets tracks every live socket so closeAllSockets() can
    // close them all deterministically instead of leaking them for the
    // lifetime of the (never-shutdown) forwardExecutor threads.
    private val upstreamSocket = ThreadLocal<DatagramSocket>()
    private val openUpstreamSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<DatagramSocket>()

    /** Forwards [query] upstream and writes the reply back onto [output].
     *  Must be invoked from AdBlockVpnService's forwardExecutor, NEVER from
     *  the packet-loop thread — this call blocks on socket I/O. */
    fun forwardToUpstream(query: DnsPacket.ParsedQuery, output: FileOutputStream) {
        val dnsRequest = buildForwardedRequest(query)

        // v3.11.0 (see PROJECT_STATE.md): DoH tried FIRST, per user decision
        // (2026-08-05) after their network was confirmed to break plain
        // UDP:53 entirely. Falls through to the existing plain-UDP resolver
        // chain below only if every DoH endpoint fails -- so a network where
        // DoH itself is blocked (but plain UDP works) still functions.
        try {
            val dohReply = DohClient.resolve(vpnService, dnsRequest)
            if (dohReply != null) {
                val responsePacket = DnsPacket.wrapUpstreamReply(query, dohReply)
                synchronized(output) { output.write(responsePacket) }
                DnsPacket.extractCacheableTtlSeconds(dohReply)?.let { ttl ->
                    DnsCache.put(query.queryDomain, DnsPacket.qtypeOf(query), dohReply, ttl)
                }
                return
            }
        } catch (_: Exception) {
            // fall through to plain-UDP below
        }

        try {
            val socket = getOrCreateUpstreamSocket()
            val replyBuf = ByteArray(1500)

            // Try each configured resolver in order; if the first one (e.g.
            // Cloudflare) times out or is unreachable, fall back to the next
            // (e.g. Google) instead of just dropping the query. A dropped
            // query looks identical to a blocked one from the requesting
            // app's point of view — this is what stops a flaky/blocked
            // upstream from masquerading as false-positive ad-blocking.
            // Same socket is reused across resolver attempts within one
            // query, exactly as before this pooling change — only the
            // socket's LIFETIME changed (now spans many queries), not the
            // per-query fallback sequence.
            for (server in Constants.UPSTREAM_DNS_SERVERS) {
                try {
                    val upstream = InetSocketAddress(server, Constants.DNS_PORT)
                    socket.send(DatagramPacket(dnsRequest, dnsRequest.size, upstream))

                    val replyPacket = DatagramPacket(replyBuf, replyBuf.size)
                    socket.receive(replyPacket)

                    val upstreamMessage = replyBuf.copyOf(replyPacket.length)
                    val responsePacket = DnsPacket.wrapUpstreamReply(query, upstreamMessage)
                    synchronized(output) { output.write(responsePacket) }

                    // v3.7.0: cache positive answers only (extractCacheableTtlSeconds
                    // returns null for non-zero RCODE / zero answers) so a real
                    // NXDOMAIN or SERVFAIL is never masked by a stale cache hit.
                    DnsPacket.extractCacheableTtlSeconds(upstreamMessage)?.let { ttl ->
                        DnsCache.put(query.queryDomain, DnsPacket.qtypeOf(query), upstreamMessage, ttl)
                    }
                    return
                } catch (_: java.net.SocketTimeoutException) {
                    // this resolver didn't answer in time, try the next one
                } catch (_: java.io.IOException) {
                    // network hiccup reaching this resolver, try the next one
                }
            }
            // All configured resolvers failed: drop the query. The
            // requesting app's own DNS client will retry, same as any
            // ordinary network hiccup — this is not a block, just silence.
        } catch (_: Exception) {
            // Socket creation/protect() failure, or the pooled socket ended up
            // in a bad state — discard it so the next query on this thread
            // gets a fresh one instead of repeatedly failing on a broken socket.
            discardUpstreamSocket()
        }
    }

    /** Returns this worker thread's persistent upstream socket, creating +
     *  protect()ing a fresh one if this thread doesn't have one yet (or its
     *  previous one was closed/discarded after an error). */
    private fun getOrCreateUpstreamSocket(): DatagramSocket {
        val existing = upstreamSocket.get()
        if (existing != null && !existing.isClosed) return existing

        val socket = DatagramSocket()
        vpnService.protect(socket) // exclude this socket from the VPN's own routing (avoid loop)
        socket.soTimeout = 2500
        upstreamSocket.set(socket)
        openUpstreamSockets.add(socket)
        return socket
    }

    private fun discardUpstreamSocket() {
        upstreamSocket.get()?.let { socket ->
            runCatching { socket.close() }
            openUpstreamSockets.remove(socket)
        }
        upstreamSocket.remove()
    }

    /** Closes every pooled upstream socket across all forwardExecutor worker
     *  threads. Called from AdBlockVpnService.stopVpn() so sockets don't sit
     *  open for the lifetime of the (never-shutdown) forwardExecutor threads
     *  after protection is turned off — getOrCreateUpstreamSocket() transparently
     *  makes a fresh one next time a thread needs it (e.g. after the VPN restarts). */
    fun closeAllSockets() {
        openUpstreamSockets.forEach { runCatching { it.close() } }
        openUpstreamSockets.clear()
    }

    private fun buildForwardedRequest(query: DnsPacket.ParsedQuery): ByteArray {
        // Reconstruct just the DNS message (header ID + standard flags + question)
        val buf = java.nio.ByteBuffer.allocate(12 + query.rawDnsQuestionSection.size)
        buf.put(query.dnsTransactionId)
        buf.putShort(0x0100) // standard query, recursion desired
        buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
        buf.put(query.rawDnsQuestionSection)
        return buf.array().copyOf(buf.position())
    }
}

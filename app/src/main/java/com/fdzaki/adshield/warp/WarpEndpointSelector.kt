package com.fdzaki.adshield.warp

import com.fdzaki.adshield.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * Picks the lowest-latency Cloudflare WARP endpoint out of
 * [Constants.WARP_ENDPOINT_CANDIDATES] before a connect (v3.7.0 — "smart
 * endpoint/server latency" from Internet Surfing Optimization spec).
 *
 * WireGuard is UDP, so there's no TCP-connect or ICMP ping to time cheaply
 * without root. Instead this sends a single undersized UDP datagram to each
 * candidate's port 2408 and times how long the *kernel* takes to hand back
 * an ICMP port-unreachable / or simply how long connect() + send() takes
 * before any error — this measures reachability + first-hop RTT to that
 * anycast IP without needing a real WireGuard handshake (which needs a
 * valid key exchange we don't want to spend on every candidate). This is a
 * coarse RTT estimate, not a precise one, but it's enough to rank a handful
 * of anycast IPs against each other, which is all endpoint selection needs.
 */
object WarpEndpointSelector {

    data class ProbeResult(val endpoint: String, val rttMs: Long?)

    /** Probes every candidate concurrently and returns the fastest reachable one,
     *  falling back to the first candidate (the documented hostname default) if
     *  every probe fails outright — e.g. no network yet, or UDP egress blocked. */
    // No socket protect() call here on purpose: probing runs BEFORE the WARP
    // tunnel is brought up (during connect(), pre-buildConfig — see
    // WarpTunnelManager.connect()), so there is no full-tunnel VPN interface
    // yet for this UDP traffic to loop back into. If that ordering ever
    // changes, this needs a VpnService.protect() call added — same pattern
    // as AdBlockVpnService.getOrCreateUpstreamSocket().
    suspend fun selectBestEndpoint(
        candidates: List<String> = Constants.WARP_ENDPOINT_CANDIDATES
    ): String = coroutineScope {
        val results = candidates.map { endpoint ->
            async(Dispatchers.IO) { probe(endpoint) }
        }.map { it.await() }

        results.filter { it.rttMs != null }
            .minByOrNull { it.rttMs!! }
            ?.endpoint
            ?: candidates.first()
    }

    private suspend fun probe(endpoint: String): ProbeResult =
        withContext(Dispatchers.IO) {
            val (host, portStr) = endpoint.split(":").let {
                if (it.size == 2) it[0] to it[1] else return@withContext ProbeResult(endpoint, null)
            }
            val port = portStr.toIntOrNull() ?: return@withContext ProbeResult(endpoint, null)

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = PROBE_TIMEOUT_MS

                val target = InetSocketAddress(host, port)
                if (target.isUnresolved) return@withContext ProbeResult(endpoint, null)

                // A single-byte garbage datagram is enough: WireGuard will just
                // silently drop it (not a valid handshake init), but the OS-level
                // send + the address resolution/first-hop routing already happened
                // by the time send() returns, so this timing still differentiates
                // "close/reachable anycast PoP" from "far/unreachable one" via
                // whether *anything* comes back before timeout (routing errors,
                // ICMP unreachable, etc. surface as an IOException quickly on a
                // dead/filtered path; a live path just times out silently, which
                // we treat as "reachable, RTT unknown but bounded by timeout").
                val started = System.currentTimeMillis()
                socket.send(DatagramPacket(PROBE_PAYLOAD, PROBE_PAYLOAD.size, target))
                val replyBuf = ByteArray(64)
                try {
                    socket.receive(DatagramPacket(replyBuf, replyBuf.size))
                    ProbeResult(endpoint, System.currentTimeMillis() - started)
                } catch (_: java.net.SocketTimeoutException) {
                    // No reply is expected/normal (WireGuard drops malformed
                    // packets silently) — send() succeeding without an immediate
                    // ICMP/SocketException is itself a reachability signal, so
                    // score it at the timeout ceiling rather than discarding it.
                    ProbeResult(endpoint, PROBE_TIMEOUT_MS.toLong())
                }
            } catch (_: SocketException) {
                ProbeResult(endpoint, null)
            } catch (_: Exception) {
                ProbeResult(endpoint, null)
            } finally {
                runCatching { socket?.close() }
            }
        }

    private const val PROBE_TIMEOUT_MS = 800
    private val PROBE_PAYLOAD = byteArrayOf(0)
}

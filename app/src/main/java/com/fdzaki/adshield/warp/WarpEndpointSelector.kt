package com.fdzaki.adshield.warp

import com.fdzaki.adshield.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * candidate host:port and times how long the *kernel* takes to hand back
 * an ICMP port-unreachable / or simply how long connect() + send() takes
 * before any error — this measures reachability + first-hop RTT to that
 * anycast IP without needing a real WireGuard handshake (which needs a
 * valid key exchange we don't want to spend on every candidate). This is a
 * coarse RTT estimate, not a precise one, but it's enough to rank a handful
 * of anycast IPs against each other, which is all endpoint selection needs.
 *
 * v3.27.0: candidates now cover 4 ports per anycast IP (2408 + Cloudflare's
 * documented fallback ports 500/1701/4500 — see Constants.WARP_FALLBACK_PORTS),
 * not just 2408. All still probed concurrently below, so covering more ports
 * costs zero extra wall-clock time (bounded by the same PROBE_TIMEOUT_MS),
 * and the ranking naturally rewards whichever port+IP combo is actually
 * unblocked on the current network — the port-camouflage benefit falls out
 * of the existing "pick the fastest reachable one" logic for free.
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
    // as com.fdzaki.adshield.vpn.dns.UpstreamForwarder's socket pooling.
    //
    // v3.28.2 — SELECT_HARD_TIMEOUT_MS wrapper (root cause of the "Kualitas
    // koneksi: Belum diperiksa" freeze, superseding the v3.28.0/v3.28.1 theory
    // that pinned it on probeTrace()/performHealthCheck()). `probe()`'s
    // `InetSocketAddress(host, port)` call (one of the 6 anycast hosts is the
    // literal hostname "engage.cloudflareclient.com", not an IP) does a
    // *blocking DNS resolution* that `socket.soTimeout` never covers (that
    // field only bounds the later `socket.receive()`) — the exact same class
    // of unbounded-DNS-phase bug already fixed once in DohClient/probeTrace.
    // `selectEndpointAndMtu()` awaits this function synchronously, BEFORE
    // `startWatchdog()` is ever reached in `connect()` — so when this hangs,
    // the watchdog/health-check loop never starts at all, which is why fixing
    // probeTrace()'s own timeout (v3.28.0/v3.28.1) never touched the actual
    // freeze. Same caveat as v3.28.0's fix applies here too: `withTimeoutOrNull`
    // only cancels coroutine bookkeeping, NOT the blocking native DNS call
    // underneath — a stuck `probe()` job's IO thread is abandoned (leaked)
    // rather than truly interrupted when this timeout fires, since the JVM/
    // Android `InetAddress` resolution path has no cooperative cancel API.
    // Accepted trade-off: `connect()` provably returns within bounded time
    // instead of hanging forever, at the cost of a rare stray IO-dispatcher
    // thread that outlives this call (dies on its own once the OS-level DNS
    // query eventually times out or fails).
    suspend fun selectBestEndpoint(
        candidates: List<String> = Constants.WARP_ENDPOINT_CANDIDATES
    ): String {
        val fastest = withTimeoutOrNull(SELECT_HARD_TIMEOUT_MS) {
            coroutineScope {
                val results = candidates.map { endpoint ->
                    async(Dispatchers.IO) { probe(endpoint) }
                }.map { it.await() }

                results.filter { it.rttMs != null }.minByOrNull { it.rttMs!! }?.endpoint
            }
        }
        return fastest ?: candidates.first()
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

    /** Outer ceiling for the WHOLE selectBestEndpoint() call (v3.28.2), including any
     *  blocking DNS resolution inside probe()'s InetSocketAddress construction — see
     *  the long comment above selectBestEndpoint() for why this exists. Higher than
     *  PROBE_TIMEOUT_MS since that only bounds the post-resolution receive() phase. */
    private const val SELECT_HARD_TIMEOUT_MS = 3000L

    private val PROBE_PAYLOAD = byteArrayOf(0)
}

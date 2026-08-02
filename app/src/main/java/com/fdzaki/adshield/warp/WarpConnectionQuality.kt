package com.fdzaki.adshield.warp

/**
 * Snapshot of WARP tunnel health, refreshed periodically by
 * [WarpTunnelManager]'s internal watchdog while the tunnel is meant to be up.
 *
 * This is intentionally separate from [com.wireguard.android.backend.Tunnel.State]:
 * the WireGuard library only tells us the *interface* came up (keys
 * exchanged, routes installed) — it says nothing about whether packets are
 * actually reaching Cloudflare. `trafficConfirmed` is the real signal: it
 * comes from probing `https://www.cloudflare.com/cdn-cgi/trace`, which
 * Cloudflare itself annotates with `warp=on` only when the request really
 * arrived via a WARP edge.
 */
data class WarpConnectionQuality(
    /** Round-trip time of the last successful trace probe, in ms. Null until the first probe completes. */
    val latencyMs: Long? = null,
    /** True only when the last probe got a response AND Cloudflare's trace endpoint confirmed `warp=on`. */
    val trafficConfirmed: Boolean = false,
    /** Cumulative bytes received/sent on the tunnel, from GoBackend.getStatistics() (0 if never queried). */
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    /** Wall-clock time (System.currentTimeMillis()) of the last probe attempt, success or failure. 0 = never checked. */
    val lastCheckedAt: Long = 0L,
    /** How many consecutive probes have failed outright (timeout/exception) — reset to 0 on any successful probe. */
    val consecutiveFailures: Int = 0,
    /** How many auto-reconnect attempts have happened in the current connect() session — reset on fresh manual connect. */
    val reconnectAttempts: Int = 0
) {
    /** Simple 3-level summary for UI (dot color etc.) — not stored, derived on demand. */
    enum class Level { GOOD, DEGRADED, BAD, UNKNOWN }

    val level: Level
        get() = when {
            lastCheckedAt == 0L -> Level.UNKNOWN
            trafficConfirmed && (latencyMs ?: Long.MAX_VALUE) <= 200 -> Level.GOOD
            trafficConfirmed -> Level.DEGRADED
            consecutiveFailures > 0 || reconnectAttempts > 0 -> Level.BAD
            else -> Level.UNKNOWN
        }
}

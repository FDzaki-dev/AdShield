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
    val reconnectAttempts: Int = 0,
    /** MTU actually in use for this session, chosen by [WarpTunnelManager]'s auto-MTU probe (v3.7.0). 0 = not probed yet. */
    val mtuUsed: Int = 0,
    /** WARP peer endpoint ("host:port") actually in use for this session, chosen by [WarpEndpointSelector] (v3.7.0). */
    val endpointUsed: String = "",
    /** Rolling packet-loss estimate (0-100) derived from consecutive failed health-check probes
     *  out of the last few attempted, refreshed each health-check tick (v3.7.0). */
    val packetLossPercent: Int = 0
) {
    /** Simple 3-level summary for UI (dot color etc.) — not stored, derived on demand. */
    enum class Level { GOOD, DEGRADED, BAD, UNKNOWN }

    // v3.28.3 — fixed a real bug, not a timeout/hang: this getter used to fall through to
    // UNKNOWN whenever a health-check probe completed successfully (HTTP response received,
    // so consecutiveFailures/reconnectAttempts both reset to 0 by performHealthCheck()) but
    // Cloudflare's trace body didn't confirm `warp=on` — e.g. tunnel interface UP, but the
    // WireGuard config's endpoint/MTU isn't actually routing traffic through the WARP edge.
    // lastCheckedAt IS non-zero in that case (a real check happened), so this is NOT "never
    // checked" — it's a genuine bad/unconfirmed result and must show as such, matching what
    // WarpForegroundService.buildNotification() already independently branches on ("Terhubung,
    // tapi trafik belum terkonfirmasi lewat WARP") but this getter never mirrored. JANGAN
    // tambahkan consecutiveFailures/reconnectAttempts kembali sebagai syarat sebelum BAD di
    // sini — begitu lastCheckedAt != 0L dan trafficConfirmed == false, itu SUDAH cukup untuk
    // BAD terlepas dari counter lain, itu persis yang bikin bug ini terlewat sebelumnya.
    val level: Level
        get() = when {
            lastCheckedAt == 0L -> Level.UNKNOWN
            trafficConfirmed && (latencyMs ?: Long.MAX_VALUE) <= 200 -> Level.GOOD
            trafficConfirmed -> Level.DEGRADED
            else -> Level.BAD
        }
}

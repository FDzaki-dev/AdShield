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
    /** Simple summary for UI (dot color etc.) — not stored, derived on demand.
     *
     * v3.28.0 — added [NOT_CONFIRMED], split out of what used to be lumped into
     * [UNKNOWN]. Root-caused (see PROJECT_STATE.md v3.28.0 / CHANGELOG.md): the
     * official `com.wireguard.android:tunnel` library has NO API for WireGuard's
     * "reserved" 3 bytes — Cloudflare's edge requires that field to be set to the
     * per-account client ID (confirmed independently against wgcf/warp-plus output
     * AND the library's own javadoc, which lists no such field) to tag a connection
     * `warp=on`. Without it the tunnel still comes up and carries traffic — hence
     * 0% packet loss and a real HTTP response from every trace probe — Cloudflare
     * just never applies WARP identity/policy to it, so `trafficConfirmed` stays
     * false FOREVER, not just "not yet". The old code had no state for that: probe
     * succeeding-but-unconfirmed and probe-never-run both collapsed to the same
     * [UNKNOWN] / "Belum diperiksa" label, which reads as "still waiting" and sent
     * users into an infinite, pointless wait. [NOT_CONFIRMED] is the honest label
     * for "checked, tunnel healthy, but this is a plain encrypted pipe to
     * Cloudflare — not real WARP" until a native fix (tracked in PROJECT_STATE.md)
     * lands. */
    enum class Level { GOOD, DEGRADED, BAD, NOT_CONFIRMED, UNKNOWN }

    val level: Level
        get() = when {
            lastCheckedAt == 0L -> Level.UNKNOWN
            trafficConfirmed && (latencyMs ?: Long.MAX_VALUE) <= 200 -> Level.GOOD
            trafficConfirmed -> Level.DEGRADED
            consecutiveFailures > 0 || reconnectAttempts > 0 -> Level.BAD
            else -> Level.NOT_CONFIRMED
        }
}

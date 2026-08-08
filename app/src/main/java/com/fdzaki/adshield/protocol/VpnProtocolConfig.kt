package com.fdzaki.adshield.protocol

/**
 * Per-protocol connection profile, consumed by the matching [VpnEngine].
 * AdShield has exactly one tunnel-based protocol — WireGuard/Cloudflare
 * WARP — alongside the DNS Ad-Block mode (which has its own dedicated
 * VpnService and doesn't go through this abstraction at all). This sealed
 * class exists so [VpnEngine] has a typed config parameter; it is not a
 * multi-protocol framework.
 */
sealed class VpnProtocolConfig {

    /** Package names the split-tunnel rule applies to. */
    abstract val splitTunnelApps: Set<String>
    abstract val splitTunnelMode: SplitTunnelMode

    /**
     * Config shape for the existing WireGuard/Cloudflare WARP engine
     * ([com.fdzaki.adshield.warp.WarpTunnelManager], adapted via
     * [com.fdzaki.adshield.protocol.WarpVpnEngineAdapter]). Deliberately carries
     * NO server/key fields — WARP is registration-based (WarpAccountRepository/
     * WarpRegistrationClient own that identity, persisted separately) rather
     * than a user-supplied profile, so this is just a marker + the one setting
     * the engine already reads at connect time (routeIpv6, mirrors
     * SettingsRepository.warpRouteIpv6).
     * [splitTunnelApps]/[splitTunnelMode] are NOT YET WIRED into
     * WarpTunnelManager.buildConfig() (no addAllowedApplication/
     * addDisallowedApplication call exists there) — accepted here for
     * interface consistency, functionally ignored by the adapter for now.
     */
    data class Warp(
        val routeIpv6: Boolean = false,
        override val splitTunnelApps: Set<String> = emptySet(),
        override val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
    ) : VpnProtocolConfig()
}

enum class SplitTunnelMode {
    /** All app traffic goes through the tunnel — default, matches current WARP behavior. */
    OFF,
    /** Only apps in [VpnProtocolConfig.splitTunnelApps] are tunneled; everything else bypasses. */
    ALLOWED_APPS_ONLY,
    /** Every app EXCEPT those in [VpnProtocolConfig.splitTunnelApps] is tunneled. */
    EXCLUDE_APPS,
}

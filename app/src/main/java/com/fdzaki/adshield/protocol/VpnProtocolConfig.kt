package com.fdzaki.adshield.protocol

/**
 * Per-protocol connection profile, consumed by the matching [VpnEngine].
 * v3.12.0 (see PROJECT_STATE.md) — data models only, added ahead of the
 * engines that will use them. NOT YET WIRED to any actual file/URL/QR
 * parser (`.ovpn`, `.conf`, `ss://` URL, IKEv2 profile) — that parsing
 * logic belongs to each protocol's own batch, alongside its engine, not
 * here. Fields below are a best-effort shape based on what each protocol
 * conventionally needs; expect them to be adjusted once real parsing is
 * implemented and tested against actual config files.
 *
 * [splitTunnelApps]/[splitTunnelMode] are shared across all tunnel-based
 * protocols (everything except DNS_ADBLOCK, which has its own whitelist
 * mechanism in BlocklistManager) since split tunneling is a VpnService
 * Builder concern (`addAllowedApplication`/`addDisallowedApplication`),
 * not protocol-specific.
 */
sealed class VpnProtocolConfig {

    /** Package names the split-tunnel rule applies to. */
    abstract val splitTunnelApps: Set<String>
    abstract val splitTunnelMode: SplitTunnelMode

    data class OpenVpn(
        /** Raw contents of a `.ovpn` profile file. */
        val ovpnConfigText: String,
        val username: String? = null,
        val password: String? = null,
        override val splitTunnelApps: Set<String> = emptySet(),
        override val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
    ) : VpnProtocolConfig()

    data class IkeV2(
        val serverAddress: String,
        val identity: String,
        /** Path to a client certificate, or null for EAP/username-password auth. */
        val certificateAlias: String? = null,
        val username: String? = null,
        val password: String? = null,
        override val splitTunnelApps: Set<String> = emptySet(),
        override val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
    ) : VpnProtocolConfig()

    data class Shadowsocks(
        val serverAddress: String,
        val serverPort: Int,
        val method: String,
        val password: String,
        /** Present when this profile came from a `vless://` URL instead of `ss://`. */
        val isVless: Boolean = false,
        override val splitTunnelApps: Set<String> = emptySet(),
        override val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.OFF,
    ) : VpnProtocolConfig()

    /**
     * v3.13.0 — config shape for the existing WireGuard/Cloudflare WARP engine
     * ([com.fdzaki.adshield.warp.WarpTunnelManager], adapted via
     * [com.fdzaki.adshield.protocol.WarpVpnEngineAdapter]). Deliberately carries
     * NO server/key fields, unlike [OpenVpn]/[IkeV2]/[Shadowsocks] above — WARP
     * is registration-based (WarpAccountRepository/WarpRegistrationClient own
     * that identity, persisted separately) rather than a user-supplied profile,
     * so this is just a marker + the one setting the engine already reads at
     * connect time (routeIpv6, mirrors SettingsRepository.warpRouteIpv6).
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

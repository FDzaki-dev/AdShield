package com.fdzaki.adshield.util

object Constants {
    const val NOTIF_CHANNEL_ID = "adshield_protection"
    const val NOTIF_ID = 1001
    const val WARP_NOTIF_ID = 1002

    // Fake local "DNS server" address handed to the OS. Only traffic aimed at
    // this address (port 53) gets routed into our tun interface — everything
    // else bypasses the VPN completely, so real app traffic (video, banking,
    // etc.) is never touched, only the DNS resolution step.
    const val VPN_ADDRESS = "10.111.222.1"
    const val VPN_ROUTE = "10.111.222.1"
    const val VPN_ADDRESS_PREFIX = 32

    // Real upstream resolvers we forward *allowed* queries to.
    val UPSTREAM_DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8")

    const val DNS_PORT = 53
    const val VPN_MTU = 32000

    const val PREFS_NAME = "adshield_settings"
    const val KEY_VPN_ENABLED = "vpn_enabled"
    const val KEY_BLOCKED_COUNT = "blocked_count"
    const val KEY_ALLOWED_COUNT = "allowed_count"
    const val KEY_CUSTOM_BLOCKLIST_URL = "custom_blocklist_url"
}

/** The two mutually-exclusive protection modes AdShield can run — never both at once. */
object AppMode {
    const val NONE = "none"
    const val DNS_ADBLOCK = "dns"
    const val WARP_TUNNEL = "warp"
}

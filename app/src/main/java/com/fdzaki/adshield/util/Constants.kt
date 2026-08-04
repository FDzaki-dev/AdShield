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

    // DNS cache (v3.7.0 — Internet Surfing Optimization). Positive answers are
    // cached in-process keyed by (domain, qtype); TTL is clamped to this range
    // so a misconfigured upstream can't pin an entry forever (too-long TTL)
    // or thrash the cache on every query (too-short/zero TTL).
    const val DNS_CACHE_MAX_ENTRIES = 2000
    const val DNS_CACHE_MIN_TTL_SEC = 30L
    const val DNS_CACHE_MAX_TTL_SEC = 3600L
    // Negative (NXDOMAIN/blocked) answers are not cached — a domain that
    // starts resolving later (dynamic DNS, freshly unblocked) must not be
    // stuck failing until an arbitrary negative-TTL expires.

    // WARP endpoint candidates (v3.7.0 — smart endpoint selection). All are
    // official Cloudflare WARP anycast endpoints (same IP range the 1.1.1.1
    // app itself rotates through); engage.cloudflareclient.com:2408 stays
    // first as the documented default / safest fallback if latency probing
    // itself fails for every candidate.
    val WARP_ENDPOINT_CANDIDATES = listOf(
        "engage.cloudflareclient.com:2408",
        "162.159.192.1:2408",
        "162.159.193.10:2408",
        "162.159.195.10:2408",
        "188.114.96.1:2408",
        "188.114.97.1:2408"
    )

    // Auto MTU tuning range (v3.7.0). Cloudflare's own Android client ships
    // 1280 as the safe default; we only ever probe UP from there since going
    // below 1280 risks breaking IPv6 path MTU requirements.
    val WARP_MTU_CANDIDATES = listOf(1420, 1400, 1360, 1280)
}

/** The two mutually-exclusive protection modes AdShield can run — never both at once. */
object AppMode {
    const val NONE = "none"
    const val DNS_ADBLOCK = "dns"
    const val WARP_TUNNEL = "warp"
}

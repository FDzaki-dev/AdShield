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

    // Real upstream resolvers we forward *allowed* queries to. Primary pair
    // is Cloudflare (1.1.1.1/1.0.0.1) per the "Internet Surfing Optimization"
    // roadmap's "DNS cepat" requirement (v3.9.0). v3.10.1 HOTFIX: added
    // 8.8.8.8 (Google) back as a 3rd fallback — v3.9.0 had removed it in
    // favor of 1.0.0.1, but 1.1.1.1 and 1.0.0.1 are BOTH Cloudflare (same
    // provider/AS). On networks/carriers that block Cloudflare's DNS
    // resolvers outright (reported: total DNS failure, ALL apps lost
    // internet with DNS Ad-Block mode on), that left ZERO working
    // resolvers — no fallback outside Cloudflare at all. Google is a
    // different provider/network path, so it survives a Cloudflare-specific
    // block. JANGAN hapus 8.8.8.8 lagi tanpa data baru yang menunjukkan ini
    // aman di jaringan user (lihat PROJECT_STATE.md insiden 2026-08-05).
    val UPSTREAM_DNS_SERVERS = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8")

    const val DNS_PORT = 53

    // DNS-over-HTTPS (v3.11.0, see PROJECT_STATE.md decision log). Tried
    // FIRST for every forwarded query, falling back to plain-UDP
    // UPSTREAM_DNS_SERVERS below only if every DoH endpoint fails — added
    // because the user's network was confirmed to break plain UDP:53
    // entirely (DoH rides port 443, indistinguishable from ordinary HTTPS).
    // Both providers are tried in order per user decision (2026-08-05):
    // Cloudflare first (matches existing 1.1.1.1 preference), Google second.
    val DOH_ENDPOINTS = listOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/dns-query"
    )
    const val DOH_TIMEOUT_MS = 4000
    // HOTFIX v3.10.2 (see PROJECT_STATE.md): was 32000, an unrealistic tun
    // MTU (real WiFi/cellular link MTU is ~1500; nothing in the actual
    // network path supports frames anywhere near 32000). User reported
    // TOTAL internet failure with DNS Ad-Block on -- including direct-IP
    // browser access that bypasses DNS entirely, which the DNS-only
    // addRoute(VPN_ROUTE, 32) architecture should never be able to affect.
    // That symptom points at the tun interface itself misbehaving at
    // establish()/kernel level on this device's network stack, not at DNS
    // resolution -- 1500 is the standard, safe value. NOT YET CONFIRMED by
    // user on-device -- check this first next session.
    const val VPN_MTU = 1500

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

    // DNS prefetch / cache pre-warming (v3.9.0 — Internet Surfing
    // Optimization, batch 2). A short, deliberately conservative list of
    // very high-traffic, non-tracker infra domains (CDNs, OS/app platform
    // hosts) resolved once in the background right after DNS-AdBlock mode
    // starts, so the FIRST real app query for any of them is already a
    // DnsCache hit instead of paying a cold upstream round-trip. Kept short
    // on purpose: this is a warm-up for the common case, not a general
    // crawler — a long list would just delay startup and waste a few KB of
    // data for domains the user may never actually visit this session.
    val POPULAR_PREFETCH_DOMAINS = listOf(
        "google.com", "googleapis.com", "gstatic.com", "youtube.com",
        "ytimg.com", "apple.com", "icloud.com", "cloudflare.com",
        "fbcdn.net", "instagram.com", "whatsapp.net", "tiktokcdn.com",
        "microsoft.com", "live.com", "github.com", "githubusercontent.com",
        "amazonaws.com", "cloudfront.net", "wikipedia.org", "x.com",
        "discord.com", "discordapp.com", "netflix.com", "spotify.com"
    )
    // Delay before the prefetch pass starts, so it never contends with the
    // VPN interface/blocklist finishing their own startup work.
    const val PREFETCH_START_DELAY_MS = 2500L
    // Small pacing gap between each prefetch query — spreads ~24 lookups
    // over roughly a second instead of bursting them all at once.
    const val PREFETCH_QUERY_GAP_MS = 40L
}

/** Protection modes AdShield can run — mutually exclusive, never more than
 *  one active at once (enforced by SettingsRepository.activeMode as single
 *  source of truth, same pattern as the original DNS/WARP pair).
 *
 *  v3.12.0 (see PROJECT_STATE.md decision log): user decided to expand
 *  AdShield from 2 modes to a multi-protocol VPN client. OPENVPN / IKEV2 /
 *  SHADOWSOCKS are added here as placeholder identifiers ONLY — no engine
 *  backs them yet (staged rollout, 1 engine per batch per Batch Lock rule).
 *  Nothing in the UI can select them yet; adding the constant here first is
 *  just so VpnProtocolConfig/VpnEngine (protocol/ package) have a stable
 *  identifier to reference while each engine is built out in its own batch.
 */
object AppMode {
    const val NONE = "none"
    const val DNS_ADBLOCK = "dns"
    const val WARP_TUNNEL = "warp"
    // Not yet functional — placeholders for the staged multi-protocol rollout.
    const val OPENVPN = "openvpn"
    const val IKEV2 = "ikev2"
    const val SHADOWSOCKS = "shadowsocks"
}

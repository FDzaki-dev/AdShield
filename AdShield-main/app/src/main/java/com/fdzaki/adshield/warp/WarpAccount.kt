package com.fdzaki.adshield.warp

/**
 * Everything needed to build a WireGuard tunnel to Cloudflare WARP, obtained
 * once via [WarpRegistrationClient] and reused on every connect after that
 * (re-registering on every connect would create a new "device" on
 * Cloudflare's side each time, which is wasteful and slower).
 */
data class WarpAccount(
    val privateKeyBase64: String,
    val publicKeyBase64: String,
    val accountId: String,
    val accessToken: String,
    val addressV4: String,
    val addressV6: String,
    val peerPublicKeyBase64: String,
    val peerEndpoint: String // "host:port", e.g. "engage.cloudflareclient.com:2408"
)

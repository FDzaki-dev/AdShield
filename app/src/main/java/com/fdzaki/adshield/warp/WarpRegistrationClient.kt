package com.fdzaki.adshield.warp

import com.wireguard.crypto.KeyPair
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Registers a free Cloudflare WARP client identity and fetches its
 * WireGuard peer config, using the same public (but UNOFFICIAL — Cloudflare
 * has not published this as a supported API) registration flow that the
 * open-source `wgcf` tool and the official 1.1.1.1 app itself use under the
 * hood. No Cloudflare account or payment is involved; this is the same
 * "anonymous free tier" every fresh install of the 1.1.1.1 app gets.
 *
 * IMPORTANT — things that can break this without warning, since none of it
 * is documented/versioned by Cloudflare:
 *  - [API_VERSION] is a path segment Cloudflare bumps periodically
 *    (observed values over time: v0a769, v0a884, v0a1922...). If
 *    registration starts failing with 4xx, check the current value used by
 *    https://github.com/ViRb3/wgcf (search its source for "ApiVersion") and
 *    update the constant below.
 *  - Cloudflare has tightened registration validation over time (some
 *    fields that used to be optional are now required). The fields sent
 *    here match wgcf's current request shape as of this writing.
 *  - This is a full-tunnel VPN to a third party (Cloudflare) — traffic
 *    exits through their network, not your carrier. That's the entire
 *    point of WARP, but the user should understand it, which is why the UI
 *    labels this mode explicitly rather than folding it into ad-blocking.
 */
object WarpRegistrationClient {

    private const val API_VERSION = "v0a1922"
    private const val BASE_URL = "https://api.cloudflareclient.com/$API_VERSION"
    private const val USER_AGENT = "okhttp/3.12.1"

    class WarpRegistrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Performs a brand-new device registration. Call once, then persist the result. */
    fun register(): WarpAccount {
        val keyPair = KeyPair()
        val publicKeyBase64 = keyPair.publicKey.toBase64()
        val privateKeyBase64 = keyPair.privateKey.toBase64()

        val requestBody = JSONObject().apply {
            put("install_id", "")
            put("tos", isoTimestampNow())
            put("key", publicKeyBase64)
            put("fcm_token", "")
            put("type", "Android")
            put("model", "PC")
            put("locale", "en_US")
        }

        val responseJson = postJson("$BASE_URL/reg", requestBody, bearerToken = null)

        val accountId = responseJson.optString("id", "")
        val accessToken = responseJson.optString("token", "")
        if (accountId.isEmpty() || accessToken.isEmpty()) {
            throw WarpRegistrationException("Respons registrasi WARP tidak lengkap (id/token kosong) — kemungkinan Cloudflare mengubah format API. Cek WarpRegistrationClient.API_VERSION.")
        }

        val config = responseJson.optJSONObject("config")
            ?: throw WarpRegistrationException("Respons registrasi WARP tidak berisi 'config'.")
        val interfaceObj = config.optJSONObject("interface")
        val addresses = interfaceObj?.optJSONObject("addresses")
        val addressV4 = addresses?.optString("v4").orEmpty()
        val addressV6 = addresses?.optString("v6").orEmpty()

        val peers = config.optJSONArray("peers")
        val firstPeer = peers?.optJSONObject(0)
            ?: throw WarpRegistrationException("Respons registrasi WARP tidak berisi peer WireGuard.")
        val peerPublicKey = firstPeer.optString("public_key").orEmpty()
        val endpointObj = firstPeer.optJSONObject("endpoint")
        val peerEndpoint = endpointObj?.optString("host")
            ?.takeIf { it.isNotBlank() }
            ?: "engage.cloudflareclient.com:2408" // documented fallback, same default wgcf ships

        if (addressV4.isEmpty() || peerPublicKey.isEmpty()) {
            throw WarpRegistrationException("Respons registrasi WARP kosong di field penting (address/peer key).")
        }

        return WarpAccount(
            privateKeyBase64 = privateKeyBase64,
            publicKeyBase64 = publicKeyBase64,
            accountId = accountId,
            accessToken = accessToken,
            addressV4 = addressV4,
            addressV6 = addressV6,
            peerPublicKeyBase64 = peerPublicKey,
            peerEndpoint = peerEndpoint
        )
    }

    private fun isoTimestampNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    private fun postJson(urlString: String, body: JSONObject, bearerToken: String?): JSONObject {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw WarpRegistrationException(
                    "Registrasi WARP gagal (HTTP $responseCode): ${responseText.take(300)}"
                )
            }
            return JSONObject(responseText)
        } catch (e: WarpRegistrationException) {
            throw e
        } catch (e: Exception) {
            throw WarpRegistrationException("Gagal menghubungi server registrasi WARP: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
}

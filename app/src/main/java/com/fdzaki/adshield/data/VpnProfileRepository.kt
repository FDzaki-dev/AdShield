package com.fdzaki.adshield.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for VPN profile secrets: private keys,
 * passwords, and auth tokens for the OpenVPN/IKEv2/Shadowsocks engines
 * being added in the v3.12.0 multi-protocol rollout (see PROJECT_STATE.md).
 * Kept entirely separate from [SettingsRepository] (plain DataStore) —
 * secrets never belong in unencrypted prefs, non-secret settings (which
 * app is whitelisted, blocklist URL, etc.) have no reason to pay the
 * encryption overhead.
 *
 * NOTE: `androidx.security:security-crypto` marked `EncryptedSharedPreferences`
 * deprecated starting 1.1.0-beta01 in favor of using Android Keystore
 * directly — it still works in the 1.1.0 stable release used here (compiles
 * with a deprecation warning, not an error), but if Google removes it in a
 * future major version this class is the ONLY place that needs to change
 * (single choke point by design).
 *
 * NOT YET WIRED to any engine — [WarpTunnelManager]/[VpnProtocolConfig]
 * implementations for OpenVPN/IKEv2/Shadowsocks will read/write through
 * this class once each is built in its own batch. Storing raw config text
 * (e.g. a full `.ovpn` file) here is deliberate: re-deriving it from parsed
 * fields on every reconnect risks losing directives the parser doesn't
 * model yet.
 */
class VpnProfileRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveOpenVpnProfile(name: String, ovpnConfigText: String, username: String?, password: String?) {
        prefs.edit()
            .putString(key(PREFIX_OPENVPN_CONFIG, name), ovpnConfigText)
            .putString(key(PREFIX_OPENVPN_USER, name), username)
            .putString(key(PREFIX_OPENVPN_PASS, name), password)
            .apply()
    }

    fun getOpenVpnProfile(name: String): Triple<String?, String?, String?> = Triple(
        prefs.getString(key(PREFIX_OPENVPN_CONFIG, name), null),
        prefs.getString(key(PREFIX_OPENVPN_USER, name), null),
        prefs.getString(key(PREFIX_OPENVPN_PASS, name), null),
    )

    /**
     * v3.15.0 — IKEv2 profile persistence, added alongside wiring
     * [com.fdzaki.adshield.protocol.IkeV2VpnEngine] into the Home screen.
     * Only username+password auth is saved here (matches the form the UI
     * currently exposes) — [certificateAlias] auth (see
     * VpnProtocolConfig.IkeV2 kdoc) has no UI yet, so it isn't persisted by
     * this method; add a separate save path if/when that's built.
     */
    fun saveIkeV2Profile(name: String, serverAddress: String, identity: String, username: String, password: String) {
        prefs.edit()
            .putString(key(PREFIX_IKEV2_SERVER, name), serverAddress)
            .putString(key(PREFIX_IKEV2_IDENTITY, name), identity)
            .putString(key(PREFIX_IKEV2_USER, name), username)
            .putString(key(PREFIX_IKEV2_PASS, name), password)
            .apply()
    }

    /** Returns (serverAddress, identity, username, password) — all null if no profile saved yet. */
    fun getIkeV2Profile(name: String): IkeV2StoredProfile? {
        val server = prefs.getString(key(PREFIX_IKEV2_SERVER, name), null) ?: return null
        val identity = prefs.getString(key(PREFIX_IKEV2_IDENTITY, name), null) ?: return null
        val username = prefs.getString(key(PREFIX_IKEV2_USER, name), null) ?: return null
        val password = prefs.getString(key(PREFIX_IKEV2_PASS, name), null) ?: return null
        return IkeV2StoredProfile(server, identity, username, password)
    }

    data class IkeV2StoredProfile(
        val serverAddress: String,
        val identity: String,
        val username: String,
        val password: String,
    )

    fun saveShadowsocksProfile(name: String, serverAddress: String, serverPort: Int, method: String, password: String) {
        prefs.edit()
            .putString(key(PREFIX_SS_ADDRESS, name), serverAddress)
            .putInt(key(PREFIX_SS_PORT, name), serverPort)
            .putString(key(PREFIX_SS_METHOD, name), method)
            .putString(key(PREFIX_SS_PASS, name), password)
            .apply()
    }

    fun deleteProfile(name: String) {
        prefs.edit()
            .remove(key(PREFIX_OPENVPN_CONFIG, name))
            .remove(key(PREFIX_OPENVPN_USER, name))
            .remove(key(PREFIX_OPENVPN_PASS, name))
            .remove(key(PREFIX_SS_ADDRESS, name))
            .remove(key(PREFIX_SS_PORT, name))
            .remove(key(PREFIX_SS_METHOD, name))
            .remove(key(PREFIX_SS_PASS, name))
            .remove(key(PREFIX_IKEV2_SERVER, name))
            .remove(key(PREFIX_IKEV2_IDENTITY, name))
            .remove(key(PREFIX_IKEV2_USER, name))
            .remove(key(PREFIX_IKEV2_PASS, name))
            .apply()
    }

    private fun key(prefix: String, profileName: String) = "$prefix:$profileName"

    private companion object {
        const val FILE_NAME = "adshield_vpn_profiles_encrypted"
        const val PREFIX_OPENVPN_CONFIG = "openvpn_config"
        const val PREFIX_OPENVPN_USER = "openvpn_user"
        const val PREFIX_OPENVPN_PASS = "openvpn_pass"
        const val PREFIX_SS_ADDRESS = "ss_address"
        const val PREFIX_SS_PORT = "ss_port"
        const val PREFIX_SS_METHOD = "ss_method"
        const val PREFIX_SS_PASS = "ss_pass"
        const val PREFIX_IKEV2_SERVER = "ikev2_server"
        const val PREFIX_IKEV2_IDENTITY = "ikev2_identity"
        const val PREFIX_IKEV2_USER = "ikev2_user"
        const val PREFIX_IKEV2_PASS = "ikev2_pass"
    }
}

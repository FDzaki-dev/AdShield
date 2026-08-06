package com.fdzaki.adshield.warp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stores the WARP device identity obtained from [WarpRegistrationClient]
 * once, so we don't hit Cloudflare's registration endpoint on every
 * connect. This is the equivalent of wgcf's `wgcf-identity.json`.
 *
 * SECURITY AUDIT FIX (v3.18.0, category Security batch 1 — see
 * PROJECT_STATE.md): this used to be a plain `preferencesDataStore` —
 * the WireGuard private key (full tunnel decryption capability) and
 * Cloudflare access token were stored **unencrypted** on disk, readable by
 * anything with filesystem access (root, ADB backup on older Android,
 * physical extraction). [com.fdzaki.adshield.data.VpnProfileRepository]
 * already used `EncryptedSharedPreferences` for the *other* VPN engines'
 * secrets (OpenVPN/IKEv2/Shadowsocks) — this class was the one inconsistent
 * holdout still on plaintext storage. Migrated to the same
 * `EncryptedSharedPreferences` (AES256_SIV keys / AES256_GCM values)
 * pattern. Public API (property/method names + `Flow` return types) is
 * UNCHANGED so [WarpTunnelManager] needed zero edits — `wasTunnelRunning`/
 * `hasAccount` are now one-shot `flow { emit(...) }` wrappers instead of
 * DataStore's reactive Flow, which is safe because neither caller in this
 * codebase ever `collect`s them continuously (only `.first()`, see
 * `WarpTunnelManager.wasRunningBeforeRestart()`) — verified via grep across
 * `app/src/main/java` before this change.
 *
 * NOTE: this migration does NOT auto-migrate any account already saved
 * under the old `adshield_warp` DataStore file on an existing install —
 * that file is simply abandoned (never read again). Effect for existing
 * users: WARP re-registers silently on next connect attempt (same as a
 * fresh install), same one-time cost as `clearAccount()`. Not a data-loss
 * or correctness bug, but noted here since it's an observable behavior
 * change on upgrade that wasn't explicitly signed off by user.
 */
class WarpAccountRepository(context: Context) {

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

    val wasTunnelRunning: Flow<Boolean> =
        flow { emit(prefs.getBoolean(KEY_WAS_TUNNEL_RUNNING, false)) }

    val hasAccount: Flow<Boolean> =
        flow { emit(!prefs.getString(KEY_ACCOUNT_ID, "").isNullOrBlank()) }

    suspend fun getAccount(): WarpAccount? {
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        if (accountId.isBlank()) return null
        return WarpAccount(
            privateKeyBase64 = prefs.getString(KEY_PRIVATE_KEY, null).orEmpty(),
            publicKeyBase64 = prefs.getString(KEY_PUBLIC_KEY, null).orEmpty(),
            accountId = accountId,
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty(),
            addressV4 = prefs.getString(KEY_ADDRESS_V4, null).orEmpty(),
            addressV6 = prefs.getString(KEY_ADDRESS_V6, null).orEmpty(),
            peerPublicKeyBase64 = prefs.getString(KEY_PEER_PUBLIC_KEY, null).orEmpty(),
            peerEndpoint = prefs.getString(KEY_PEER_ENDPOINT, null).orEmpty()
        )
    }

    suspend fun saveAccount(account: WarpAccount) {
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, account.privateKeyBase64)
            .putString(KEY_PUBLIC_KEY, account.publicKeyBase64)
            .putString(KEY_ACCOUNT_ID, account.accountId)
            .putString(KEY_ACCESS_TOKEN, account.accessToken)
            .putString(KEY_ADDRESS_V4, account.addressV4)
            .putString(KEY_ADDRESS_V6, account.addressV6)
            .putString(KEY_PEER_PUBLIC_KEY, account.peerPublicKeyBase64)
            .putString(KEY_PEER_ENDPOINT, account.peerEndpoint)
            .apply()
    }

    suspend fun clearAccount() {
        prefs.edit()
            .remove(KEY_PRIVATE_KEY)
            .remove(KEY_PUBLIC_KEY)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_ADDRESS_V4)
            .remove(KEY_ADDRESS_V6)
            .remove(KEY_PEER_PUBLIC_KEY)
            .remove(KEY_PEER_ENDPOINT)
            .apply()
    }

    suspend fun setWasTunnelRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_TUNNEL_RUNNING, running).apply()
    }

    private companion object {
        const val FILE_NAME = "adshield_warp_encrypted"
        const val KEY_PRIVATE_KEY = "warp_private_key"
        const val KEY_PUBLIC_KEY = "warp_public_key"
        const val KEY_ACCOUNT_ID = "warp_account_id"
        const val KEY_ACCESS_TOKEN = "warp_access_token"
        const val KEY_ADDRESS_V4 = "warp_address_v4"
        const val KEY_ADDRESS_V6 = "warp_address_v6"
        const val KEY_PEER_PUBLIC_KEY = "warp_peer_public_key"
        const val KEY_PEER_ENDPOINT = "warp_peer_endpoint"
        const val KEY_WAS_TUNNEL_RUNNING = "warp_was_running"
    }
}

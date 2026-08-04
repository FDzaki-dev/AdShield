package com.fdzaki.adshield.warp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.warpDataStore by preferencesDataStore(name = "adshield_warp")

/**
 * Stores the WARP device identity obtained from [WarpRegistrationClient]
 * once, so we don't hit Cloudflare's registration endpoint on every
 * connect. This is the equivalent of wgcf's `wgcf-identity.json`.
 */
class WarpAccountRepository(private val context: Context) {

    private object Keys {
        val PRIVATE_KEY = stringPreferencesKey("warp_private_key")
        val PUBLIC_KEY = stringPreferencesKey("warp_public_key")
        val ACCOUNT_ID = stringPreferencesKey("warp_account_id")
        val ACCESS_TOKEN = stringPreferencesKey("warp_access_token")
        val ADDRESS_V4 = stringPreferencesKey("warp_address_v4")
        val ADDRESS_V6 = stringPreferencesKey("warp_address_v6")
        val PEER_PUBLIC_KEY = stringPreferencesKey("warp_peer_public_key")
        val PEER_ENDPOINT = stringPreferencesKey("warp_peer_endpoint")
        val WAS_TUNNEL_RUNNING = booleanPreferencesKey("warp_was_running")
    }

    val wasTunnelRunning: Flow<Boolean> =
        context.warpDataStore.data.map { it[Keys.WAS_TUNNEL_RUNNING] ?: false }

    val hasAccount: Flow<Boolean> =
        context.warpDataStore.data.map { !(it[Keys.ACCOUNT_ID] ?: "").isBlank() }

    suspend fun getAccount(): WarpAccount? {
        val prefs = context.warpDataStore.data.first()
        val accountId = prefs[Keys.ACCOUNT_ID] ?: return null
        if (accountId.isBlank()) return null
        return WarpAccount(
            privateKeyBase64 = prefs[Keys.PRIVATE_KEY].orEmpty(),
            publicKeyBase64 = prefs[Keys.PUBLIC_KEY].orEmpty(),
            accountId = accountId,
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            addressV4 = prefs[Keys.ADDRESS_V4].orEmpty(),
            addressV6 = prefs[Keys.ADDRESS_V6].orEmpty(),
            peerPublicKeyBase64 = prefs[Keys.PEER_PUBLIC_KEY].orEmpty(),
            peerEndpoint = prefs[Keys.PEER_ENDPOINT].orEmpty()
        )
    }

    suspend fun saveAccount(account: WarpAccount) {
        context.warpDataStore.edit { prefs ->
            prefs[Keys.PRIVATE_KEY] = account.privateKeyBase64
            prefs[Keys.PUBLIC_KEY] = account.publicKeyBase64
            prefs[Keys.ACCOUNT_ID] = account.accountId
            prefs[Keys.ACCESS_TOKEN] = account.accessToken
            prefs[Keys.ADDRESS_V4] = account.addressV4
            prefs[Keys.ADDRESS_V6] = account.addressV6
            prefs[Keys.PEER_PUBLIC_KEY] = account.peerPublicKeyBase64
            prefs[Keys.PEER_ENDPOINT] = account.peerEndpoint
        }
    }

    suspend fun clearAccount() {
        context.warpDataStore.edit { prefs ->
            prefs.remove(Keys.PRIVATE_KEY)
            prefs.remove(Keys.PUBLIC_KEY)
            prefs.remove(Keys.ACCOUNT_ID)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.ADDRESS_V4)
            prefs.remove(Keys.ADDRESS_V6)
            prefs.remove(Keys.PEER_PUBLIC_KEY)
            prefs.remove(Keys.PEER_ENDPOINT)
        }
    }

    suspend fun setWasTunnelRunning(running: Boolean) {
        context.warpDataStore.edit { it[Keys.WAS_TUNNEL_RUNNING] = running }
    }
}

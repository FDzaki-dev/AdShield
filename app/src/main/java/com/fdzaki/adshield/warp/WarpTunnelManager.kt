package com.fdzaki.adshield.warp

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Owns the single WireGuard tunnel used for "VPN Tunnel (WARP)" mode.
 *
 * This is intentionally a completely separate engine from
 * [com.fdzaki.adshield.vpn.AdBlockVpnService]:
 *  - AdBlockVpnService only ever tunnels DNS (see PROJECT_STATE.md decision
 *    #1) and stays lightweight/battery-friendly.
 *  - This tunnels EVERYTHING (0.0.0.0/0, ::/0) through an encrypted
 *    WireGuard connection to Cloudflare WARP, using the official
 *    `com.wireguard.android:tunnel` library's GoBackend — no ad-blocking
 *    happens here, this is a privacy/encryption feature, not a filtering one.
 * The two are mutually exclusive at the UI level: only one may run at a
 * time (see MainViewModel / MainActivity mode switching).
 */
class WarpTunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val accountRepository = WarpAccountRepository(appContext)

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            _state.value = newState
        }
    }

    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    /** Registers a new WARP identity if none is stored yet. Safe to call every connect(). */
    suspend fun ensureRegistered(): Boolean = withContext(Dispatchers.IO) {
        val existing = accountRepository.getAccount()
        if (existing != null && existing.accountId.isNotBlank()) return@withContext true

        return@withContext try {
            val account = WarpRegistrationClient.register()
            accountRepository.saveAccount(account)
            _lastError.value = null
            true
        } catch (e: WarpRegistrationClient.WarpRegistrationException) {
            _lastError.value = e.message
            false
        } catch (e: Exception) {
            _lastError.value = "Gagal registrasi WARP: ${e.message}"
            false
        }
    }

    /** Brings the tunnel up. Returns true on success. Caller must already hold VPN permission. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connecting.value = true
        try {
            val registered = ensureRegistered()
            if (!registered) return@withContext false

            val account = accountRepository.getAccount() ?: run {
                _lastError.value = "Akun WARP tidak ditemukan setelah registrasi."
                return@withContext false
            }

            return@withContext try {
                val config = buildConfig(account)
                backend.setState(tunnel, Tunnel.State.UP, config)
                accountRepository.setWasTunnelRunning(true)
                _lastError.value = null
                true
            } catch (e: Exception) {
                _lastError.value = "Gagal menyalakan tunnel WARP: ${e.message}"
                false
            }
        } finally {
            _connecting.value = false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (_: Exception) {
            // Already down / never started — nothing to clean up.
        }
        accountRepository.setWasTunnelRunning(false)
    }

    suspend fun wasRunningBeforeRestart(): Boolean = accountRepository.wasTunnelRunning.first()

    /** Forgets the registered WARP identity, forcing a fresh registration next connect. */
    suspend fun forgetAccount() = withContext(Dispatchers.IO) {
        disconnect()
        accountRepository.clearAccount()
    }

    private fun buildConfig(account: WarpAccount): Config {
        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(account.privateKeyBase64)
            .addAddress(InetNetwork.parse("${account.addressV4}/32"))
            .parseDnsServers("1.1.1.1,1.0.0.1")
        if (account.addressV6.isNotBlank()) {
            runCatching { interfaceBuilder.addAddress(InetNetwork.parse("${account.addressV6}/128")) }
        }

        val peerBuilder = Peer.Builder()
            .setPublicKey(Key.fromBase64(account.peerPublicKeyBase64))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setEndpoint(InetEndpoint.parse(account.peerEndpoint))
            .setPersistentKeepalive(25)

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    companion object {
        private const val TUNNEL_NAME = "adshield_warp"

        @Volatile private var instance: WarpTunnelManager? = null

        fun getInstance(context: Context): WarpTunnelManager =
            instance ?: synchronized(this) {
                instance ?: WarpTunnelManager(context).also { instance = it }
            }
    }
}

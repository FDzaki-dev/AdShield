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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

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
    private val settingsRepository = com.fdzaki.adshield.data.SettingsRepository(appContext)

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

    private val _quality = MutableStateFlow(WarpConnectionQuality())
    /** Health of the WARP tunnel — latency, real-traffic confirmation, reconnect status.
     *  Only meaningful while [state] is UP; check [WarpConnectionQuality.lastCheckedAt] == 0 for "not probed yet". */
    val quality: StateFlow<WarpConnectionQuality> = _quality

    /** Own coroutine scope for the watchdog loop — lives as long as this singleton (app process),
     *  independent from whichever caller (Service, ViewModel) invoked connect(). */
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null

    /** True while the user/system wants the tunnel running — distinct from [state], which can
     *  legitimately be DOWN for a moment during a reconnect while this stays true. Read by the
     *  watchdog to tell an intentional stop apart from an unexpected drop. */
    @Volatile private var desiredRunning = false
    @Volatile private var reconnecting = false

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

    /** Brings the tunnel up. Returns true on success. Caller must already hold VPN permission.
     *  This is the only entry point that starts a fresh watchdog "session" — reconnect-attempt
     *  counters reset here so a manual off/on always gets a full retry budget. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        _connecting.value = true
        desiredRunning = true
        _quality.value = WarpConnectionQuality()
        try {
            val registered = ensureRegistered()
            if (!registered) {
                desiredRunning = false
                return@withContext false
            }

            val account = accountRepository.getAccount() ?: run {
                _lastError.value = "Akun WARP tidak ditemukan setelah registrasi."
                desiredRunning = false
                return@withContext false
            }

            return@withContext try {
                val routeIpv6 = settingsRepository.warpRouteIpv6.first()
                val config = buildConfig(account, routeIpv6)
                backend.setState(tunnel, Tunnel.State.UP, config)
                accountRepository.setWasTunnelRunning(true)
                _lastError.value = null
                startWatchdog()
                true
            } catch (e: Exception) {
                _lastError.value = "Gagal menyalakan tunnel WARP: ${e.message}"
                desiredRunning = false
                false
            }
        } finally {
            _connecting.value = false
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        desiredRunning = false
        watchdogJob?.cancel()
        watchdogJob = null
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (_: Exception) {
            // Already down / never started — nothing to clean up.
        }
        accountRepository.setWasTunnelRunning(false)
        _quality.value = WarpConnectionQuality()
    }

    /** Periodically probes real connectivity through the tunnel and drives auto-reconnect.
     *  Safe to call repeatedly — no-ops if a watchdog is already running. */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = managerScope.launch {
            delay(INITIAL_CHECK_DELAY_MS)
            while (isActive && desiredRunning) {
                performHealthCheck()
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun performHealthCheck() {
        if (!desiredRunning) return

        if (_state.value != Tunnel.State.UP) {
            // Interface itself isn't up even though we want it to be — treat like a failed probe.
            registerProbeFailure()
            return
        }

        val probe = probeTrace()
        val stats = runCatching { backend.getStatistics(tunnel) }.getOrNull()
        if (probe != null) {
            _quality.value = _quality.value.copy(
                latencyMs = probe.latencyMs,
                trafficConfirmed = probe.warpOn,
                rxBytes = stats?.totalRx() ?: _quality.value.rxBytes,
                txBytes = stats?.totalTx() ?: _quality.value.txBytes,
                lastCheckedAt = System.currentTimeMillis(),
                consecutiveFailures = 0,
                reconnectAttempts = 0
            )
        } else {
            registerProbeFailure()
        }
    }

    private suspend fun registerProbeFailure() {
        val failures = _quality.value.consecutiveFailures + 1
        _quality.value = _quality.value.copy(
            consecutiveFailures = failures,
            lastCheckedAt = System.currentTimeMillis()
        )
        if (failures >= FAILURE_THRESHOLD) {
            attemptReconnect()
        }
    }

    /** Tears the tunnel down and brings it back up with backoff, without touching
     *  [desiredRunning] (still true — this is a "we still want it up" reconnect, not a stop). */
    private suspend fun attemptReconnect() {
        if (reconnecting || !desiredRunning) return
        reconnecting = true
        try {
            val attempts = _quality.value.reconnectAttempts + 1
            if (attempts > MAX_RECONNECT_ATTEMPTS) {
                _lastError.value = "WARP terputus berulang kali — auto-reconnect dihentikan " +
                    "sementara. Coba matikan lalu nyalakan manual, atau periksa koneksi internet."
                return
            }
            _quality.value = _quality.value.copy(reconnectAttempts = attempts)

            val backoffMs = min(BASE_BACKOFF_MS * (1L shl (attempts - 1)), MAX_BACKOFF_MS)
            delay(backoffMs)
            if (!desiredRunning) return

            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {
                // Ignore — proceeding to bring it back up regardless.
            }

            val account = accountRepository.getAccount()
            if (account == null) {
                _lastError.value = "Auto-reconnect gagal: akun WARP tidak ditemukan."
                return
            }
            try {
                val routeIpv6 = settingsRepository.warpRouteIpv6.first()
                val config = buildConfig(account, routeIpv6)
                backend.setState(tunnel, Tunnel.State.UP, config)
                _lastError.value = null
            } catch (e: Exception) {
                _lastError.value = "Auto-reconnect gagal: ${e.message}"
            }
        } finally {
            reconnecting = false
        }
    }

    /** Result of a single trace probe. `warpOn` reflects Cloudflare's own `warp=on` field in the
     *  trace response body — the only reliable confirmation that traffic is really exiting via WARP. */
    private data class TraceProbeResult(val latencyMs: Long, val warpOn: Boolean)

    private fun probeTrace(): TraceProbeResult? {
        return try {
            val started = System.currentTimeMillis()
            val connection = (URL(TRACE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val elapsed = System.currentTimeMillis() - started
            val warpOn = body.lineSequence().any { line ->
                val trimmed = line.trim()
                trimmed == "warp=on" || trimmed == "warp=plus"
            }
            TraceProbeResult(latencyMs = elapsed, warpOn = warpOn)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun wasRunningBeforeRestart(): Boolean = accountRepository.wasTunnelRunning.first()

    /** Forgets the registered WARP identity, forcing a fresh registration next connect.
     *  Goes through disconnect() first, which also cancels the watchdog and resets quality. */
    suspend fun forgetAccount() = withContext(Dispatchers.IO) {
        disconnect()
        accountRepository.clearAccount()
    }

    private fun buildConfig(account: WarpAccount, routeIpv6: Boolean): Config {
        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(account.privateKeyBase64)
            .addAddress(InetNetwork.parse("${account.addressV4}/32"))
            .parseDnsServers("1.1.1.1,1.0.0.1")
            .setMtu(WARP_MTU)
        if (account.addressV6.isNotBlank()) {
            runCatching { interfaceBuilder.addAddress(InetNetwork.parse("${account.addressV6}/128")) }
        }

        val peerBuilder = Peer.Builder()
            .setPublicKey(Key.fromBase64(account.peerPublicKeyBase64))
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
        if (routeIpv6) {
            peerBuilder.addAllowedIp(InetNetwork.parse("::/0"))
        }
        peerBuilder
            .setEndpoint(InetEndpoint.parse(account.peerEndpoint))
            .setPersistentKeepalive(25)

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    companion object {
        private const val TUNNEL_NAME = "adshield_warp"

        // Watchdog / connection-quality tuning. Kept conservative to avoid battery drain from
        // a full-tunnel VPN app polling too aggressively.
        private const val INITIAL_CHECK_DELAY_MS = 8_000L
        private const val HEALTH_CHECK_INTERVAL_MS = 25_000L
        private const val PROBE_TIMEOUT_MS = 4_000
        private const val FAILURE_THRESHOLD = 2
        private const val BASE_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
        // MTU left unset (library auto/default) before v3.2.0 could push MTU higher than what
        // many mobile networks (esp. cellular, carrier NAT/tunneling overhead) actually pass
        // without fragmenting the encapsulated WireGuard packet — fragmented packets get
        // dropped/retransmitted, which tanks real-world throughput far more than a smaller MTU
        // ever costs. 1280 matches Cloudflare's own official Android WARP app default and the
        // wgcf-generated profile default (verified via wgcf docs + xtls WARP integration docs,
        // both confirm official Android client ships MTU=1280 "for maximum compatibility") —
        // this is the safest value across the widest range of real device networks, not a guess.
        private const val WARP_MTU = 1280

        @Volatile private var instance: WarpTunnelManager? = null

        fun getInstance(context: Context): WarpTunnelManager =
            instance ?: synchronized(this) {
                instance ?: WarpTunnelManager(context).also { instance = it }
            }
    }
}

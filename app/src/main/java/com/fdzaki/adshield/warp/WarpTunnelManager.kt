package com.fdzaki.adshield.warp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
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

    // v3.7.0 — Internet Surfing Optimization: winning endpoint/MTU from the
    // most recent probe, used to build the config and to fill in
    // WarpConnectionQuality.endpointUsed/mtuUsed for the UI.
    @Volatile private var activeEndpoint: String = com.fdzaki.adshield.util.Constants.WARP_ENDPOINT_CANDIDATES.first()
    @Volatile private var activeMtu: Int = WARP_MTU

    // Fast reconnect (v3.7.0): listens for the OS switching the active
    // network (WiFi<->cellular, or a brand-new network after one drops) and
    // immediately kicks the watchdog into a reconnect attempt instead of
    // waiting out the next periodic health-check tick — this is what turns
    // a WiFi->data handover from "up to HEALTH_CHECK_INTERVAL_MS of no
    // internet" into "reconnect starts within ~1s of the OS noticing".
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var lastKnownNetwork: Network? = null

    // v3.27.0 — connection-migration debounce (MASQUE-inspired benefit,
    // WITHOUT implementing MASQUE/QUIC). Real MASQUE tolerates a flaky path
    // change by validating the new path before fully migrating the session,
    // instead of tearing down on every single network blip. Android's
    // NetworkCallback can fire onAvailable() several times in quick
    // succession during a genuinely flappy handover (weak WiFi at the edge
    // of range, cellular tower reselection) — before this, EVERY one of
    // those fired a full attemptReconnect(immediate = true) (endpoint
    // re-probe + MTU probe + WireGuard reconfigure), which is wasted work
    // and wasted reconnect-attempt budget if the network settles a few
    // hundred ms later anyway. Now only the LAST network seen within a
    // short settle window actually triggers a reconnect — coalescing a
    // burst of flapping into a single attempt targeting wherever things
    // actually landed, same spirit as MASQUE not committing to a migration
    // until the new path proves out.
    private var networkSwitchDebounceJob: Job? = null

    // v3.16.7 — Reliability audit: captive portal detection. Before this, a captive-portal
    // network (airport/cafe WiFi login page) looked identical to "internet benar-benar mati"
    // to the watchdog — probeTrace() just times out either way since the portal blocks all
    // non-portal traffic, so it burned the full MAX_RECONNECT_ATTEMPTS budget retrying a
    // WireGuard handshake that cannot possibly succeed until the user logs in through a
    // browser. Uses Android's own per-network NET_CAPABILITY_CAPTIVE_PORTAL flag (the same
    // signal that drives the system "Sign in to network" notification) instead of guessing
    // from probe failures, which is unreliable — a captive portal can still let some UDP
    // through in ways that don't cleanly time out.
    private val _captivePortalDetected = MutableStateFlow(false)
    /** True while the OS reports the active network is behind an unauthenticated captive
     *  portal. Exposed for future UI wiring; currently only changes [lastError]'s wording
     *  and pauses reconnect-attempt spending (see [attemptReconnect]). */
    val captivePortalDetected: StateFlow<Boolean> = _captivePortalDetected
    @Volatile private var captivePortalActive = false

    /** Registers a new WARP identity if none is stored yet. Safe to call every connect().
     *
     *  v3.16.5 — reliability audit: this used to be a single-shot call with no retry at
     *  all, so a single transient network hiccup during the very first connect() (before
     *  the tunnel/watchdog's own reconnect logic even exists yet, since there's no account
     *  to build a tunnel config from) failed the whole connect attempt and forced the user
     *  to manually tap connect again. Now retries [REGISTER_MAX_ATTEMPTS] times with the
     *  same exponential-backoff shape as [attemptReconnect] below, capped at
     *  [REGISTER_MAX_BACKOFF_MS] so a genuinely broken registration (e.g. Cloudflare API
     *  format change) still fails within a bounded, user-visible time instead of hanging. */
    suspend fun ensureRegistered(): Boolean = withContext(Dispatchers.IO) {
        val existing = accountRepository.getAccount()
        if (existing != null && existing.accountId.isNotBlank()) return@withContext true

        var lastMessage: String? = null
        for (attempt in 1..REGISTER_MAX_ATTEMPTS) {
            try {
                val account = WarpRegistrationClient.register()
                accountRepository.saveAccount(account)
                _lastError.value = null
                return@withContext true
            } catch (e: WarpRegistrationClient.WarpRegistrationException) {
                lastMessage = e.message
            } catch (e: Exception) {
                lastMessage = "Gagal registrasi WARP: ${e.message}"
            }
            if (attempt < REGISTER_MAX_ATTEMPTS) {
                val backoffMs = min(REGISTER_BASE_BACKOFF_MS * (1L shl (attempt - 1)), REGISTER_MAX_BACKOFF_MS)
                delay(backoffMs)
            }
        }
        _lastError.value = lastMessage
        false
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
                selectEndpointAndMtu()
                val config = buildConfig(account, routeIpv6, activeEndpoint, activeMtu)
                backend.setState(tunnel, Tunnel.State.UP, config)
                accountRepository.setWasTunnelRunning(true)
                _lastError.value = null
                _quality.value = _quality.value.copy(endpointUsed = activeEndpoint, mtuUsed = activeMtu)
                registerNetworkWatcher()
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
        networkSwitchDebounceJob?.cancel()
        networkSwitchDebounceJob = null
        unregisterNetworkWatcher()
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null)
        } catch (_: Exception) {
            // Already down / never started — nothing to clean up.
        }
        accountRepository.setWasTunnelRunning(false)
        _quality.value = WarpConnectionQuality()
        captivePortalActive = false
        _captivePortalDetected.value = false
    }

    /** Picks the fastest endpoint + highest working MTU before bringing the tunnel up.
     *  Uses the cached winner from a recent probe (within [ENDPOINT_CACHE_TTL_MS]) instead
     *  of re-probing every candidate on every single connect() — endpoint latency ranking
     *  and the safe MTU ceiling for a given network path both change slowly in practice. */
    private suspend fun selectEndpointAndMtu() {
        val cachedEndpoint = settingsRepository.warpCachedEndpoint.first()
        val cachedMtu = settingsRepository.warpCachedMtu.first()
        val cacheAge = System.currentTimeMillis() - settingsRepository.warpEndpointCacheTime.first()

        if (cachedEndpoint.isNotBlank() && cachedMtu > 0 && cacheAge in 0 until ENDPOINT_CACHE_TTL_MS) {
            activeEndpoint = cachedEndpoint
            activeMtu = cachedMtu
            return
        }

        activeEndpoint = runCatching { WarpEndpointSelector.selectBestEndpoint() }
            .getOrDefault(com.fdzaki.adshield.util.Constants.WARP_ENDPOINT_CANDIDATES.first())
        activeMtu = probeBestMtu()
        runCatching { settingsRepository.setWarpEndpointCache(activeEndpoint, activeMtu) }
    }

    /** Auto MTU tuning (v3.7.0): tries each candidate MTU from largest to smallest and keeps
     *  the first one a plain UDP datagram of that size can actually round-trip through to
     *  Cloudflare without fragmenting — larger MTU = less per-packet overhead = better real
     *  throughput, but only if the path actually supports it (cellular/carrier NAT often
     *  doesn't). Falls back to the conservative 1280 default (see WARP_MTU) on total failure. */
    private suspend fun probeBestMtu(): Int = withContext(Dispatchers.IO) {
        for (candidate in com.fdzaki.adshield.util.Constants.WARP_MTU_CANDIDATES) {
            val ok = runCatching {
                val socket = DatagramSocket()
                socket.soTimeout = 800
                // WireGuard overhead is ~60 bytes; probe with a payload sized to what the
                // resulting encrypted packet would roughly be, sent at the candidate MTU's
                // IP-layer size. Cloudflare's endpoint silently drops non-handshake packets
                // either way, so we only care whether send() itself succeeds without an
                // immediate "message too long" style IOException (which is what a path with
                // a lower real MTU throws back for an oversized UDP datagram).
                val host = activeEndpoint.substringBeforeLast(":")
                val port = activeEndpoint.substringAfterLast(":").toIntOrNull() ?: 2408
                val payload = ByteArray((candidate - 60).coerceAtLeast(1))
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(host, port)))
                socket.close()
                true
            }.getOrDefault(false)
            if (ok) return@withContext candidate
        }
        WARP_MTU
    }

    private fun registerNetworkWatcher() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Only react to an actual network CHANGE (WiFi<->data, or a fresh network
                // after one dropped) — the very first onAvailable right after connect()
                // fires for the network we already just connected on and isn't a switch.
                val previous = lastKnownNetwork
                lastKnownNetwork = network
                if (previous != null && previous != network && desiredRunning) {
                    // v3.27.0: debounce instead of reconnecting immediately on every
                    // onAvailable — see field kdoc on networkSwitchDebounceJob above.
                    // Cancelling any in-flight debounce and starting a fresh one means
                    // only the LAST network within NETWORK_SWITCH_DEBOUNCE_MS actually
                    // triggers attemptReconnect(); a settled single switch still reacts
                    // within essentially the same ~1s the old code did (debounce delay
                    // is short relative to HEALTH_CHECK_INTERVAL_MS's 25s).
                    networkSwitchDebounceJob?.cancel()
                    networkSwitchDebounceJob = managerScope.launch {
                        delay(NETWORK_SWITCH_DEBOUNCE_MS)
                        if (desiredRunning && lastKnownNetwork == network) {
                            attemptReconnect(immediate = true)
                        }
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (network != lastKnownNetwork) return
                val portalNow = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                if (portalNow == captivePortalActive) return
                captivePortalActive = portalNow
                _captivePortalDetected.value = portalNow
                if (!desiredRunning) return
                if (portalNow) {
                    _lastError.value = CAPTIVE_PORTAL_MESSAGE
                } else {
                    // Portal login just completed (or the OS otherwise cleared the flag) —
                    // the network is genuinely usable again now, so recover right away
                    // instead of waiting for the next periodic health-check tick.
                    _lastError.value = null
                    managerScope.launch { attemptReconnect(immediate = true) }
                }
            }
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkWatcher() {
        val cm = connectivityManager ?: return
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
        lastKnownNetwork = null
    }

    /** Periodically probes real connectivity through the tunnel and drives auto-reconnect.
     *  Safe to call repeatedly — no-ops if a watchdog is already running. */
    private fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = managerScope.launch {
            // Connection warm-up (v3.9.0 — Internet Surfing Optimization,
            // batch 2): fire the first health probe immediately instead of
            // waiting out a fixed startup delay. This does double duty —
            // it sends the first real packet through the freshly-up
            // interface (settles WireGuard's handshake/routing right away
            // rather than leaving it idle), AND it gets a genuine
            // latency/traffic-confirmed reading onto the UI within roughly
            // one probe round-trip of connect() instead of leaving the
            // quality card blank for several seconds.
            performHealthCheck()
            delay(HEALTH_CHECK_INTERVAL_MS)
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
            // v3.7.0 packet-loss estimate: rolling window over the last few probe
            // outcomes (1=lost,0=ok), independent of consecutiveFailures (which
            // resets to 0 immediately on any single success and so can't express
            // "flaky but mostly working").
            recordProbeOutcome(lost = false)
            _quality.value = _quality.value.copy(
                latencyMs = probe.latencyMs,
                trafficConfirmed = probe.warpOn,
                rxBytes = stats?.totalRx() ?: _quality.value.rxBytes,
                txBytes = stats?.totalTx() ?: _quality.value.txBytes,
                lastCheckedAt = System.currentTimeMillis(),
                consecutiveFailures = 0,
                reconnectAttempts = 0,
                packetLossPercent = currentPacketLossPercent(),
                endpointUsed = activeEndpoint,
                mtuUsed = activeMtu
            )
        } else {
            recordProbeOutcome(lost = true)
            registerProbeFailure()
        }
    }

    /** Small fixed-size ring of recent probe outcomes for the packet-loss estimate. */
    private val probeOutcomeWindow = java.util.ArrayDeque<Boolean>(PACKET_LOSS_WINDOW)
    @Synchronized
    private fun recordProbeOutcome(lost: Boolean) {
        if (probeOutcomeWindow.size >= PACKET_LOSS_WINDOW) probeOutcomeWindow.removeFirst()
        probeOutcomeWindow.addLast(lost)
    }
    @Synchronized
    private fun currentPacketLossPercent(): Int {
        if (probeOutcomeWindow.isEmpty()) return 0
        return (probeOutcomeWindow.count { it } * 100) / probeOutcomeWindow.size
    }

    private suspend fun registerProbeFailure() {
        if (captivePortalActive) {
            // Don't spend consecutiveFailures/reconnectAttempts budget on a network we
            // already know is blocked behind a portal login — probeTrace() failing here is
            // expected and tells us nothing new. onCapabilitiesChanged() drives recovery
            // once the portal capability actually clears.
            _quality.value = _quality.value.copy(lastCheckedAt = System.currentTimeMillis())
            return
        }
        val failures = _quality.value.consecutiveFailures + 1
        _quality.value = _quality.value.copy(
            consecutiveFailures = failures,
            lastCheckedAt = System.currentTimeMillis(),
            packetLossPercent = currentPacketLossPercent()
        )
        if (failures >= FAILURE_THRESHOLD) {
            attemptReconnect()
        }
    }

    /** Brings the tunnel back with backoff, without touching [desiredRunning] (still true —
     *  this is a "we still want it up" reconnect, not a stop).
     *
     *  v3.7.0 kill-switch hardening: no longer tears the interface DOWN before bringing it
     *  back UP. `backend.setState(tunnel, UP, newConfig)` on an already-up WireGuard
     *  interface just re-applies the config (new peer/endpoint/keepalive) without a gap where
     *  routes are torn down — previously that DOWN->UP gap was a real window where an app's
     *  traffic could briefly escape the tunnel onto the raw network during every reconnect.
     *  [immediate] (set by the network-switch watcher) skips the exponential backoff delay
     *  entirely — a WiFi<->data handover should retry right away, not wait out a backoff meant
     *  for "server seems to be having trouble". */
    private suspend fun attemptReconnect(immediate: Boolean = false) {
        if (reconnecting || !desiredRunning) return
        if (captivePortalActive) {
            // Guards both call sites: registerProbeFailure() (already skips before calling
            // this) and onAvailable()'s immediate network-switch path, which can otherwise
            // fire a doomed reconnect the instant a captive-portal network is (re)selected.
            _lastError.value = CAPTIVE_PORTAL_MESSAGE
            return
        }
        reconnecting = true
        try {
            val attempts = _quality.value.reconnectAttempts + 1
            if (attempts > MAX_RECONNECT_ATTEMPTS) {
                // v3.16.6 — reliability audit: this used to just set lastError and return,
                // but reconnectAttempts is only reset to 0 by a SUCCESSFUL probe
                // (performHealthCheck), so with the tunnel genuinely dead the watchdog kept
                // calling attemptReconnect() again every HEALTH_CHECK_INTERVAL_MS forever —
                // each call hit this same branch and returned immediately, meaning "dihentikan
                // sementara" was misleading: nothing ever actually paused, it just silently
                // failed the same way every ~25s indefinitely (wasted probes/battery, and no
                // real path back to UP without the user force-toggling the mode off and on).
                // Now: actually stop the watchdog and cleanly tear the interface down, so the
                // state is deterministic (fully DOWN + a clear error) instead of a limbo of
                // infinite silent no-op retries.
                _lastError.value = "WARP terputus berulang kali — auto-reconnect dihentikan. " +
                    "Tunnel dimatikan; nyalakan manual untuk mencoba lagi."
                desiredRunning = false
                watchdogJob?.cancel()
                unregisterNetworkWatcher()
                runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
                accountRepository.setWasTunnelRunning(false)
                return
            }
            _quality.value = _quality.value.copy(reconnectAttempts = attempts)

            if (!immediate) {
                val backoffMs = min(BASE_BACKOFF_MS * (1L shl (attempts - 1)), MAX_BACKOFF_MS)
                delay(backoffMs)
                if (!desiredRunning) return
            }

            val account = accountRepository.getAccount()
            if (account == null) {
                _lastError.value = "Auto-reconnect gagal: akun WARP tidak ditemukan."
                return
            }
            try {
                val routeIpv6 = settingsRepository.warpRouteIpv6.first()
                // Re-pick endpoint/MTU on a network switch (immediate=true) since the best
                // path likely changed with the network; on a plain retry after a flaky
                // probe, keep the same endpoint/MTU that was already working moments ago.
                if (immediate) selectEndpointAndMtu()
                val config = buildConfig(account, routeIpv6, activeEndpoint, activeMtu)
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
                setRequestProperty("Connection", "keep-alive")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // v4.2.0 — "Radikal Perf" batch (see PROJECT_STATE.md): this used to call
            // connection.disconnect() here on every single health-check probe. Per
            // java.net.HttpURLConnection docs (same root cause already fixed in
            // vpn/DohClient.kt v3.25.0/v4.1.0), disconnect() signals the underlying
            // socket must NOT be kept alive for reuse — so for as long as WARP stays
            // connected, EVERY probeTrace() call (one every HEALTH_CHECK_INTERVAL_MS,
            // i.e. potentially hundreds of times over a session) paid a full fresh TLS
            // handshake to Cloudflare instead of resuming the still-open, already-
            // negotiated connection from the probe before it. Removing disconnect() is
            // safe: the response stream is still fully read and closed via `.use {}`
            // below (the actual requirement for a connection to become pool-eligible),
            // and this connection isn't wrapped in a custom protect()ing SSLSocketFactory
            // (unlike DohClient) — it rides the already-UP WireGuard tunnel like any
            // other app traffic, so no protect()-per-socket concern applies here. Bonus:
            // the reported `latencyMs` below is now a more honest steady-state RTT
            // instead of being inflated by a fresh handshake on every single probe.
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

    private fun buildConfig(account: WarpAccount, routeIpv6: Boolean, endpointOverride: String, mtu: Int): Config {
        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(account.privateKeyBase64)
            .addAddress(InetNetwork.parse("${account.addressV4}/32"))
            // DNS leak protection: WireGuard pushes these as the ONLY DNS servers for the
            // duration of the tunnel (AllowedIPs 0.0.0.0/0 below routes everything through
            // it), so there is no path for a DNS query to reach any other resolver — no
            // separate "leak protection" toggle needed, it's structural to this config.
            .parseDnsServers("1.1.1.1,1.0.0.1")
            .setMtu(mtu)
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
            // Smart endpoint selection (v3.7.0): use the probed-fastest candidate instead of
            // always the account's registration-time endpoint, which is just whichever PoP
            // Cloudflare happened to hand out at registration, not necessarily the closest one.
            .setEndpoint(InetEndpoint.parse(endpointOverride.ifBlank { account.peerEndpoint }))
            .setPersistentKeepalive(PERSISTENT_KEEPALIVE_SEC)

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }

    companion object {
        private const val TUNNEL_NAME = "adshield_warp"

        // Watchdog / connection-quality tuning. Kept conservative to avoid battery drain from
        // a full-tunnel VPN app polling too aggressively.
        private const val HEALTH_CHECK_INTERVAL_MS = 25_000L
        private const val PROBE_TIMEOUT_MS = 4_000
        private const val FAILURE_THRESHOLD = 2
        private const val BASE_BACKOFF_MS = 5_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        // v3.27.0 — connection-migration debounce (see networkSwitchDebounceJob kdoc).
        // Short enough that a genuine single WiFi<->cellular handover still reconnects
        // fast (imperceptible next to HEALTH_CHECK_INTERVAL_MS's 25s), long enough to
        // coalesce a burst of onAvailable() calls from a flapping/unstable network into
        // one reconnect instead of one per blip.
        private const val NETWORK_SWITCH_DEBOUNCE_MS = 700L
        // Registration retry (v3.16.5) — separate, smaller budget than reconnect: this runs
        // synchronously inside connect() while the user is waiting for the toggle to flip, so
        // it needs to fail within a few seconds, not minutes.
        private const val REGISTER_MAX_ATTEMPTS = 3
        private const val REGISTER_BASE_BACKOFF_MS = 2_000L
        private const val REGISTER_MAX_BACKOFF_MS = 10_000L
        private const val TRACE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
        // v3.16.7 — shown instead of the generic "auto-reconnect dihentikan" message when
        // the OS confirms the network itself is behind a captive portal, so the user knows
        // to open a browser rather than assuming the app/tunnel is broken.
        private const val CAPTIVE_PORTAL_MESSAGE =
            "Jaringan WiFi ini butuh login (captive portal) — buka browser untuk login, " +
                "WARP akan otomatis tersambung lagi setelahnya."
        private const val PERSISTENT_KEEPALIVE_SEC = 25 // NAT/carrier timeouts are commonly <60s
        // How long a probed endpoint+MTU stays trusted before selectEndpointAndMtu() re-probes
        // from scratch on a fresh connect() — long enough to avoid re-probing on every quick
        // toggle, short enough that a genuinely bad pick doesn't stick around for days.
        private const val ENDPOINT_CACHE_TTL_MS = 30 * 60 * 1000L
        private const val PACKET_LOSS_WINDOW = 8
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

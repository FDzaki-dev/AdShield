package com.fdzaki.adshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.BlocklistUpdateWorker
import com.fdzaki.adshield.data.InstalledApp
import com.fdzaki.adshield.data.InstalledAppsRepository
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.VpnProfileRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import com.fdzaki.adshield.protocol.IkeV2VpnEngine
import com.fdzaki.adshield.protocol.VpnEngineState
import com.fdzaki.adshield.protocol.VpnProtocolConfig
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.util.ResourceMonitor
import com.fdzaki.adshield.vpn.AdBlockVpnService
import com.fdzaki.adshield.warp.WarpConnectionQuality
import com.fdzaki.adshield.warp.WarpTunnelManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * One-off UI feedback events (Snackbar) — added in the "feedback sector"
 * audit pass (see PROJECT_STATE.md v3.3.0) after finding several silent
 * actions: domain add/remove, log clear, counter reset, WARP account
 * forget, and VPN-permission-denied all previously gave the user zero
 * confirmation. A Channel (not StateFlow) is used deliberately: these are
 * one-shot events, not state — a StateFlow would risk re-showing the same
 * Snackbar on config change/recomposition.
 */
sealed class UiEvent {
    /** Plain confirmation/info message, no action button. */
    data class Message(val text: String) : UiEvent()

    /** Message + "Urungkan" (Undo) action. [onUndo] is invoked by the host
     *  (MainActivity) if the user taps the Snackbar action before it times out. */
    data class UndoableMessage(val text: String, val onUndo: () -> Unit) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    private fun sendEvent(event: UiEvent) {
        viewModelScope.launch { _uiEvents.send(event) }
    }

    private val settingsRepository = SettingsRepository(application)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val domainLogDao = AppDatabase.getInstance(application).domainLogDao()
    private val blocklist = BlocklistManager.getInstance()
    private val warpTunnelManager = WarpTunnelManager.getInstance(application)

    // v3.15.0 — first VpnEngine implementation actually wired to the UI (see
    // PROJECT_STATE.md). DNS Ad-Block/WARP deliberately keep their existing
    // direct Service-based path (AdBlockVpnService/WarpForegroundService) —
    // migrating those proven, running foreground services onto VpnEngine is
    // its own separate, higher-risk batch, NOT done here. IKEv2 has no
    // custom Service of its own (android.net.VpnManager owns the tunnel at
    // the OS level once provisioned), so it's driven straight from the
    // ViewModel/engine instead.
    private val vpnProfileRepository = VpnProfileRepository(application)
    private val ikeV2Engine = IkeV2VpnEngine(application)
    val ikeV2State: StateFlow<VpnEngineState> = ikeV2Engine.state

    private val _ikeV2Profile = MutableStateFlow(loadIkeV2Profile())
    val ikeV2Profile: StateFlow<VpnProfileRepository.IkeV2StoredProfile?> = _ikeV2Profile

    private fun loadIkeV2Profile() = vpnProfileRepository.getIkeV2Profile(IKEV2_PROFILE_NAME)

    /** Saves the profile the user typed on Home, then refreshes the in-memory
     *  copy [ikeV2Profile] reads from — [VpnProfileRepository] itself is
     *  plain synchronous EncryptedSharedPreferences, not Flow-backed. */
    fun saveIkeV2Profile(serverAddress: String, identity: String, username: String, password: String) {
        vpnProfileRepository.saveIkeV2Profile(IKEV2_PROFILE_NAME, serverAddress, identity, username, password)
        _ikeV2Profile.value = loadIkeV2Profile()
        sendEvent(UiEvent.Message("Profil IKEv2 disimpan"))
    }

    private fun currentIkeV2Config(): VpnProtocolConfig.IkeV2? {
        val profile = _ikeV2Profile.value ?: return null
        return VpnProtocolConfig.IkeV2(
            serverAddress = profile.serverAddress,
            identity = profile.identity,
            username = profile.username,
            password = profile.password,
        )
    }

    /** Called by MainActivity before connecting — returns the system consent
     *  Intent to launch if one is needed (see VpnEngine.prepareConsent kdoc),
     *  or null if consent isn't required / no profile is saved yet. */
    suspend fun prepareIkeV2Consent(): android.content.Intent? {
        val config = currentIkeV2Config() ?: return null
        return ikeV2Engine.prepareConsent(config)
    }

    /** MainActivity calls this AFTER consent is confirmed (or immediately if
     *  none was needed) — mutual exclusion with DNS/WARP is MainActivity's
     *  responsibility (it stops those services before calling this, same
     *  pattern as requestVpnPermissionThenStartDns/Warp). */
    fun connectIkeV2() {
        val config = currentIkeV2Config() ?: run {
            sendEvent(UiEvent.Message("Isi dan simpan profil IKEv2 dulu sebelum menyambung"))
            return
        }
        viewModelScope.launch {
            settingsRepository.setActiveMode(AppMode.IKEV2)
            ikeV2Engine.connect(config)
        }
    }

    fun disconnectIkeV2() {
        viewModelScope.launch {
            ikeV2Engine.disconnect()
            if (settingsRepository.activeMode.first() == AppMode.IKEV2) {
                settingsRepository.setActiveMode(AppMode.NONE)
            }
        }
    }

    /** Which of the two mutually-exclusive modes is active — persisted, so
     *  it also reflects state correctly right after process restart. */
    val activeMode: StateFlow<String> = settingsRepository.activeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppMode.NONE)

    // Feedback audit finding (v3.8.1): vpnActive used to be a MutableStateFlow flipped
    // optimistically by MainActivity.startDnsService()/stopDnsService() the instant a tap
    // happened — true the moment the start Intent was sent, with no link to whether
    // AdBlockVpnService actually managed to establish the VPN interface. A failed
    // establish() left this stuck true forever (see AdBlockVpnService.startVpn()). Derived
    // directly from the same persisted activeMode source WARP already uses (`warpUp`
    // below) so the ring can no longer disagree with reality; setVpnActive()/_vpnActive are
    // removed — see MainActivity, which no longer calls them.
    val vpnActive: StateFlow<Boolean> = activeMode
        .map { it == AppMode.DNS_ADBLOCK }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val warpState: StateFlow<Tunnel.State> = warpTunnelManager.state
    val warpLastError: StateFlow<String?> = warpTunnelManager.lastError
    val warpConnecting: StateFlow<Boolean> = warpTunnelManager.connecting
    val warpQuality: StateFlow<WarpConnectionQuality> = warpTunnelManager.quality

    /** User's choice for whether WARP routes IPv6 traffic — default false, see
     *  SettingsRepository.warpRouteIpv6 doc for the v3.2.1 measurement behind
     *  that default. Only takes effect next time WARP is turned on. */
    val warpRouteIpv6: StateFlow<Boolean> = settingsRepository.warpRouteIpv6
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Last DNS-mode (Ad-Block) failure, if any — e.g. VPN interface failed
     *  to establish. Used by the Diagnostics screen; see AdBlockVpnService
     *  for why this exists (previously DNS failures were silent). */
    val dnsLastError: StateFlow<String?> = AdBlockVpnService.lastError

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    val whitelistedApps: StateFlow<Set<String>> = settingsRepository.whitelistedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val customBlockedDomains: StateFlow<Set<String>> = settingsRepository.customBlockedDomains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val customAllowedDomains: StateFlow<Set<String>> = settingsRepository.customAllowedDomains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedCount: StateFlow<Long> = settingsRepository.blockedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val allowedCount: StateFlow<Long> = settingsRepository.allowedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val recentLogs: StateFlow<List<DomainLogEntity>> = domainLogDao.recentEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loggingEnabled: StateFlow<Boolean> = settingsRepository.loggingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoStartOnBoot: StateFlow<Boolean> = settingsRepository.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customBlocklistUrl: StateFlow<String> = settingsRepository.customBlocklistUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val blocklistLastUpdated: StateFlow<Long> = settingsRepository.blocklistLastUpdated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val blocklistUpdateStatus: StateFlow<String> = settingsRepository.blocklistUpdateStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Memory + battery snapshot for the Diagnostics screen (v3.10.0 —
     *  resource profiling instrumentation). Polled every
     *  [RESOURCE_POLL_INTERVAL_MS] inside the flow itself; WhileSubscribed(5000)
     *  means the polling loop only actually runs while something (the
     *  Diagnostics screen) is collecting it — it does not sample in the
     *  background, so it costs nothing while that screen isn't open. */
    val resourceSnapshot: StateFlow<ResourceMonitor.Snapshot> = flow {
        val context = getApplication<Application>()
        while (true) {
            emit(ResourceMonitor.snapshot(context))
            delay(RESOURCE_POLL_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResourceMonitor.Snapshot())

    init {
        viewModelScope.launch { _installedApps.value = installedAppsRepository.loadUserFacingApps() }

        // Keep the in-memory BlocklistManager (used by the VPN thread) synced
        // with whatever the user edits in Settings/Whitelist screens live.
        viewModelScope.launch {
            settingsRepository.customBlockedDomains.collect { blocklist.setCustomBlocked(it) }
        }
        viewModelScope.launch {
            settingsRepository.customAllowedDomains.collect { blocklist.setCustomAllowed(it) }
        }
        viewModelScope.launch {
            settingsRepository.whitelistedApps.collect { blocklist.setWhitelistedApps(it) }
        }

        // Reconcile the periodic auto-update schedule against whatever URL is
        // currently saved. enqueueUniquePeriodicWork(..., KEEP) is idempotent,
        // so calling this every time the ViewModel is (re)created is safe and
        // cheap — it does NOT restart an already-running periodic schedule.
        viewModelScope.launch {
            reconcileBlocklistSchedule(settingsRepository.customBlocklistUrl.first())
        }
    }

    private fun reconcileBlocklistSchedule(url: String) {
        val workManager = WorkManager.getInstance(getApplication())
        if (url.isBlank()) {
            workManager.cancelUniqueWork(BlocklistUpdateWorker.PERIODIC_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(
            BlocklistUpdateWorker.PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            BlocklistUpdateWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Saves the URL and immediately reconciles the auto-update schedule
     *  (enqueues periodic work if non-blank, cancels it if cleared). Does
     *  NOT trigger an immediate fetch — call [refreshBlocklistNow] for that,
     *  same as the Rules screen's "Simpan & Perbarui" button does. */
    fun setCustomBlocklistUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setCustomBlocklistUrl(url)
            reconcileBlocklistSchedule(url)
        }
    }

    /** Enqueues a one-time fetch right away, separate from the periodic
     *  schedule (own unique work name, see BlocklistUpdateWorker) so this
     *  never cancels/replaces the scheduled periodic run. Sets a transient
     *  "Memperbarui…" status immediately so the Rules screen shows instant
     *  feedback instead of looking stuck until the Worker finishes. */
    fun refreshBlocklistNow() {
        viewModelScope.launch { settingsRepository.setBlocklistUpdateStatus("Memperbarui…") }
        val request = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            BlocklistUpdateWorker.MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun toggleAppWhitelist(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch { settingsRepository.setAppWhitelisted(packageName, whitelisted) }
    }

    fun addBlockedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addCustomBlockedDomain(domain)
            sendEvent(UiEvent.Message("\"$domain\" ditambahkan ke daftar blokir"))
        }
    }

    fun removeBlockedDomain(domain: String) {
        viewModelScope.launch {
            settingsRepository.removeCustomBlockedDomain(domain)
            sendEvent(UiEvent.UndoableMessage("\"$domain\" dihapus dari daftar blokir") {
                addBlockedDomain(domain)
            })
        }
    }

    fun addAllowedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            settingsRepository.addCustomAllowedDomain(domain)
            sendEvent(UiEvent.Message("\"$domain\" ditambahkan ke daftar izinkan"))
        }
    }

    fun removeAllowedDomain(domain: String) {
        viewModelScope.launch {
            settingsRepository.removeCustomAllowedDomain(domain)
            sendEvent(UiEvent.UndoableMessage("\"$domain\" dihapus dari daftar izinkan") {
                addAllowedDomain(domain)
            })
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLoggingEnabled(enabled) }
    }

    fun setWarpRouteIpv6(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWarpRouteIpv6(enabled) }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartOnBoot(enabled) }
    }

    /** Caller (LogsScreen) is responsible for confirming with the user first
     *  — this just executes + confirms via Snackbar, no undo (log entries
     *  aren't held in memory after clearAll(), so true undo isn't cheap). */
    fun clearLogs() {
        viewModelScope.launch {
            domainLogDao.clearAll()
            sendEvent(UiEvent.Message("Log domain dibersihkan"))
        }
    }

    fun resetCounters() {
        viewModelScope.launch {
            settingsRepository.resetCounters()
            sendEvent(UiEvent.Message("Statistik diblokir/diizinkan direset"))
        }
    }

    /** Previously defined but never wired to any screen (found during the
     *  feedback audit — see PROJECT_STATE.md). Now called from a "Lupakan
     *  Akun WARP" action on the Diagnostics screen, confirmed before calling
     *  and confirmed again here via Snackbar after it completes. */
    fun forgetWarpAccount() {
        viewModelScope.launch {
            warpTunnelManager.forgetAccount()
            sendEvent(UiEvent.Message("Akun WARP dilupakan — akan didaftarkan ulang otomatis saat WARP diaktifkan lagi"))
        }
    }

    /** Called by MainActivity when the system VPN-permission dialog result
     *  is NOT RESULT_OK (user tapped "Tolak"/back-pressed it away). Found
     *  during the feedback audit: this used to be a silent no-op — the
     *  protection ring just stayed off with zero explanation. */
    fun notifyVpnPermissionDenied() {
        sendEvent(UiEvent.Message("Izin VPN ditolak — AdShield butuh izin ini untuk memfilter DNS/trafik"))
    }

    /** Follow-up audit finding (round 2): the battery-optimization-exemption
     *  flow was even more silent than the VPN one — wrapped in a bare
     *  runCatching with zero fallback, so a blocked intent (common on
     *  aggressive OEM skins like Infinix XOS — this app's own target
     *  device) failed with no trace at all. [granted] is determined by
     *  MainActivity re-checking PowerManager.isIgnoringBatteryOptimizations
     *  after the system dialog returns, since that Intent's resultCode
     *  itself isn't reliable for this on many OEMs. */
    fun notifyBatteryExemptionResult(granted: Boolean) {
        sendEvent(
            if (granted) UiEvent.Message("Dikecualikan dari optimasi baterai — proteksi background lebih aman")
            else UiEvent.Message("Belum dikecualikan dari optimasi baterai — sistem mungkin akan menghentikan proteksi saat idle lama")
        )
    }

    /** Called when the settings Intent itself couldn't be launched at all
     *  (some OEM ROMs block ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     *  outright) — previously swallowed by a bare runCatching with no
     *  fallback whatsoever. */
    fun notifyBatteryExemptionUnavailable() {
        sendEvent(UiEvent.Message("Perangkat ini memblokir pengaturan ini — coba cari manual di Pengaturan > Baterai > Aplikasi tak terbatas"))
    }

    /** One-shot read of the true persisted mode, straight from DataStore —
     *  unlike [activeMode] (a stateIn'd StateFlow), this doesn't depend on
     *  something already having subscribed to it. Needed when handling a
     *  toggle shortcut tap on a cold start: at that point activeMode.value
     *  would still just be its stateIn() seed value (AppMode.NONE), not the
     *  real persisted mode, since WhileSubscribed(5000) hasn't started
     *  collecting yet. */
    suspend fun currentActiveMode(): String = settingsRepository.activeMode.first()

    /** One-shot read at cold start — same reasoning as [currentActiveMode]:
     *  MainActivity needs the true persisted value BEFORE deciding which
     *  NavHost start destination to render, not a StateFlow that only
     *  starts collecting once something subscribes. */
    suspend fun currentHasSeenOnboarding(): Boolean = settingsRepository.hasSeenOnboarding.first()

    fun markOnboardingComplete() {
        viewModelScope.launch { settingsRepository.setHasSeenOnboarding(true) }
    }

    companion object {
        private const val RESOURCE_POLL_INTERVAL_MS = 3000L
        // Single-profile storage for now (see VpnProfileRepository) — same
        // simplification OpenVpn/Shadowsocks scaffolding already implied by
        // taking a `name` key; multi-profile support is a later UI concern.
        private const val IKEV2_PROFILE_NAME = "default"
    }
}

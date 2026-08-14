package com.fdzaki.adshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.BlocklistUpdateWorker
import com.fdzaki.adshield.data.InstalledApp
import com.fdzaki.adshield.data.InstalledAppsRepository
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import com.fdzaki.adshield.util.AppMode
import com.fdzaki.adshield.util.ResourceMonitor
import com.fdzaki.adshield.ui.theme.AppThemeVariant
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
/** v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): one row per app
 *  that made a DNS query while the screen was off, joined with its current
 *  label/icon for display. [label] falls back to [packageName] itself when
 *  the app was since uninstalled (loadAppInfo returns null). */
data class SilentLeakUiItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val count: Int
)

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

    /** v4.8.0 "Lock In" kill switch — default true. See SettingsRepository.
     *  warpKillSwitchEnabled doc; read live by WarpTunnelManager the moment
     *  auto-reconnect gives up, so toggling this mid-session is safe. */
    val warpKillSwitchEnabled: StateFlow<Boolean> = settingsRepository.warpKillSwitchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // v4.7.0 — custom theme #2 toggle (see PROJECT_STATE.md / ThemeVariant.kt).
    // Mapped from SettingsRepository's plain-String storage key here (the
    // one place in the app allowed to depend on both `data` and `ui.theme`)
    // so nothing outside this ViewModel needs to know the raw key format.
    val themeVariant: StateFlow<AppThemeVariant> = settingsRepository.themeVariant
        .map { AppThemeVariant.fromStorageKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeVariant.TITANIUM_BRASS)

    fun setThemeVariant(variant: AppThemeVariant) {
        viewModelScope.launch { settingsRepository.setThemeVariant(variant.storageKey) }
    }

    /** Last DNS-mode (Ad-Block) failure, if any — e.g. VPN interface failed
     *  to establish. Used by the Diagnostics screen; see AdBlockVpnService
     *  for why this exists (previously DNS failures were silent). */
    val dnsLastError: StateFlow<String?> = AdBlockVpnService.lastError

    /** DoH resolver diagnostic snapshot (v3.25.0 — see PROJECT_STATE.md "Krisis
     *  DNS/DoH"). Read by DiagnosticsScreen so a failed DoH attempt on a real
     *  device is diagnosable (exact exception/reason) instead of a silent
     *  "internet doesn't work" like the original 2026-08 crisis. */
    val dohHealth: StateFlow<com.fdzaki.adshield.vpn.DohHealthMonitor.Snapshot> =
        com.fdzaki.adshield.vpn.DohHealthMonitor.state

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

    // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): joins the raw
    // per-app counts against InstalledAppsRepository for label/icon. map's
    // transform lambda is suspend-capable, and List.map is `inline`, so
    // calling the suspend loadAppInfo() per row here is valid Kotlin — no
    // extra coroutine launch needed per row.
    val silentLeaks: StateFlow<List<SilentLeakUiItem>> = domainLogDao.silentLeaks()
        .map { counts ->
            counts.map { c ->
                val info = installedAppsRepository.loadAppInfo(c.backgroundApp)
                SilentLeakUiItem(
                    packageName = c.backgroundApp,
                    label = info?.label ?: c.backgroundApp,
                    icon = info?.icon,
                    count = c.count
                )
            }
        }
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

    fun setWarpKillSwitchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWarpKillSwitchEnabled(enabled) }
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
    }
}

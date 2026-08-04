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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val domainLogDao = AppDatabase.getInstance(application).domainLogDao()
    private val blocklist = BlocklistManager.getInstance()
    private val warpTunnelManager = WarpTunnelManager.getInstance(application)

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive

    /** Which of the two mutually-exclusive modes is active — persisted, so
     *  it also reflects state correctly right after process restart. */
    val activeMode: StateFlow<String> = settingsRepository.activeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppMode.NONE)

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

    fun setVpnActive(active: Boolean) {
        _vpnActive.value = active
    }

    fun toggleAppWhitelist(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch { settingsRepository.setAppWhitelisted(packageName, whitelisted) }
    }

    fun addBlockedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch { settingsRepository.addCustomBlockedDomain(domain) }
    }

    fun removeBlockedDomain(domain: String) {
        viewModelScope.launch { settingsRepository.removeCustomBlockedDomain(domain) }
    }

    fun addAllowedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch { settingsRepository.addCustomAllowedDomain(domain) }
    }

    fun removeAllowedDomain(domain: String) {
        viewModelScope.launch { settingsRepository.removeCustomAllowedDomain(domain) }
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

    fun clearLogs() {
        viewModelScope.launch { domainLogDao.clearAll() }
    }

    fun resetCounters() {
        viewModelScope.launch { settingsRepository.resetCounters() }
    }

    fun forgetWarpAccount() {
        viewModelScope.launch { warpTunnelManager.forgetAccount() }
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
}

package com.fdzaki.adshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fdzaki.adshield.data.BlocklistManager
import com.fdzaki.adshield.data.InstalledApp
import com.fdzaki.adshield.data.InstalledAppsRepository
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val installedAppsRepository = InstalledAppsRepository(application)
    private val domainLogDao = AppDatabase.getInstance(application).domainLogDao()
    private val blocklist = BlocklistManager.getInstance()

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive

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

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartOnBoot(enabled) }
    }

    fun clearLogs() {
        viewModelScope.launch { domainLogDao.clearAll() }
    }

    fun resetCounters() {
        viewModelScope.launch { settingsRepository.resetCounters() }
    }
}

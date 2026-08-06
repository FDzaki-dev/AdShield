package com.fdzaki.adshield.vpn.dns

import android.content.Context
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Increments blocked/allowed counters and, if enabled, persists a
 * per-domain log entry to Room. Extracted from AdBlockVpnService (v3.17.0
 * God-Class refactor, see PROJECT_STATE.md) — ZERO behavior change.
 */
class DnsQueryLogger(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
) {
    fun log(domain: String, blocked: Boolean) {
        scope.launch {
            if (blocked) settingsRepository.incrementBlocked() else settingsRepository.incrementAllowed()
            if (settingsRepository.loggingEnabled.first()) {
                runCatching {
                    AppDatabase.getInstance(context).domainLogDao()
                        .insert(DomainLogEntity(domain = domain, blocked = blocked))
                }
            }
        }
    }
}

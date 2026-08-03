package com.fdzaki.adshield.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "adshield_settings")

/**
 * Single source of truth for user-configurable state:
 *  - which apps are whitelisted (their traffic's DNS queries are never blocked)
 *  - custom allow/deny domain rules the user typed in manually
 *  - running counters shown on the Home screen
 *  - whether protection should auto-start on boot
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WHITELISTED_APPS = stringSetPreferencesKey("whitelisted_apps")
        val CUSTOM_BLOCKED_DOMAINS = stringSetPreferencesKey("custom_blocked_domains")
        val CUSTOM_ALLOWED_DOMAINS = stringSetPreferencesKey("custom_allowed_domains")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val WAS_RUNNING = booleanPreferencesKey("was_running")
        val BLOCKED_COUNT = longPreferencesKey("blocked_count")
        val ALLOWED_COUNT = longPreferencesKey("allowed_count")
        val CUSTOM_BLOCKLIST_URL = stringPreferencesKey("custom_blocklist_url")
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val BLOCKLIST_LAST_UPDATED = longPreferencesKey("blocklist_last_updated")
        val BLOCKLIST_UPDATE_STATUS = stringPreferencesKey("blocklist_update_status")
    }

    val whitelistedApps: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.WHITELISTED_APPS] ?: emptySet() }

    val customBlockedDomains: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet() }

    val customAllowedDomains: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.CUSTOM_ALLOWED_DOMAINS] ?: emptySet() }

    val autoStartOnBoot: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_START_ON_BOOT] ?: true }

    val wasRunning: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WAS_RUNNING] ?: false }

    val blockedCount: Flow<Long> =
        context.dataStore.data.map { it[Keys.BLOCKED_COUNT] ?: 0L }

    val allowedCount: Flow<Long> =
        context.dataStore.data.map { it[Keys.ALLOWED_COUNT] ?: 0L }

    val customBlocklistUrl: Flow<String> =
        context.dataStore.data.map { it[Keys.CUSTOM_BLOCKLIST_URL] ?: "" }

    /** Epoch millis of the last successful (or attempted) BlocklistUpdateWorker
     *  run. 0L means "never" — Rules screen shows "Belum pernah" for that. */
    val blocklistLastUpdated: Flow<Long> =
        context.dataStore.data.map { it[Keys.BLOCKLIST_LAST_UPDATED] ?: 0L }

    /** Human-readable outcome of the last update attempt (e.g. "Berhasil: 3421
     *  domain dimuat" or "Gagal: file kosong…") — written by BlocklistUpdateWorker,
     *  read directly by the Rules screen. Empty string means never attempted. */
    val blocklistUpdateStatus: Flow<String> =
        context.dataStore.data.map { it[Keys.BLOCKLIST_UPDATE_STATUS] ?: "" }

    suspend fun setBlocklistLastUpdated(timestamp: Long) {
        context.dataStore.edit { it[Keys.BLOCKLIST_LAST_UPDATED] = timestamp }
    }

    suspend fun setBlocklistUpdateStatus(status: String) {
        context.dataStore.edit { it[Keys.BLOCKLIST_UPDATE_STATUS] = status }
    }

    val loggingEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOGGING_ENABLED] ?: true }

    /** Which of the two mutually-exclusive modes (DNS ad-block / WARP tunnel)
     *  is currently supposed to be running — single source of truth used by
     *  BootReceiver and the UI to keep them from ever running together. */
    val activeMode: Flow<String> =
        context.dataStore.data.map { it[Keys.ACTIVE_MODE] ?: com.fdzaki.adshield.util.AppMode.NONE }

    /** Whether the first-run onboarding flow has been completed (or skipped)
     *  at least once. Defaults false — a fresh DataStore (new install) means
     *  the onboarding screen shows once, then this flips permanently true. */
    val hasSeenOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_SEEN_ONBOARDING] ?: false }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }

    suspend fun setActiveMode(mode: String) {
        context.dataStore.edit { it[Keys.ACTIVE_MODE] = mode }
    }

    suspend fun setAppWhitelisted(packageName: String, whitelisted: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.WHITELISTED_APPS] ?: emptySet()).toMutableSet()
            if (whitelisted) current.add(packageName) else current.remove(packageName)
            prefs[Keys.WHITELISTED_APPS] = current
        }
    }

    suspend fun addCustomBlockedDomain(domain: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet()).toMutableSet()
            current.add(domain.trim().lowercase())
            prefs[Keys.CUSTOM_BLOCKED_DOMAINS] = current
        }
    }

    suspend fun removeCustomBlockedDomain(domain: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_BLOCKED_DOMAINS] ?: emptySet()).toMutableSet()
            current.remove(domain)
            prefs[Keys.CUSTOM_BLOCKED_DOMAINS] = current
        }
    }

    suspend fun addCustomAllowedDomain(domain: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_ALLOWED_DOMAINS] ?: emptySet()).toMutableSet()
            current.add(domain.trim().lowercase())
            prefs[Keys.CUSTOM_ALLOWED_DOMAINS] = current
        }
    }

    suspend fun removeCustomAllowedDomain(domain: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.CUSTOM_ALLOWED_DOMAINS] ?: emptySet()).toMutableSet()
            current.remove(domain)
            prefs[Keys.CUSTOM_ALLOWED_DOMAINS] = current
        }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_ON_BOOT] = enabled }
    }

    suspend fun setWasRunning(running: Boolean) {
        context.dataStore.edit { it[Keys.WAS_RUNNING] = running }
    }

    suspend fun incrementBlocked() {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOCKED_COUNT] = (prefs[Keys.BLOCKED_COUNT] ?: 0L) + 1
        }
    }

    suspend fun incrementAllowed() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALLOWED_COUNT] = (prefs[Keys.ALLOWED_COUNT] ?: 0L) + 1
        }
    }

    suspend fun resetCounters() {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOCKED_COUNT] = 0L
            prefs[Keys.ALLOWED_COUNT] = 0L
        }
    }

    suspend fun setCustomBlocklistUrl(url: String) {
        context.dataStore.edit { it[Keys.CUSTOM_BLOCKLIST_URL] = url.trim() }
    }

    suspend fun setLoggingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOGGING_ENABLED] = enabled }
    }
}

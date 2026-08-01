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

    val loggingEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.LOGGING_ENABLED] ?: true }

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

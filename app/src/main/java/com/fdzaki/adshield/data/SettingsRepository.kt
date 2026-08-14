package com.fdzaki.adshield.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val WARP_ROUTE_IPV6 = booleanPreferencesKey("warp_route_ipv6")
        val WARP_KILL_SWITCH_ENABLED = booleanPreferencesKey("warp_kill_switch_enabled")
        // v3.7.0 — Internet Surfing Optimization: cache the winner of the last
        // endpoint-latency probe / MTU probe so a reconnect within the cache
        // window (see WarpTunnelManager.ENDPOINT_CACHE_TTL_MS) can skip
        // re-probing every candidate and just reuse the known-good pick.
        val WARP_CACHED_ENDPOINT = stringPreferencesKey("warp_cached_endpoint")
        val WARP_CACHED_MTU = intPreferencesKey("warp_cached_mtu")
        val WARP_ENDPOINT_CACHE_TIME = longPreferencesKey("warp_endpoint_cache_time")
        // v4.7.0 — custom theme #2 toggle (see ui/theme/ThemeVariant.kt).
        // Stored as the enum's `storageKey` string (not `.name`/ordinal) so
        // renaming the Kotlin enum constant later can't silently break a
        // value already persisted on someone's device.
        val THEME_VARIANT = stringPreferencesKey("theme_variant")
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

    // Perf (v4.1.0 — "Radikal Perf" batch, see PROJECT_STATE.md): DnsQueryLogger
    // used to call incrementBlocked()/incrementAllowed() — each its OWN
    // dataStore.edit{} disk write — on EVERY single DNS query the packet loop
    // handled. DataStore.edit() does a real atomic file write (temp file +
    // rename) per call; under heavy browsing that meant dozens of synchronous
    // disk writes per second just for two counters. DnsQueryLogger now
    // accumulates both deltas in memory and flushes them here in ONE edit{}
    // call every few seconds instead of one edit{} per query — same eventual
    // counter values, orders of magnitude fewer disk writes.
    suspend fun incrementCountersBy(blockedDelta: Long, allowedDelta: Long) {
        if (blockedDelta == 0L && allowedDelta == 0L) return
        context.dataStore.edit { prefs ->
            if (blockedDelta != 0L) prefs[Keys.BLOCKED_COUNT] = (prefs[Keys.BLOCKED_COUNT] ?: 0L) + blockedDelta
            if (allowedDelta != 0L) prefs[Keys.ALLOWED_COUNT] = (prefs[Keys.ALLOWED_COUNT] ?: 0L) + allowedDelta
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

    /** Whether the WARP tunnel routes IPv6 traffic (::/0) in addition to IPv4.
     *  Default `false` — v3.2.1 measurement on a real device found IPv6 over
     *  WARP on the user's cellular operator collapsed upload throughput by
     *  86% (download only -15%); disabling it not only fixed that but beat
     *  the no-VPN baseline in both directions. Kept as a user toggle (not a
     *  hardcoded default) rather than removed entirely because this is
     *  operator/network-dependent — someone on a different network may not
     *  hit the same issue and may want IPv6 traffic protected too. Only
     *  takes effect the next time WARP is turned on (WireGuard config is
     *  fixed for the lifetime of a running tunnel).*/
    val warpRouteIpv6: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WARP_ROUTE_IPV6] ?: false }

    suspend fun setWarpRouteIpv6(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WARP_ROUTE_IPV6] = enabled }
    }

    /** v4.8.0 — "Lock In" kill switch. Default ON (fail-closed): when auto-reconnect
     *  gives up after MAX_RECONNECT_ATTEMPTS, [WarpTunnelManager] leaves the dead
     *  tunnel interface established instead of tearing it down, so 0.0.0.0/0 + ::/0
     *  keep routing into it and traffic is silently blackholed rather than leaking
     *  onto the raw network. OFF restores the old fail-open behavior (tunnel torn
     *  down, device falls back to normal internet) for anyone who'd rather have
     *  connectivity than a hard lock. Read at the moment reconnect budget runs out,
     *  not cached at connect() time — a mid-session toggle takes effect immediately. */
    val warpKillSwitchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.WARP_KILL_SWITCH_ENABLED] ?: true }

    suspend fun setWarpKillSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WARP_KILL_SWITCH_ENABLED] = enabled }
    }

    // v3.7.0 — cached winner of the last endpoint/MTU probe, plus when it was cached.
    val warpCachedEndpoint: Flow<String> =
        context.dataStore.data.map { it[Keys.WARP_CACHED_ENDPOINT] ?: "" }

    val warpCachedMtu: Flow<Int> =
        context.dataStore.data.map { it[Keys.WARP_CACHED_MTU] ?: 0 }

    val warpEndpointCacheTime: Flow<Long> =
        context.dataStore.data.map { it[Keys.WARP_ENDPOINT_CACHE_TIME] ?: 0L }

    suspend fun setWarpEndpointCache(endpoint: String, mtu: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WARP_CACHED_ENDPOINT] = endpoint
            prefs[Keys.WARP_CACHED_MTU] = mtu
            prefs[Keys.WARP_ENDPOINT_CACHE_TIME] = System.currentTimeMillis()
        }
    }

    /** Raw storage-key string, default = TITANIUM_BRASS's key so a fresh
     *  install renders the original identity with zero migration needed.
     *  Kept as `Flow<String>` (not `Flow<AppThemeVariant>`) here — this data
     *  layer has no dependency on `ui/theme`, matching [activeMode]'s
     *  existing plain-String pattern in this same class. */
    val themeVariant: Flow<String> =
        context.dataStore.data.map { it[Keys.THEME_VARIANT] ?: "titanium_brass" }

    suspend fun setThemeVariant(storageKey: String) {
        context.dataStore.edit { it[Keys.THEME_VARIANT] = storageKey }
    }
}

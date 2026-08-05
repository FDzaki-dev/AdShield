package com.fdzaki.adshield.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fdzaki.adshield.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast, thread-safe domain lookup used from the VPN packet-processing loop.
 *
 * Matching strategy (Cloudflare 1.1.1.1 / Pi-hole style — precise, not
 * blanket): every entry is EXACT MATCH by default. A domain only covers its
 * subdomains if it's explicitly listed as a wildcard entry ("*.example.com"
 * in the list, or "*.example.com" typed by the user in Aturan Kustom).
 *
 * This is a deliberate change from naive "walk up to parent domain" logic:
 * that approach silently blocks every subdomain of any listed domain,
 * including shared infrastructure (CDNs, cloud storage, analytics domains
 * also used for legitimate non-ad purposes) that happens to share a suffix
 * with something ad-related — collateral damage the user explicitly does
 * not want. See PROJECT_STATE.md for the full rationale.
 */
class BlocklistManager private constructor() {

    private data class Entry(val domain: String, val isWildcard: Boolean)

    private val blockedExact = ConcurrentHashMap.newKeySet<String>()
    private val blockedWildcardBases = ConcurrentHashMap.newKeySet<String>()

    private val allowedExact = ConcurrentHashMap.newKeySet<String>()
    private val allowedWildcardBases = ConcurrentHashMap.newKeySet<String>()

    /**
     * Optional downloaded blocklist (see [BlocklistUpdateWorker], v2.5.0).
     * Deliberately kept in its OWN sets, completely separate from
     * [blockedExact]/[blockedWildcardBases] above — [loadRemoteList] always
     * clears and replaces these two only, so an empty/corrupt/failed remote
     * fetch can never remove a domain that the bundled default list or the
     * user's manual custom rules still want blocked.
     */
    private val remoteBlockedExact = ConcurrentHashMap.newKeySet<String>()
    private val remoteBlockedWildcardBases = ConcurrentHashMap.newKeySet<String>()

    /**
     * Domain esensial untuk fungsi dasar konektivitas Android — captive
     * portal detection, connectivity check, time sync. Ini SELALU diizinkan
     * tanpa terkecuali, tidak bisa di-override oleh blocklist bawaan maupun
     * aturan kustom user. Kalau domain semacam ini ikut terblokir (misal
     * gara-gara typo di aturan kustom, atau update blocklist yang ceroboh),
     * gejalanya membingungkan: HP terlihat "tidak ada internet" padahal
     * cuma DNS captive-portal check yang gagal. Ini bukan fitur baru — ini
     * jaring pengaman terhadap kelas false-positive yang paling merusak.
     */
    private val criticalAllowlist = setOf(
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "clients3.google.com",
        "www.msftconnecttest.com",
        "msftconnecttest.com",
        "captive.apple.com",
        "time.android.com",
        "time.google.com",
        "dns.google"
    )

    private val whitelistedApps = ConcurrentHashMap.newKeySet<String>()

    private var customBlockedSnapshot: Set<Entry> = emptySet()

    @Volatile var totalBlockedDomainCount: Int = 0
        private set

    fun loadDefaultList(context: Context) {
        val reader = BufferedReader(
            InputStreamReader(context.resources.openRawResource(R.raw.blocklist_default))
        )
        reader.useLines { lines ->
            lines.forEach { rawLine ->
                val entry = parseLine(rawLine) ?: return@forEach
                if (entry.isWildcard) blockedWildcardBases.add(entry.domain)
                else blockedExact.add(entry.domain)
            }
        }
        recalculateTotalBlockedDomainCount()
    }

    /**
     * Loads whatever was cached from the last successful [BlocklistUpdateWorker]
     * run, if any. Called once at VPN start (same point as [loadDefaultList])
     * because [instance] is a process-wide singleton that can be recreated
     * fresh after process death — without this, a remote list fetched in a
     * previous process would silently stop applying after the app/system
     * killed and restarted the VPN service.
     */
    fun loadCachedRemoteListIfPresent(context: Context) {
        val file = File(context.filesDir, REMOTE_BLOCKLIST_FILENAME)
        if (!file.exists()) return
        val lines = runCatching { file.readLines() }.getOrNull() ?: return
        loadRemoteList(lines)
    }

    /**
     * Replaces the downloaded remote list entirely (clear-then-fill, not an
     * incremental diff like [setCustomBlocked]) — simpler and safe here
     * specifically because remote entries live in their own sets that
     * nothing else writes to.
     */
    fun loadRemoteList(lines: List<String>) {
        remoteBlockedExact.clear()
        remoteBlockedWildcardBases.clear()
        lines.forEach { rawLine ->
            val entry = parseLine(rawLine) ?: return@forEach
            if (entry.isWildcard) remoteBlockedWildcardBases.add(entry.domain)
            else remoteBlockedExact.add(entry.domain)
        }
        recalculateTotalBlockedDomainCount()
    }

    val remoteBlockedDomainCount: Int
        get() = remoteBlockedExact.size + remoteBlockedWildcardBases.size

    private fun recalculateTotalBlockedDomainCount() {
        totalBlockedDomainCount =
            blockedExact.size + blockedWildcardBases.size +
            remoteBlockedExact.size + remoteBlockedWildcardBases.size
    }

    /**
     * Parses one blocklist line. Accepted forms:
     *  - "0.0.0.0 example.com" / "127.0.0.1 example.com"  (hosts-file style, exact match)
     *  - "example.com"                                     (bare domain, exact match)
     *  - "*.example.com"                                   (wildcard, matches example.com + all subdomains)
     * Comments ("#") and blank lines are skipped.
     */
    private fun parseLine(line: String): Entry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"))
        val raw = when {
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return null
        }
        var clean = raw.trim().lowercase().removeSuffix(".")
        if (clean.isEmpty() || clean == "localhost" || clean == "localhost.localdomain") return null

        val isWildcard = clean.startsWith("*.")
        if (isWildcard) clean = clean.removePrefix("*.")
        if (clean.isEmpty()) return null
        return Entry(clean, isWildcard)
    }

    fun setCustomBlocked(domains: Set<String>) {
        val parsed = domains.mapNotNull { parseLine(it) }.toSet()
        // Remove entries from the previous snapshot that are no longer present
        (customBlockedSnapshot - parsed).forEach {
            if (it.isWildcard) blockedWildcardBases.remove(it.domain) else blockedExact.remove(it.domain)
        }
        parsed.forEach {
            if (it.isWildcard) blockedWildcardBases.add(it.domain) else blockedExact.add(it.domain)
        }
        customBlockedSnapshot = parsed
        recalculateTotalBlockedDomainCount()
    }

    fun setCustomAllowed(domains: Set<String>) {
        allowedExact.clear()
        allowedWildcardBases.clear()
        domains.mapNotNull { parseLine(it) }.forEach {
            if (it.isWildcard) allowedWildcardBases.add(it.domain) else allowedExact.add(it.domain)
        }
    }

    fun setWhitelistedApps(packages: Set<String>) {
        whitelistedApps.clear()
        whitelistedApps.addAll(packages)
    }

    fun isAppWhitelisted(packageName: String?): Boolean =
        packageName != null && whitelistedApps.contains(packageName)

    /** Lets the VPN packet loop skip the (relatively expensive) per-query
     *  UID lookup entirely when no app is whitelisted. */
    fun hasWhitelistedApps(): Boolean = whitelistedApps.isNotEmpty()

    /**
     * O(depth of `domain`) instead of O(size of `bases`) — was a linear scan over every
     * wildcard entry for every single DNS query (see PROJECT_STATE.md, perf audit
     * 2026-08-04). Safe while the bundled list is small (~55 wildcard entries), but the
     * remote custom-blocklist-URL feature (v2.5.0) lets `bases` grow to thousands of
     * entries from a public list — at that size the old linear scan would cost every
     * query (blocked or not) real CPU time in the VPN packet loop, the hottest path in
     * the app. Walking `domain`'s own parent suffixes and doing a hash-set lookup at
     * each level is equivalent in result (a wildcard entry only ever matches at one of
     * `domain`'s own suffix boundaries) but costs a handful of O(1) lookups (real-world
     * domain depth is almost always 2-5 labels) no matter how large `bases` gets.
     */
    private fun matchesAnyWildcard(domain: String, bases: Set<String>): Boolean {
        if (bases.isEmpty()) return false
        if (bases.contains(domain)) return true // domain itself listed as a wildcard base
        var dotIndex = domain.indexOf('.')
        while (dotIndex != -1) {
            val suffix = domain.substring(dotIndex + 1)
            if (bases.contains(suffix)) return true
            dotIndex = domain.indexOf('.', dotIndex + 1)
        }
        return false
    }

    /**
     * Exact-match first (fast O(1) path for the common case), then falls
     * back to checking wildcard bases only if there's no exact hit. User
     * "allow" overrides are checked first and always win, so a false
     * positive can always be fixed from the Rules screen without waiting
     * for an app update. Remote (downloaded) entries are checked alongside
     * the default+custom sets, same precedence — a domain blocked by ANY
     * of the three sources is blocked.
     */
    fun isBlocked(domain: String): Boolean {
        val d = domain.trim().lowercase().removeSuffix(".")
        if (d.isEmpty()) return false

        if (criticalAllowlist.contains(d)) return false

        if (allowedExact.contains(d)) return false
        if (allowedWildcardBases.isNotEmpty() && matchesAnyWildcard(d, allowedWildcardBases)) return false

        if (blockedExact.contains(d) || remoteBlockedExact.contains(d)) return true
        if (blockedWildcardBases.isNotEmpty() && matchesAnyWildcard(d, blockedWildcardBases)) return true
        if (remoteBlockedWildcardBases.isNotEmpty() && matchesAnyWildcard(d, remoteBlockedWildcardBases)) return true

        return false
    }

    companion object {
        @Volatile private var instance: BlocklistManager? = null

        /** Filename for the on-disk cache written by [BlocklistUpdateWorker]
         *  and read back by [loadCachedRemoteListIfPresent]. Kept here
         *  (rather than in the Worker) since BlocklistManager owns all
         *  blocklist state/merge logic per PROJECT_STATE.md decision #4. */
        const val REMOTE_BLOCKLIST_FILENAME = "custom_remote_blocklist.txt"

        fun getInstance(): BlocklistManager =
            instance ?: synchronized(this) {
                instance ?: BlocklistManager().also { instance = it }
            }
    }
}

/**
 * Downloads the user-supplied blocklist URL (see
 * `SettingsRepository.customBlocklistUrl`, set from the Rules screen),
 * caches it to disk, and loads it into the live [BlocklistManager] singleton
 * immediately (this Worker runs in-process — AdShield doesn't declare a
 * separate `:remote` process — so mutating the singleton directly here is
 * safe and takes effect right away without needing to restart the VPN).
 *
 * Kept in the same file as BlocklistManager (not a separate file) since the
 * two are tightly coupled and this keeps the batch within file-count limits;
 * this project already mixes small closely-related declarations in one file
 * (see util/Constants.kt: Constants + AppMode).
 *
 * Deliberately does NOT retry aggressively on failure (`Result.failure()`,
 * not `Result.retry()`) — same reasoning as the WARP reconnect cap in
 * WarpTunnelManager: if there's genuinely no network or the URL is bad, an
 * immediate retry loop just burns battery. The next scheduled periodic run
 * (or a manual "Perbarui Sekarang" tap) will try again naturally.
 */
class BlocklistUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settingsRepository = SettingsRepository(applicationContext)
        val url = settingsRepository.customBlocklistUrl.first()
        if (url.isBlank()) return@withContext Result.success()

        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                requestMethod = "GET"
            }
            val text = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val validLines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()

            if (validLines.isEmpty()) {
                settingsRepository.setBlocklistUpdateStatus("Gagal: file kosong atau tidak ada domain valid")
                return@withContext Result.failure()
            }

            // Write-then-rename so a process death mid-download never leaves
            // a half-written cache file that loadCachedRemoteListIfPresent
            // would read back corrupt on next VPN start.
            val finalFile = File(applicationContext.filesDir, BlocklistManager.REMOTE_BLOCKLIST_FILENAME)
            val tempFile = File(applicationContext.filesDir, "${BlocklistManager.REMOTE_BLOCKLIST_FILENAME}.tmp")
            tempFile.writeText(validLines.joinToString("\n"))
            if (!tempFile.renameTo(finalFile)) {
                settingsRepository.setBlocklistUpdateStatus("Gagal: tidak bisa menyimpan cache ke penyimpanan lokal")
                return@withContext Result.failure()
            }

            BlocklistManager.getInstance().loadRemoteList(validLines)

            settingsRepository.setBlocklistLastUpdated(System.currentTimeMillis())
            settingsRepository.setBlocklistUpdateStatus("Berhasil: ${validLines.size} domain dimuat")
            Result.success()
        } catch (e: Exception) {
            settingsRepository.setBlocklistUpdateStatus("Gagal: ${e.message ?: e::class.simpleName}")
            Result.failure()
        }
    }

    companion object {
        /** Unique work names for WorkManager's enqueueUniqueWork/enqueueUniquePeriodicWork
         *  dedup — periodic auto-refresh and manual "Perbarui Sekarang" are
         *  named separately so a manual refresh doesn't cancel/replace the
         *  scheduled periodic one, and vice versa. */
        const val PERIODIC_WORK_NAME = "blocklist_periodic_update"
        const val MANUAL_WORK_NAME = "blocklist_manual_update"
        const val PERIODIC_INTERVAL_HOURS = 24L
    }
}

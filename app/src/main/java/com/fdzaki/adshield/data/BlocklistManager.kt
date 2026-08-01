package com.fdzaki.adshield.data

import android.content.Context
import com.fdzaki.adshield.R
import java.io.BufferedReader
import java.io.InputStreamReader
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
        totalBlockedDomainCount = blockedExact.size + blockedWildcardBases.size
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
        totalBlockedDomainCount = blockedExact.size + blockedWildcardBases.size
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

    /** True if `domain` equals `base` or is a subdomain of `base` (for wildcard matching only). */
    private fun matchesWildcardBase(domain: String, base: String): Boolean =
        domain == base || domain.endsWith(".$base")

    private fun matchesAnyWildcard(domain: String, bases: Set<String>): Boolean {
        for (base in bases) if (matchesWildcardBase(domain, base)) return true
        return false
    }

    /**
     * Exact-match first (fast O(1) path for the common case), then falls
     * back to checking wildcard bases only if there's no exact hit. User
     * "allow" overrides are checked first and always win, so a false
     * positive can always be fixed from the Rules screen without waiting
     * for an app update.
     */
    fun isBlocked(domain: String): Boolean {
        val d = domain.trim().lowercase().removeSuffix(".")
        if (d.isEmpty()) return false

        if (allowedExact.contains(d)) return false
        if (allowedWildcardBases.isNotEmpty() && matchesAnyWildcard(d, allowedWildcardBases)) return false

        if (blockedExact.contains(d)) return true
        if (blockedWildcardBases.isNotEmpty() && matchesAnyWildcard(d, blockedWildcardBases)) return true

        return false
    }

    companion object {
        @Volatile private var instance: BlocklistManager? = null

        fun getInstance(): BlocklistManager =
            instance ?: synchronized(this) {
                instance ?: BlocklistManager().also { instance = it }
            }
    }
}

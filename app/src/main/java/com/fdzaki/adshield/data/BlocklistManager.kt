package com.fdzaki.adshield.data

import android.content.Context
import com.fdzaki.adshield.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Fast, thread-safe domain lookup used from the VPN packet-processing loop.
 * Backed by simple hash sets — a DNS query only needs an O(1) membership
 * check, never a full list scan.
 */
class BlocklistManager private constructor() {

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val allowedOverrides = ConcurrentHashMap.newKeySet<String>()
    private val whitelistedApps = ConcurrentHashMap.newKeySet<String>()

    @Volatile var totalBlockedDomainCount: Int = 0
        private set

    fun loadDefaultList(context: Context) {
        val reader = BufferedReader(
            InputStreamReader(context.resources.openRawResource(R.raw.blocklist_default))
        )
        reader.useLines { lines ->
            lines.forEach { rawLine ->
                val domain = normalizeHostsLine(rawLine)
                if (domain != null) blockedDomains.add(domain)
            }
        }
        totalBlockedDomainCount = blockedDomains.size
    }

    /** Parses a standard hosts-file style line ("0.0.0.0 ads.example.com") or a bare domain. */
    private fun normalizeHostsLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"))
        val domain = when {
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return null
        }
        val clean = domain.trim().lowercase().removeSuffix(".")
        if (clean.isEmpty() || clean == "localhost" || clean == "localhost.localdomain") return null
        return clean
    }

    fun setCustomBlocked(domains: Set<String>) {
        val extra = domains.mapNotNull { normalizeHostsLine(it) }
        blockedDomains.removeAll { it in customBlockedSnapshot && it !in extra }
        customBlockedSnapshot = extra.toSet()
        blockedDomains.addAll(extra)
        totalBlockedDomainCount = blockedDomains.size
    }

    private var customBlockedSnapshot: Set<String> = emptySet()

    fun setCustomAllowed(domains: Set<String>) {
        allowedOverrides.clear()
        allowedOverrides.addAll(domains.mapNotNull { normalizeHostsLine(it) })
    }

    fun setWhitelistedApps(packages: Set<String>) {
        whitelistedApps.clear()
        whitelistedApps.addAll(packages)
    }

    fun isAppWhitelisted(packageName: String?): Boolean =
        packageName != null && whitelistedApps.contains(packageName)

    /**
     * Checks a queried domain against blocklist + user overrides. Also
     * matches parent domains, so blocking "doubleclick.net" also covers
     * "ads.doubleclick.net", the way hosts-file blockers behave.
     */
    fun isBlocked(domain: String): Boolean {
        val d = domain.trim().lowercase().removeSuffix(".")
        if (d.isEmpty()) return false
        if (allowedOverrides.contains(d)) return false

        var segment = d
        while (true) {
            if (allowedOverrides.contains(segment)) return false
            if (blockedDomains.contains(segment)) return true
            val dot = segment.indexOf('.')
            if (dot < 0) break
            segment = segment.substring(dot + 1)
        }
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

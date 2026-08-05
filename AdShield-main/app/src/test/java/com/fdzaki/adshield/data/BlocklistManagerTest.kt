package com.fdzaki.adshield.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [BlocklistManager.isBlocked] and its supporting parse/merge logic —
 * per PROJECT_STATE.md this is the single most fragile part of the codebase
 * (already rewritten once in v1.1.0 after a real over-blocking complaint,
 * and load-bearing for a safety guarantee added in v1.2.0 + v2.5.0).
 *
 * IMPORTANT: [BlocklistManager] is a process-wide singleton (`getInstance()`).
 * These tests never touch [BlocklistManager.loadDefaultList] /
 * [BlocklistManager.loadCachedRemoteListIfPresent] (both require an Android
 * `Context`, unavailable in a local JVM test) — only the pure-Kotlin entry
 * points (`setCustomBlocked`, `setCustomAllowed`, `loadRemoteList`,
 * `setWhitelistedApps`, `isBlocked`) are exercised. [reset] fully clears
 * state before every test so tests can't leak into each other via the
 * shared singleton.
 */
class BlocklistManagerTest {

    private val manager = BlocklistManager.getInstance()

    private fun reset() {
        manager.setCustomBlocked(emptySet())
        manager.setCustomAllowed(emptySet())
        manager.loadRemoteList(emptyList())
        manager.setWhitelistedApps(emptySet())
    }

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    // ---- Exact-match by default (v1.1.0 core guarantee — do not regress) ----

    @Test
    fun `exact entry blocks only that exact domain, not subdomains`() {
        manager.setCustomBlocked(setOf("ads.example.com"))

        assertTrue(manager.isBlocked("ads.example.com"))
        assertFalse(manager.isBlocked("sub.ads.example.com"))
        assertFalse(manager.isBlocked("example.com"))
    }

    @Test
    fun `exact entry does not block its parent domain`() {
        manager.setCustomBlocked(setOf("ads.example.com"))
        assertFalse(manager.isBlocked("example.com"))
    }

    // ---- Wildcard entries (explicit *.domain opt-in) ----

    @Test
    fun `wildcard entry blocks the base domain and all subdomains`() {
        manager.setCustomBlocked(setOf("*.ads.example.com"))

        assertTrue(manager.isBlocked("ads.example.com"))       // base itself
        assertTrue(manager.isBlocked("sub.ads.example.com"))   // direct subdomain
        assertTrue(manager.isBlocked("deep.sub.ads.example.com")) // nested subdomain
        assertFalse(manager.isBlocked("example.com"))          // unrelated parent
        assertFalse(manager.isBlocked("otherads.example.com")) // NOT a suffix match, different label
    }

    @Test
    fun `wildcard base match is not fooled by suffix-only overlap`() {
        // "notads.example.com" must NOT match a "*.ads.example.com" wildcard
        // just because the string ends similarly — matching is by dot-
        // delimited label suffix ("endsWith('.' + base)"), not raw endsWith.
        manager.setCustomBlocked(setOf("*.ads.example.com"))
        assertFalse(manager.isBlocked("notads.example.com"))
    }

    // ---- Custom allow always wins over blocked ----

    @Test
    fun `custom allow overrides a blocked exact domain`() {
        manager.setCustomBlocked(setOf("example.com"))
        manager.setCustomAllowed(setOf("example.com"))
        assertFalse(manager.isBlocked("example.com"))
    }

    @Test
    fun `custom allow wildcard overrides blocked subdomains`() {
        manager.setCustomBlocked(setOf("*.example.com"))
        manager.setCustomAllowed(setOf("*.safe.example.com"))
        assertTrue(manager.isBlocked("ads.example.com"))
        assertFalse(manager.isBlocked("safe.example.com"))
        assertFalse(manager.isBlocked("api.safe.example.com"))
    }

    // ---- Critical allowlist: never overridable (v1.2.0 safety net) ----

    @Test
    fun `critical allowlist domain is never blocked even if user blocks it explicitly`() {
        manager.setCustomBlocked(setOf("connectivitycheck.gstatic.com"))
        assertFalse(manager.isBlocked("connectivitycheck.gstatic.com"))
    }

    @Test
    fun `critical allowlist domain is never blocked even via wildcard rule`() {
        manager.setCustomBlocked(setOf("*.gstatic.com"))
        // The wildcard would otherwise catch this subdomain, but the
        // critical-allowlist check runs first and always wins.
        assertFalse(manager.isBlocked("connectivitycheck.gstatic.com"))
        // Sibling subdomains not on the critical list are still blocked —
        // the allowlist protects specific essential hosts, not the whole
        // gstatic.com tree.
        assertTrue(manager.isBlocked("ads.gstatic.com"))
    }

    // ---- Normalization: case-insensitive, trailing-dot tolerant ----

    @Test
    fun `matching is case-insensitive and tolerates a trailing dot`() {
        manager.setCustomBlocked(setOf("Example.COM"))
        assertTrue(manager.isBlocked("example.com"))
        assertTrue(manager.isBlocked("EXAMPLE.COM."))
        assertTrue(manager.isBlocked("example.com."))
    }

    // ---- parseLine formats (hosts-file style, bare domain, wildcard, comments) ----

    @Test
    fun `hosts-file style lines are parsed as exact-match domains`() {
        manager.setCustomBlocked(setOf("0.0.0.0 tracker.example.com", "127.0.0.1 other.example.com"))
        assertTrue(manager.isBlocked("tracker.example.com"))
        assertTrue(manager.isBlocked("other.example.com"))
    }

    @Test
    fun `comments and blank-equivalent lines never become block entries`() {
        // setCustomBlocked receives a Set<String>, so blank/comment lines
        // here mainly guard parseLine's own robustness — none of these
        // should ever produce a domain that blocks anything.
        manager.setCustomBlocked(setOf("# just a comment", "", "   ", "localhost"))
        assertFalse(manager.isBlocked(""))
        assertFalse(manager.isBlocked("localhost"))
    }

    // ---- setCustomBlocked diffing: stale entries must actually disappear ----

    @Test
    fun `replacing the custom blocked set removes domains no longer present`() {
        manager.setCustomBlocked(setOf("a.example.com", "b.example.com"))
        assertTrue(manager.isBlocked("a.example.com"))
        assertTrue(manager.isBlocked("b.example.com"))

        manager.setCustomBlocked(setOf("b.example.com")) // "a" dropped from the new set
        assertFalse(manager.isBlocked("a.example.com"))
        assertTrue(manager.isBlocked("b.example.com"))
    }

    // ---- Remote list additive-safety (v2.5.0 guarantee) ----

    @Test
    fun `remote list domains are blocked alongside default and custom sources`() {
        manager.setCustomBlocked(setOf("custom.example.com"))
        manager.loadRemoteList(listOf("remote.example.com"))

        assertTrue(manager.isBlocked("custom.example.com"))
        assertTrue(manager.isBlocked("remote.example.com"))
    }

    @Test
    fun `an empty or failed remote list reload never removes custom blocked domains`() {
        manager.setCustomBlocked(setOf("custom.example.com"))
        manager.loadRemoteList(listOf("remote.example.com"))
        assertTrue(manager.isBlocked("remote.example.com"))

        // Simulates a failed/empty remote fetch replacing the remote set.
        manager.loadRemoteList(emptyList())

        assertFalse(manager.isBlocked("remote.example.com")) // remote entry is gone
        assertTrue(manager.isBlocked("custom.example.com"))  // custom entry untouched
    }

    // ---- Whitelisted apps bookkeeping ----

    @Test
    fun `hasWhitelistedApps and isAppWhitelisted reflect the current set`() {
        assertFalse(manager.hasWhitelistedApps())
        assertFalse(manager.isAppWhitelisted("com.example.app"))

        manager.setWhitelistedApps(setOf("com.example.app"))

        assertTrue(manager.hasWhitelistedApps())
        assertTrue(manager.isAppWhitelisted("com.example.app"))
        assertFalse(manager.isAppWhitelisted("com.other.app"))
        assertFalse(manager.isAppWhitelisted(null))
    }
}

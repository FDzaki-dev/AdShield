package com.fdzaki.adshield.data

import com.fdzaki.adshield.util.Constants
import java.util.concurrent.ConcurrentHashMap

/**
 * In-process cache of upstream DNS answers, keyed by "domain|qtype".
 *
 * Purpose (v3.7.0 — Internet Surfing Optimization): repeat queries for the
 * same domain (very common — trackers, CDNs, app telemetry all re-resolve
 * constantly) skip the upstream round-trip entirely and get answered
 * straight from the tun packet loop thread, shaving the full
 * socket-send/receive latency off every cache hit.
 *
 * Deliberately NOT a general-purpose LRU library: query volume here is
 * bursty but bounded (a phone doesn't realistically have tens of thousands
 * of distinct live domains), so a simple size-capped ConcurrentHashMap with
 * eager expiry checks on read is enough and avoids pulling in a dependency
 * for this one file.
 */
object DnsCache {

    private data class Entry(
        val answerBytes: ByteArray,
        val expiresAtMs: Long
    )

    private val map = ConcurrentHashMap<String, Entry>()

    private fun key(domain: String, qtype: Int): String = "$domain|$qtype"

    /** Returns the cached raw answer bytes (already TTL-fresh), or null on miss/expiry. */
    fun get(domain: String, qtype: Int): ByteArray? {
        val k = key(domain, qtype)
        val entry = map[k] ?: return null
        if (System.currentTimeMillis() >= entry.expiresAtMs) {
            map.remove(k, entry)
            return null
        }
        return entry.answerBytes
    }

    /**
     * Stores a positive answer. [ttlSeconds] should come from the upstream
     * reply's own TTL field (see DnsPacket) and is clamped to
     * [Constants.DNS_CACHE_MIN_TTL_SEC]..[Constants.DNS_CACHE_MAX_TTL_SEC] so
     * neither a 0/1s TTL (thrash) nor an unrealistically huge one (stale
     * entries surviving hours) can happen.
     */
    fun put(domain: String, qtype: Int, answerBytes: ByteArray, ttlSeconds: Long) {
        if (map.size >= Constants.DNS_CACHE_MAX_ENTRIES) {
            // Cheap eviction: drop a handful of already-expired entries first;
            // if none are expired yet, just skip inserting rather than pay
            // for a real LRU walk on the hot packet-loop path.
            val now = System.currentTimeMillis()
            val toRemove = map.entries.asSequence().filter { it.value.expiresAtMs <= now }.take(64).map { it.key }.toList()
            toRemove.forEach { map.remove(it) }
            if (map.size >= Constants.DNS_CACHE_MAX_ENTRIES) return
        }
        val clampedTtl = ttlSeconds.coerceIn(Constants.DNS_CACHE_MIN_TTL_SEC, Constants.DNS_CACHE_MAX_TTL_SEC)
        map[key(domain, qtype)] = Entry(answerBytes, System.currentTimeMillis() + clampedTtl * 1000L)
    }

    /** Called when the VPN (re)starts so stale entries from a previous session/network never leak in. */
    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size
}

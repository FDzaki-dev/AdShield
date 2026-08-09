package com.fdzaki.adshield.vpn.dns

import android.content.Context
import com.fdzaki.adshield.data.SettingsRepository
import com.fdzaki.adshield.data.db.AppDatabase
import com.fdzaki.adshield.data.db.DomainLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Increments blocked/allowed counters and, if enabled, persists a
 * per-domain log entry to Room. Extracted from AdBlockVpnService (v3.17.0
 * God-Class refactor, see PROJECT_STATE.md).
 *
 * Perf rewrite (v4.1.0 — "Radikal Perf" batch, see PROJECT_STATE.md):
 * [log] used to launch a brand-new coroutine AND perform a synchronous
 * DataStore disk write (via incrementBlocked()/incrementAllowed(), each its
 * own edit{} = temp-file-write + rename) for EVERY single DNS query handled
 * by the packet loop — the hottest path in the whole app. Under normal
 * browsing (dozens of queries/second across background apps) that meant
 * dozens of coroutine dispatches and real filesystem writes per second just
 * to keep two counters up to date.
 *
 * [log] is now a plain, allocation-free, non-suspending call: it only bumps
 * two [AtomicLong]s and (if logging is enabled) appends to a lock-free
 * queue — safe to call directly from the packet-loop / forwardExecutor
 * threads with no coroutine launch at all. A single background loop started
 * by [start] drains both every [FLUSH_INTERVAL_MS] into ONE DataStore
 * edit{} call and ONE batched Room insert transaction. [flush] is also
 * exposed directly so [stop] can do a final synchronous drain — no counts
 * or log entries are lost when the VPN stops between two periodic flushes.
 */
class DnsQueryLogger(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
) {
    private val blockedDelta = AtomicLong(0)
    private val allowedDelta = AtomicLong(0)
    private val pendingEntries = ConcurrentLinkedQueue<DomainLogEntity>()

    // Read on the hot path, written only by the collector below — avoids
    // calling settingsRepository.loggingEnabled.first() (a Flow collection)
    // once per DNS query just to check one boolean.
    @Volatile private var loggingEnabledCache = true
    private var enabledCollectorJob: Job? = null
    private var flushLoopJob: Job? = null
    private var pruneLoopJob: Job? = null

    fun log(domain: String, blocked: Boolean) {
        if (blocked) blockedDelta.incrementAndGet() else allowedDelta.incrementAndGet()
        if (loggingEnabledCache) {
            pendingEntries.add(DomainLogEntity(domain = domain, blocked = blocked))
            // Defensive cap: if a flush is somehow starved (e.g. Room stuck),
            // don't let this grow unbounded and eat memory — drop the oldest
            // buffered entries rather than crash or OOM. Matches the FIFO
            // retention philosophy already used by CrashLogger elsewhere.
            while (pendingEntries.size > MAX_PENDING_ENTRIES) {
                pendingEntries.poll()
            }
        }
    }

    /** Starts the periodic background flush + the logging-enabled cache
     *  collector. Call once per VPN session start; [stop] tears both down. */
    fun start() {
        enabledCollectorJob = scope.launch {
            settingsRepository.loggingEnabled.collect { loggingEnabledCache = it }
        }
        flushLoopJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
        // v4.3.0 — "Radikal Perf" batch 3 (see PROJECT_STATE.md): keeps the
        // domain_log Room table capped so it never decays query perf over a
        // long-running install (see DomainLogDao.pruneKeepingLatest kdoc).
        // Runs far less often than the flush loop above — a DELETE every 3s
        // would defeat the point.
        pruneLoopJob = scope.launch {
            while (isActive) {
                delay(PRUNE_INTERVAL_MS)
                runCatching {
                    AppDatabase.getInstance(context).domainLogDao().pruneKeepingLatest(PRUNE_KEEP_ROWS)
                }
            }
        }
    }

    /** Drains whatever is currently buffered into DataStore + Room. Safe to
     *  call concurrently with [log] (both use lock-free/atomic structures);
     *  a query landing in the gap between drain and DB write is simply
     *  picked up by the next flush instead of lost. */
    suspend fun flush() {
        val b = blockedDelta.getAndSet(0)
        val a = allowedDelta.getAndSet(0)
        if (b != 0L || a != 0L) {
            runCatching { settingsRepository.incrementCountersBy(b, a) }
        }

        if (pendingEntries.isNotEmpty()) {
            val batch = ArrayList<DomainLogEntity>(minOf(pendingEntries.size, MAX_PENDING_ENTRIES))
            while (true) {
                val entry = pendingEntries.poll() ?: break
                batch.add(entry)
            }
            if (batch.isNotEmpty()) {
                runCatching {
                    AppDatabase.getInstance(context).domainLogDao().insertAll(batch)
                }
            }
        }
    }

    /** Stops the periodic loop and synchronously flushes whatever is left
     *  buffered — called from AdBlockVpnService.stopVpn() so a stop right
     *  before a scheduled flush never silently drops counts/log entries. */
    suspend fun stop() {
        flushLoopJob?.cancel()
        enabledCollectorJob?.cancel()
        pruneLoopJob?.cancel()
        flushLoopJob = null
        enabledCollectorJob = null
        pruneLoopJob = null
        flush()
    }

    companion object {
        /** How often buffered counters/log entries are written to disk. */
        private const val FLUSH_INTERVAL_MS = 3000L
        /** Hard cap on in-memory buffered log entries between flushes. */
        private const val MAX_PENDING_ENTRIES = 500
        /** How often the domain_log table is pruned back down. */
        private const val PRUNE_INTERVAL_MS = 5 * 60 * 1000L
        /** Rows kept on prune — well above the 500-row UI LIMIT as a buffer. */
        private const val PRUNE_KEEP_ROWS = 2000
    }
}

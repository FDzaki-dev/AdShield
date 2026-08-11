package com.fdzaki.adshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainLogDao {

    @Insert
    suspend fun insert(entry: DomainLogEntity)

    // Perf (v4.1.0 — "Radikal Perf" batch, see PROJECT_STATE.md): batched
    // counterpart to insert() used by DnsQueryLogger's periodic flush instead
    // of one insert() + one coroutine launch per single DNS query. Room wraps
    // a List<> @Insert in one transaction, so N buffered entries cost one
    // disk write instead of N.
    @Insert
    suspend fun insertAll(entries: List<DomainLogEntity>)

    @Query("SELECT * FROM domain_log ORDER BY timestamp DESC LIMIT 500")
    fun recentEntries(): Flow<List<DomainLogEntity>>

    @Query("SELECT * FROM domain_log WHERE blocked = 1 ORDER BY timestamp DESC LIMIT 500")
    fun recentBlockedEntries(): Flow<List<DomainLogEntity>>

    @Query("DELETE FROM domain_log")
    suspend fun clearAll()

    @Query("DELETE FROM domain_log WHERE timestamp < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)

    // Perf (v4.3.0 — "Radikal Perf" batch 3, see PROJECT_STATE.md): this
    // table was previously NEVER pruned by anything (pruneOlderThan above
    // was defined but had zero callers) — it grew unbounded for the entire
    // lifetime of the install. Combined with the missing index (see
    // DomainLogEntity kdoc), every recentEntries()/recentBlockedEntries()
    // query got slower over weeks/months of use. The UI only ever shows the
    // most recent 500 rows (see the LIMIT above), so keeping anything
    // beyond a generous buffer past that has zero user-visible benefit.
    // Wired into DnsQueryLogger's periodic loop (every ~5 min, not every
    // flush — a DELETE+subquery every 3s would be its own waste).
    @Query("DELETE FROM domain_log WHERE id NOT IN (SELECT id FROM domain_log ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneKeepingLatest(keep: Int)

    @Query("SELECT COUNT(*) FROM domain_log")
    suspend fun count(): Int

    // v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md / DomainLogEntity
    // kdoc): backgroundApp is only ever non-null for a query made while the
    // screen was off, so this aggregation IS the leak report — no extra
    // WHERE clause needed beyond IS NOT NULL. LIMIT 50 caps the worst case
    // (many distinct apps) the same defensive way recentEntries() caps rows.
    @Query(
        "SELECT backgroundApp, COUNT(*) as count FROM domain_log " +
            "WHERE backgroundApp IS NOT NULL GROUP BY backgroundApp " +
            "ORDER BY count DESC LIMIT 50"
    )
    fun silentLeaks(): Flow<List<SilentLeakCount>>
}

/** Projection for [DomainLogDao.silentLeaks] — one row per app seen making
 *  DNS queries while the screen was off, most-frequent first. */
data class SilentLeakCount(
    val backgroundApp: String,
    val count: Int
)

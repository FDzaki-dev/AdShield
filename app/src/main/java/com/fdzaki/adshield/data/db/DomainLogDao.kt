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

    @Query("SELECT COUNT(*) FROM domain_log")
    suspend fun count(): Int
}

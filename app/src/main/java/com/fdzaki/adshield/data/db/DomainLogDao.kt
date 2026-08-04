package com.fdzaki.adshield.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainLogDao {

    @Insert
    suspend fun insert(entry: DomainLogEntity)

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

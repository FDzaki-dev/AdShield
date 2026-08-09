package com.fdzaki.adshield.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Perf (v4.3.0 — "Radikal Perf" batch 3, see PROJECT_STATE.md): added the
// index below. Every query in DomainLogDao orders/filters by `timestamp`
// (recentEntries/recentBlockedEntries/pruneOlderThan/pruneKeepingLatest) and
// this table had ZERO index before — every single one of those queries was
// a full table scan + sort, re-run on every ~3s DnsQueryLogger flush for as
// long as this table (which was also never pruned — see pruneKeepingLatest)
// kept growing. Schema version bumped 1->2 in AppDatabase; safe because
// AppDatabase already uses fallbackToDestructiveMigration() for this
// no-Migration-object setup, so this is just a local log table reset, not
// user-visible data loss of anything durable.
@Entity(tableName = "domain_log", indices = [Index(value = ["timestamp"])])
data class DomainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val blocked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

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
// v4.5.0 — Silent Leak Detector (see PROJECT_STATE.md): added `backgroundApp`,
// nullable, populated ONLY when the query happened while the screen was off
// (see ScreenStateMonitor + DnsPacketLoop). Deliberately NOT indexed: the
// aggregation query (DomainLogDao.silentLeaks()) scans this table, but the
// table is already kept ≤ ~2000 rows by pruneKeepingLatest (v4.3.0), so an
// unindexed GROUP BY over that bound is cheap — adding an index here would
// be the same over-eager-index mistake in reverse. Schema bumped 2->3;
// fallbackToDestructiveMigration() already in AppDatabase means this is
// just a local log-table reset, not durable user data loss.
@Entity(tableName = "domain_log", indices = [Index(value = ["timestamp"])])
data class DomainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val blocked: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val backgroundApp: String? = null
)

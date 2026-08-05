package com.fdzaki.adshield.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_log")
data class DomainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val blocked: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

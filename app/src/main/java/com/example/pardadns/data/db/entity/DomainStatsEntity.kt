package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-domain lifetime query counts. Top queried domains come from this table. */
@Entity(
    tableName = "domain_stats",
    indices = [
        Index(value = ["queryCount"]),
        Index(value = ["blockedCount"]),
    ]
)
data class DomainStatsEntity(
    @PrimaryKey val domain: String,
    val queryCount: Int = 0,
    val blockedCount: Int = 0,
)

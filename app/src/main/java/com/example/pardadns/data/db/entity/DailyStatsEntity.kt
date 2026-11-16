package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per calendar day — accumulates total and blocked query counts. */
@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val dateKey: String,   // "YYYY-MM-DD"
    val totalQueries: Int = 0,
    val blockedCount: Int = 0,
)

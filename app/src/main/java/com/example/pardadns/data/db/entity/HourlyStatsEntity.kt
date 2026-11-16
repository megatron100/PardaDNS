package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per calendar hour. [hourKey] format: "yyyy-MM-dd-HH"  (e.g. "2024-04-12-14").
 * Used to drive the adaptive line chart (hourly/2-hour/daily depending on data span).
 */
@Entity(tableName = "hourly_stats")
data class HourlyStatsEntity(
    @PrimaryKey val hourKey: String,
    val totalQueries: Int = 0,
    val blockedCount: Int = 0,
)

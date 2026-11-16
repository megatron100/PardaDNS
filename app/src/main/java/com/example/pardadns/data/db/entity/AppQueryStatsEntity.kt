package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-app lifetime query counts. Top queried / blocked apps come from this table. */
@Entity(
    tableName = "app_query_stats",
    indices = [
        Index(value = ["queryCount"]),
        Index(value = ["blockedCount"]),
    ]
)
data class AppQueryStatsEntity(
    @PrimaryKey val packageName: String,
    val queryCount: Int = 0,
    val blockedCount: Int = 0,
)

package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Full query log for advanced DNS logging feature.
 * Stores individual DNS queries with search and filter capability.
 * Rolling retention of 10,000 entries to prevent unbounded growth.
 */
@Entity(
    tableName = "query_log",
    indices = [
        Index(value = ["domain"]),
        Index(value = ["timestamp"]),
        Index(value = ["blocked"]),
        Index(value = ["packageName"]),
    ]
)
data class QueryLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val timestamp: Long = System.currentTimeMillis(),
    val blocked: Boolean,
    val responseTimeMs: Int = 0,       // Latency in milliseconds
    val packageName: String? = null,   // App that made the query
    val providerName: String? = null,  // DNS provider used
    val queryType: String = "A",       // A, AAAA, CNAME, etc.
)

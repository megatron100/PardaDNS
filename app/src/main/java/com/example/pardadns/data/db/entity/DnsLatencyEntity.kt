package com.example.pardadns.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks DNS response latency per provider. Aggregates min/max/avg/count
 * for performance comparison across DNS providers.
 */
@Entity(tableName = "dns_latency")
data class DnsLatencyEntity(
    @PrimaryKey val providerName: String,
    val totalQueries: Int = 0,
    val sumLatencyMs: Long = 0,        // Sum of all latencies for avg calculation
    val minLatencyMs: Int = Int.MAX_VALUE,
    val maxLatencyMs: Int = 0,
    val lastLatencyMs: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    /** Average latency in milliseconds. */
    val avgLatencyMs: Int get() = if (totalQueries == 0) 0 else (sumLatencyMs / totalQueries).toInt()
}

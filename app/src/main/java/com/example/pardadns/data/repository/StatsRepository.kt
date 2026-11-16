package com.example.pardadns.data.repository

import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.db.entity.HourlyStatsEntity
import com.example.pardadns.data.db.entity.QueryLogEntity
import kotlinx.coroutines.flow.Flow

/** Read-only dashboard data exposed to the UI layer. */
data class LifetimeTotals(val totalQueries: Int, val blockedCount: Int) {
    val blockRate: Float get() = if (totalQueries == 0) 0f else blockedCount.toFloat() / totalQueries
}

interface StatsRepository {
    /** Upsert a single DNS query event — called from the VPN service per query. */
    suspend fun recordQuery(domain: String, packageName: String?, blocked: Boolean, dateKey: String, hourKey: String)

    /** Record DNS query with latency and full logging for Pro features. */
    suspend fun recordQueryWithLatency(
        domain: String,
        packageName: String?,
        blocked: Boolean,
        dateKey: String,
        hourKey: String,
        latencyMs: Int,
        providerName: String,
        queryType: String = "A",
    )

    fun observeLifetimeTotals(): Flow<LifetimeTotals>
    fun observeTopBlockedApps(): Flow<List<AppQueryStatsEntity>>
    fun observeTopDomains(): Flow<List<DomainStatsEntity>>
    fun observeTopBlockedDomains(): Flow<List<DomainStatsEntity>>
    fun observeAllDomains(): Flow<List<DomainStatsEntity>>
    fun observeAllBlockedDomains(): Flow<List<DomainStatsEntity>>
    fun observeAllBlockedApps(): Flow<List<AppQueryStatsEntity>>
    fun observeRecentDays(): Flow<List<DailyStatsEntity>>
    fun observeRecentHours(): Flow<List<HourlyStatsEntity>>
    fun observeAllApps(): Flow<List<AppQueryStatsEntity>>

    // ── DNS Performance (Pro feature) ────────────────────────────────────────
    fun observeAllLatencies(): Flow<List<DnsLatencyEntity>>

    // ── Query Log (Pro feature) ──────────────────────────────────────────────
    fun observeRecentQueries(limit: Int = 500): Flow<List<QueryLogEntity>>
    fun searchQueries(search: String, limit: Int = 500): Flow<List<QueryLogEntity>>
    fun searchAndFilterQueries(
        search: String? = null,
        blocked: Boolean? = null,
        packageName: String? = null,
        limit: Int = 500,
    ): Flow<List<QueryLogEntity>>

    /** Delete all stats data from all tables. */
    suspend fun clearAllStats()
}

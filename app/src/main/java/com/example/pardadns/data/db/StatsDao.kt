package com.example.pardadns.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.db.entity.HourlyStatsEntity
import com.example.pardadns.data.db.entity.QueryLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    // ── Hourly stats ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHourIfAbsent(row: HourlyStatsEntity)

    @Query("UPDATE hourly_stats SET totalQueries = totalQueries + 1, blockedCount = blockedCount + :blocked WHERE hourKey = :hourKey")
    suspend fun incrementHour(hourKey: String, blocked: Int)

    /** Rolling 72-hour window (3 days), newest first. Enough for hourly and 2-hour views. */
    @Query("SELECT * FROM hourly_stats ORDER BY hourKey DESC LIMIT 72")
    fun observeRecentHours(): Flow<List<HourlyStatsEntity>>

    // ── Daily stats ───────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayIfAbsent(row: DailyStatsEntity)

    @Query("UPDATE daily_stats SET totalQueries = totalQueries + 1, blockedCount = blockedCount + :blocked WHERE dateKey = :dateKey")
    suspend fun incrementDay(dateKey: String, blocked: Int)

    /** Rolling 30-day window, newest first. */
    @Query("SELECT * FROM daily_stats ORDER BY dateKey DESC LIMIT 30")
    fun observeRecentDays(): Flow<List<DailyStatsEntity>>

    /** All-time totals across every recorded day. */
    @Query("SELECT COALESCE(SUM(totalQueries),0) AS totalQueries, COALESCE(SUM(blockedCount),0) AS blockedCount FROM daily_stats")
    fun observeLifetimeTotals(): Flow<LifetimeTotalsRow>

    // ── Domain stats ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDomainIfAbsent(row: DomainStatsEntity)

    @Query("UPDATE domain_stats SET queryCount = queryCount + 1, blockedCount = blockedCount + :blocked WHERE domain = :domain")
    suspend fun incrementDomain(domain: String, blocked: Int)

    /** Top 10 queried domains by total query count. */
    @Query("SELECT * FROM domain_stats ORDER BY queryCount DESC LIMIT 10")
    fun observeTopDomains(): Flow<List<DomainStatsEntity>>

    /** All queried domains by total query count (no limit). */
    @Query("SELECT * FROM domain_stats ORDER BY queryCount DESC")
    fun observeAllDomains(): Flow<List<DomainStatsEntity>>

    /** Top 10 blocked domains by blocked count. */
    @Query("SELECT * FROM domain_stats WHERE blockedCount > 0 ORDER BY blockedCount DESC LIMIT 10")
    fun observeTopBlockedDomains(): Flow<List<DomainStatsEntity>>

    /** All blocked domains by blocked count (no limit). */
    @Query("SELECT * FROM domain_stats WHERE blockedCount > 0 ORDER BY blockedCount DESC")
    fun observeAllBlockedDomains(): Flow<List<DomainStatsEntity>>

    // ── App stats ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppIfAbsent(row: AppQueryStatsEntity)

    @Query("UPDATE app_query_stats SET queryCount = queryCount + 1, blockedCount = blockedCount + :blocked WHERE packageName = :pkg")
    suspend fun incrementApp(pkg: String, blocked: Int)

    /** Top 10 apps by blocked query count. */
    @Query("SELECT * FROM app_query_stats ORDER BY blockedCount DESC LIMIT 10")
    fun observeTopBlockedApps(): Flow<List<AppQueryStatsEntity>>

    /** All apps by blocked query count (no limit). */
    @Query("SELECT * FROM app_query_stats ORDER BY blockedCount DESC")
    fun observeAllBlockedApps(): Flow<List<AppQueryStatsEntity>>

    /** All apps ordered by query count (no limit). */
    @Query("SELECT * FROM app_query_stats ORDER BY queryCount DESC")
    fun observeAllApps(): Flow<List<AppQueryStatsEntity>>

    // ── Delete all stats ──────────────────────────────────────────────────────

    @Query("DELETE FROM hourly_stats")
    suspend fun deleteAllHourlyStats()

    @Query("DELETE FROM daily_stats")
    suspend fun deleteAllDailyStats()

    @Query("DELETE FROM domain_stats")
    suspend fun deleteAllDomainStats()

    @Query("DELETE FROM app_query_stats")
    suspend fun deleteAllAppStats()

    // ── DNS Latency stats ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLatencyIfAbsent(row: DnsLatencyEntity)

    @Query("""
        UPDATE dns_latency 
        SET totalQueries = totalQueries + 1,
            sumLatencyMs = sumLatencyMs + :latencyMs,
            minLatencyMs = MIN(minLatencyMs, :latencyMs),
            maxLatencyMs = MAX(maxLatencyMs, :latencyMs),
            lastLatencyMs = :latencyMs,
            lastUpdated = :timestamp
        WHERE providerName = :providerName
    """)
    suspend fun incrementLatency(providerName: String, latencyMs: Int, timestamp: Long)

    /** All DNS providers by query count (performance comparison). */
    @Query("SELECT * FROM dns_latency ORDER BY totalQueries DESC")
    fun observeAllLatencies(): Flow<List<DnsLatencyEntity>>

    @Query("DELETE FROM dns_latency")
    suspend fun deleteAllLatencyStats()

    // ── Query Log (full history) ──────────────────────────────────────────────

    @Insert
    suspend fun insertQueryLog(row: QueryLogEntity)

    /** Rolling retention: delete oldest entries beyond limit. */
    @Query("DELETE FROM query_log WHERE id NOT IN (SELECT id FROM query_log ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneQueryLog(limit: Int = 10000)

    /** Recent queries, newest first. */
    @Query("SELECT * FROM query_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentQueries(limit: Int = 500): Flow<List<QueryLogEntity>>

    /** Search queries by domain name. */
    @Query("SELECT * FROM query_log WHERE domain LIKE '%' || :search || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun searchQueries(search: String, limit: Int = 500): Flow<List<QueryLogEntity>>

    /** Filter queries by blocked status. */
    @Query("SELECT * FROM query_log WHERE blocked = :blocked ORDER BY timestamp DESC LIMIT :limit")
    fun filterQueriesByBlocked(blocked: Boolean, limit: Int = 500): Flow<List<QueryLogEntity>>

    /** Filter queries by app package. */
    @Query("SELECT * FROM query_log WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT :limit")
    fun filterQueriesByApp(packageName: String, limit: Int = 500): Flow<List<QueryLogEntity>>

    /** Combined search and filter. */
    @Query("""
        SELECT * FROM query_log 
        WHERE (:search IS NULL OR domain LIKE '%' || :search || '%')
          AND (:blocked IS NULL OR blocked = :blocked)
          AND (:packageName IS NULL OR packageName = :packageName)
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun searchAndFilterQueries(
        search: String? = null,
        blocked: Boolean? = null,
        packageName: String? = null,
        limit: Int = 500,
    ): Flow<List<QueryLogEntity>>

    @Query("DELETE FROM query_log")
    suspend fun deleteAllQueryLogs()
}

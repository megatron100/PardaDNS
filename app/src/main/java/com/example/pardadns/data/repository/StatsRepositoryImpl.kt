package com.example.pardadns.data.repository

import androidx.room.withTransaction
import com.example.pardadns.data.db.LifetimeTotalsRow
import com.example.pardadns.data.db.PardaDatabase
import com.example.pardadns.data.db.StatsDao
import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.db.entity.HourlyStatsEntity
import com.example.pardadns.data.db.entity.QueryLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val db: PardaDatabase,
    private val dao: StatsDao,
) : StatsRepository {

    /** Counter to throttle expensive pruneQueryLog — only runs every 100 inserts. */
    private var queryLogInsertCount = 0

    override suspend fun recordQuery(
        domain: String,
        packageName: String?,
        blocked: Boolean,
        dateKey: String,
        hourKey: String,
    ) {
        val blockedInt = if (blocked) 1 else 0

        db.withTransaction {
            // Hourly
            dao.insertHourIfAbsent(HourlyStatsEntity(hourKey))
            dao.incrementHour(hourKey, blockedInt)

            // Daily
            dao.insertDayIfAbsent(DailyStatsEntity(dateKey))
            dao.incrementDay(dateKey, blockedInt)

            // Domain
            dao.insertDomainIfAbsent(DomainStatsEntity(domain))
            dao.incrementDomain(domain, blockedInt)

            // App (optional — only recorded when we know the caller's package)
            if (packageName != null) {
                dao.insertAppIfAbsent(AppQueryStatsEntity(packageName))
                dao.incrementApp(packageName, blockedInt)
            }
        }
    }

    override suspend fun recordQueryWithLatency(
        domain: String,
        packageName: String?,
        blocked: Boolean,
        dateKey: String,
        hourKey: String,
        latencyMs: Int,
        providerName: String,
        queryType: String,
    ) {
        val blockedInt = if (blocked) 1 else 0
        val now = System.currentTimeMillis()

        db.withTransaction {
            // Hourly
            dao.insertHourIfAbsent(HourlyStatsEntity(hourKey))
            dao.incrementHour(hourKey, blockedInt)

            // Daily
            dao.insertDayIfAbsent(DailyStatsEntity(dateKey))
            dao.incrementDay(dateKey, blockedInt)

            // Domain
            dao.insertDomainIfAbsent(DomainStatsEntity(domain))
            dao.incrementDomain(domain, blockedInt)

            // App (optional — only recorded when we know the caller's package)
            if (packageName != null) {
                dao.insertAppIfAbsent(AppQueryStatsEntity(packageName))
                dao.incrementApp(packageName, blockedInt)
            }

            // DNS Latency tracking (Pro feature)
            // Skip locally-blocked queries (latencyMs == 0) — they never hit the
            // network, so including them would drag down avg and pin min to 0 ms.
            if (latencyMs > 0) {
                dao.insertLatencyIfAbsent(DnsLatencyEntity(providerName))
                dao.incrementLatency(providerName, latencyMs, now)
            }

            // Query Log (Pro feature) — rolling retention
            dao.insertQueryLog(
                QueryLogEntity(
                    domain = domain,
                    timestamp = now,
                    blocked = blocked,
                    responseTimeMs = latencyMs,
                    packageName = packageName,
                    providerName = providerName,
                    queryType = queryType,
                )
            )

            // Prune only every 100 inserts to avoid expensive subquery on every query
            if (++queryLogInsertCount % 100 == 0) {
                dao.pruneQueryLog(limit = 10000)
            }
        }
    }

    override fun observeLifetimeTotals(): Flow<LifetimeTotals> =
        dao.observeLifetimeTotals().map { row ->
            LifetimeTotals(totalQueries = row.totalQueries, blockedCount = row.blockedCount)
        }

    override fun observeTopBlockedApps(): Flow<List<AppQueryStatsEntity>> =
        dao.observeTopBlockedApps()

    override fun observeTopDomains(): Flow<List<DomainStatsEntity>> =
        dao.observeTopDomains()

    override fun observeTopBlockedDomains(): Flow<List<DomainStatsEntity>> =
        dao.observeTopBlockedDomains()

    override fun observeAllDomains(): Flow<List<DomainStatsEntity>> =
        dao.observeAllDomains()

    override fun observeAllBlockedDomains(): Flow<List<DomainStatsEntity>> =
        dao.observeAllBlockedDomains()

    override fun observeAllBlockedApps(): Flow<List<AppQueryStatsEntity>> =
        dao.observeAllBlockedApps()

    override fun observeRecentDays(): Flow<List<DailyStatsEntity>> =
        dao.observeRecentDays()

    override fun observeRecentHours(): Flow<List<HourlyStatsEntity>> =
        dao.observeRecentHours()

    override fun observeAllApps(): Flow<List<AppQueryStatsEntity>> =
        dao.observeAllApps()

    // ── DNS Performance (Pro feature) ────────────────────────────────────────
    override fun observeAllLatencies(): Flow<List<DnsLatencyEntity>> =
        dao.observeAllLatencies()

    // ── Query Log (Pro feature) ──────────────────────────────────────────────
    override fun observeRecentQueries(limit: Int): Flow<List<QueryLogEntity>> =
        dao.observeRecentQueries(limit)

    override fun searchQueries(search: String, limit: Int): Flow<List<QueryLogEntity>> =
        dao.searchQueries(search, limit)

    override fun searchAndFilterQueries(
        search: String?,
        blocked: Boolean?,
        packageName: String?,
        limit: Int,
    ): Flow<List<QueryLogEntity>> =
        dao.searchAndFilterQueries(search, blocked, packageName, limit)

    override suspend fun clearAllStats() {
        db.withTransaction {
            dao.deleteAllHourlyStats()
            dao.deleteAllDailyStats()
            dao.deleteAllDomainStats()
            dao.deleteAllAppStats()
            dao.deleteAllLatencyStats()
            dao.deleteAllQueryLogs()
        }
    }
}

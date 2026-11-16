package com.example.pardadns.ui.analytics

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.db.entity.HourlyStatsEntity
import com.example.pardadns.data.repository.LifetimeTotals
import com.example.pardadns.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repo: StatsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val appIcons  = MutableStateFlow<Map<String, Drawable>>(emptyMap())

    /** False until the first DB query completes — drives the initial full-screen loader. */
    private val _hasLoaded    = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    /** True while a manual refresh triggered by the user is running. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _totals            = MutableStateFlow(LifetimeTotals(0, 0))
    val totals: StateFlow<LifetimeTotals> = _totals.asStateFlow()

    private val _topBlockedApps    = MutableStateFlow<List<AppEntry>>(emptyList())
    val topBlockedApps: StateFlow<List<AppEntry>> = _topBlockedApps.asStateFlow()

    private val _topDomains        = MutableStateFlow<List<DomainStatsEntity>>(emptyList())
    val topDomains: StateFlow<List<DomainStatsEntity>> = _topDomains.asStateFlow()

    private val _topBlockedDomains = MutableStateFlow<List<DomainStatsEntity>>(emptyList())
    val topBlockedDomains: StateFlow<List<DomainStatsEntity>> = _topBlockedDomains.asStateFlow()

    private val _recentDays        = MutableStateFlow<List<DailyStatsEntity>>(emptyList())
    val recentDays: StateFlow<List<DailyStatsEntity>> = _recentDays.asStateFlow()

    private val _todayStats        = MutableStateFlow<DailyStatsEntity?>(null)
    val todayStats: StateFlow<DailyStatsEntity?> = _todayStats.asStateFlow()

    private val _chartSeries       = MutableStateFlow(ChartSeries.EMPTY)
    val chartSeries: StateFlow<ChartSeries> = _chartSeries.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (labels, icons) = buildAppInfoMaps(context)
            appLabels.value = labels
            appIcons.value  = icons
        }
    }

    /**
     * Called from [AnalyticsScreen]'s `LaunchedEffect` every 2 seconds.
     * On first call, shows a full-screen spinner via [hasLoaded].
     * Subsequent calls update data silently with no visible loading indicator.
     */
    suspend fun poll() = doLoad()

    /** Triggered by the manual refresh button — shows a small spinner in the toolbar. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { doLoad() } finally { _isRefreshing.value = false }
        }
    }

    /** Queries all Room tables on IO and pushes results to the state flows. */
    private suspend fun doLoad() = withContext(Dispatchers.IO) {
        val totals      = repo.observeLifetimeTotals().first()
        val apps        = repo.observeTopBlockedApps().first()
        val domains     = repo.observeTopDomains().first()
        val blockedDoms = repo.observeTopBlockedDomains().first()
        val days        = repo.observeRecentDays().first()
        val hours       = repo.observeRecentHours().first()
        val labels      = appLabels.value
        val icons       = appIcons.value

        _totals.value            = totals
        _topBlockedApps.value    = apps.map { it.toEntry(labels, icons) }
        _topDomains.value        = domains
        _topBlockedDomains.value = blockedDoms
        _recentDays.value        = days
        _todayStats.value        = days.firstOrNull()
        _chartSeries.value       = buildChartSeries(hours, days)
        _hasLoaded.value         = true
    }

    /** Clear all stats data and refresh the UI. */
    fun clearAllStats() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.clearAllStats()
                doLoad()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

// ── Chart models ──────────────────────────────────────────────────────────────

enum class ChartGranularity(val label: String) {
    HOURLY("Last 24 hours"),
    TWO_HOUR("Last 48 hours"),
    DAILY("Last 30 days"),
}

data class ChartSeries(
    val totalPoints: List<Int>,
    val blockedPoints: List<Int>,
    val xLabels: List<String>,
    val granularity: ChartGranularity,
) {
    companion object {
        val EMPTY = ChartSeries(
            totalPoints   = List(24) { 0 },
            blockedPoints = List(24) { 0 },
            xLabels       = listOf("23h ago", "Now"),
            granularity   = ChartGranularity.HOURLY,
        )
    }
}

// ── Adaptive builder ──────────────────────────────────────────────────────────

private fun buildChartSeries(
    hours: List<HourlyStatsEntity>,
    @Suppress("UNUSED_PARAMETER") days: List<DailyStatsEntity>,
): ChartSeries {
    // Always show last 24 hours (full day) — graph is always full width
    return buildHourlyChart(hours, slots = 24)
}

private fun buildHourlyChart(hours: List<HourlyStatsEntity>, slots: Int): ChartSeries {
    val byHour = hours.associate { it.hourKey to (it.totalQueries to it.blockedCount) }
    val now    = LocalDateTime.now()
    val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

    val total   = mutableListOf<Int>()
    val blocked = mutableListOf<Int>()
    repeat(slots) { offset ->
        val key = now.minusHours((slots - 1 - offset).toLong()).format(fmt)
        val (q, b) = byHour[key] ?: (0 to 0)
        total.add(q); blocked.add(b)
    }
    val labels = listOf("${slots - 1}h ago", "Now")
    return ChartSeries(total, blocked, labels, ChartGranularity.HOURLY)
}

private fun buildTwoHourChart(hours: List<HourlyStatsEntity>, slots: Int): ChartSeries {
    val byHour = hours.associate { it.hourKey to (it.totalQueries to it.blockedCount) }
    val now    = LocalDateTime.now()
    val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")

    val total   = mutableListOf<Int>()
    val blocked = mutableListOf<Int>()
    repeat(slots) { offset ->
        // Each slot = 2 hours; sum both hours
        val h1 = now.minusHours(((slots - 1 - offset) * 2).toLong()).format(fmt)
        val h2 = now.minusHours(((slots - 1 - offset) * 2 + 1).toLong()).format(fmt)
        val (q1, b1) = byHour[h1] ?: (0 to 0)
        val (q2, b2) = byHour[h2] ?: (0 to 0)
        total.add(q1 + q2); blocked.add(b1 + b2)
    }
    val hours48 = slots * 2
    val labels  = listOf("${hours48}h ago", "Now")
    return ChartSeries(total, blocked, labels, ChartGranularity.TWO_HOUR)
}

private fun buildDailyChart(days: List<DailyStatsEntity>, slots: Int): ChartSeries {
    val byDate = days.associate { it.dateKey to (it.totalQueries to it.blockedCount) }
    val today  = LocalDate.now()

    val total   = mutableListOf<Int>()
    val blocked = mutableListOf<Int>()
    repeat(slots) { offset ->
        val key = today.minusDays((slots - 1 - offset).toLong()).toString()
        val (q, b) = byDate[key] ?: (0 to 0)
        total.add(q); blocked.add(b)
    }
    val labels = listOf("${slots}d ago", "Today")
    return ChartSeries(total, blocked, labels, ChartGranularity.DAILY)
}

// ── UI model ──────────────────────────────────────────────────────────────────

/** UI model for a single app row — label and icon are already resolved. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val queryCount: Int,
    val blockedCount: Int,
    val icon: Drawable? = null,
)

private fun AppQueryStatsEntity.toEntry(
    labels: Map<String, String>,
    icons: Map<String, Drawable>,
) = AppEntry(
    packageName  = packageName,
    label        = labels[packageName] ?: packageName.substringAfterLast('.'),
    queryCount   = queryCount,
    blockedCount = blockedCount,
    icon         = icons[packageName],
)

private fun buildAppInfoMaps(context: Context): Pair<Map<String, String>, Map<String, Drawable>> {
    val pm = context.packageManager
    return try {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, android.content.pm.PackageManager.GET_META_DATA)
        val labels = activities.associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        val icons = activities.mapNotNull { info ->
            try {
                info.activityInfo.packageName to info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
        }.toMap()
        labels to icons
    } catch (_: Exception) {
        emptyMap<String, String>() to emptyMap()
    }
}

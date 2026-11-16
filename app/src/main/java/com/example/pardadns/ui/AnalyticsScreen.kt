package com.example.pardadns.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.db.entity.DailyStatsEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.ui.analytics.AnalyticsViewModel
import com.example.pardadns.ui.analytics.AppEntry
import com.example.pardadns.ui.analytics.ChartSeries
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults

// ── Palette ───────────────────────────────────────────────────────────────────
internal val Indigo600   = Color(0xFF4F46E5)
internal val IndigoLight = Color(0xFFEEF2FF)
internal val Red500      = Color(0xFFEF4444)
private  val RedLight    = Color(0xFFFEF2F2)
internal val Green500    = Color(0xFF22C55E)
private  val GreenLight  = Color(0xFFF0FDF4)
// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    onViewAllQueries: () -> Unit = {},
    onViewAllBlockedQueries: () -> Unit = {},
    onViewAllBlockedApps: () -> Unit = {},
    onViewDnsPerformance: () -> Unit = {},
    onViewQueryLog: () -> Unit = {},
    blockedDomains: Set<String> = emptySet(),
    onBlockToggle: (domain: String, block: Boolean) -> Unit = { _, _ -> },
    isMonitoring: Boolean = true,
    onToggleMonitoring: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    vm: AnalyticsViewModel = hiltViewModel(),
) {
    val hasLoaded    by vm.hasLoaded.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val totals       by vm.totals.collectAsStateWithLifecycle()
    val topApps      by vm.topBlockedApps.collectAsStateWithLifecycle()
    val topDomains   by vm.topDomains.collectAsStateWithLifecycle()
    val topBlockedDomains by vm.topBlockedDomains.collectAsStateWithLifecycle()
    val chartSeries  by vm.chartSeries.collectAsStateWithLifecycle()

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Stats?") },
            text = { Text("This will permanently delete all analytics data including queries, blocked domains, and app statistics. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAllStats()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Red500)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Analytics", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasLoaded) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !isRefreshing,
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete all stats",
                                tint = Red500,
                            )
                        }
                        IconButton(
                            onClick  = { vm.refresh() },
                            enabled  = !isRefreshing,
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        // 2-second polling loop — active only while this screen is in composition.
        LaunchedEffect(Unit) {
            while (true) {
                vm.poll()
                delay(2_000L)
            }
        }

        if (!hasLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (totals.totalQueries == 0 && chartSeries.totalPoints.all { it == 0 }) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding()
                    + contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Pause / Resume monitoring chip ───────────────────────────────
            item {
                AssistChip(
                    onClick = { onToggleMonitoring(!isMonitoring) },
                    label = {
                        Text(
                            if (isMonitoring) "Monitoring Active" else "Monitoring Paused",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isMonitoring) GreenLight else RedLight,
                        labelColor = if (isMonitoring) Green500 else Red500,
                        leadingIconContentColor = if (isMonitoring) Green500 else Red500,
                    ),
                )
            }

            // ── Activity line chart ───────────────────────────────────────────
            item { QueryActivityCard(series = chartSeries) }

            // ── DNS Queries | Blocked + Block Rate ───────────────────────────
            item {
                StatsOverviewCard(
                    totalQueries = totals.totalQueries,
                    blockedCount = totals.blockedCount,
                    blockRate    = totals.blockRate,
                )
            }

            // ── Top queried domains ───────────────────────────────────────────
            if (topDomains.isNotEmpty()) {
                item { TopQueriesCard(domains = topDomains, onViewAll = onViewAllQueries, blockedDomains = blockedDomains, onBlockToggle = onBlockToggle) }
            }

            // ── Top blocked domains ───────────────────────────────────────────
            if (topBlockedDomains.isNotEmpty()) {
                item { TopBlockedQueriesCard(domains = topBlockedDomains, onViewAll = onViewAllBlockedQueries, blockedDomains = blockedDomains, onBlockToggle = onBlockToggle) }
            }

            // ── Top blocked apps (shown only if data exists) ──────────────────
            if (topApps.isNotEmpty()) {
                item { TopBlockedAppsCard(apps = topApps, onViewAll = onViewAllBlockedApps) }
            }

            // ── Pro Features Section ──────────────────────────────────────────
            item {
                Text(
                    "Pro Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                ProFeatureCard(
                    title = "DNS Performance",
                    description = "Compare response times across providers",
                    icon = Icons.Outlined.BarChart,
                    onClick = onViewDnsPerformance,
                )
            }
            item {
                ProFeatureCard(
                    title = "Query Log",
                    description = "Full query history with search & filter",
                    icon = Icons.Outlined.History,
                    onClick = onViewQueryLog,
                )
            }
        }
    }
}

// ── Query Activity card ───────────────────────────────────────────────────────

@Composable
private fun QueryActivityCard(series: ChartSeries) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    AnalyticsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row with collapse toggle — entire row is clickable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Query Activity",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (expanded) {
                        Text(
                            series.granularity.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector        = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }

            // Chart content — hidden when collapsed
            if (expanded) {
                DualLineChart(series = series)

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LegendDot(color = Green500, label = "Total queries")
                    LegendDot(color = Red500,   label = "Blocked")
                }

                // x-axis: first and last label only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(series.xLabels.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(series.xLabels.last(),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DualLineChart(series: ChartSeries) {
    val maxVal = maxOf(
        series.totalPoints.maxOrNull() ?: 0,
        series.blockedPoints.maxOrNull() ?: 0,
    ).coerceAtLeast(1)

    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val w = size.width
        val h = size.height
        val padTop    = 12f
        val padBottom = 8f
        val chartH    = h - padTop - padBottom

        // Horizontal grid lines (4 lines)
        for (i in 0..3) {
            val y = padTop + chartH * (i / 3f)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // Draw filled area + line for each series
        drawSeries(
            data        = series.totalPoints,
            maxVal      = maxVal,
            width       = w,
            height      = chartH,
            padTop      = padTop,
            lineColor   = Green500,
            fillStart   = Green500.copy(alpha = 0.20f),
            fillEnd     = Green500.copy(alpha = 0.01f),
        )
        drawSeries(
            data        = series.blockedPoints,
            maxVal      = maxVal,
            width       = w,
            height      = chartH,
            padTop      = padTop,
            lineColor   = Red500,
            fillStart   = Red500.copy(alpha = 0.18f),
            fillEnd     = Red500.copy(alpha = 0.01f),
        )
    }
}

private fun DrawScope.drawSeries(
    data: List<Int>,
    maxVal: Int,
    width: Float,
    height: Float,
    padTop: Float,
    lineColor: Color,
    fillStart: Color,
    fillEnd: Color,
) {
    if (data.size < 2) return
    val step = width / (data.size - 1).coerceAtLeast(1)

    fun xAt(i: Int) = i * step
    fun yAt(v: Int) = padTop + height * (1f - v.toFloat() / maxVal)

    // Smooth cubic bezier path
    val linePath = Path()
    linePath.moveTo(xAt(0), yAt(data[0]))
    for (i in 1 until data.size) {
        val cpX = xAt(i - 1) + step / 2f
        linePath.cubicTo(cpX, yAt(data[i - 1]), cpX, yAt(data[i]), xAt(i), yAt(data[i]))
    }

    // Fill area beneath the line
    val fillPath = Path()
    fillPath.addPath(linePath)
    fillPath.lineTo(xAt(data.lastIndex), padTop + height)
    fillPath.lineTo(0f, padTop + height)
    fillPath.close()
    drawPath(
        path  = fillPath,
        brush = Brush.verticalGradient(
            colors    = listOf(fillStart, fillEnd),
            startY    = padTop,
            endY      = padTop + height,
        ),
    )

    // Draw the line on top
    drawPath(
        path  = linePath,
        color = lineColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Dot on the last point
    drawCircle(
        color  = lineColor,
        radius = 4.dp.toPx(),
        center = Offset(xAt(data.lastIndex), yAt(data.last())),
    )
    drawCircle(
        color  = Color.White,
        radius = 2.dp.toPx(),
        center = Offset(xAt(data.lastIndex), yAt(data.last())),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Returns 30 (totalQueries, blockedCount) pairs oldest-first. Missing days → (0,0). Kept for future use. */
@Suppress("unused")
private fun buildChartPoints(days: List<DailyStatsEntity>, count: Int): List<Pair<Int, Int>> {
    val byDate = days.associate { it.dateKey to (it.totalQueries to it.blockedCount) }
    val today  = java.time.LocalDate.now()
    return (count - 1 downTo 0).map { offset ->
        val key = today.minusDays(offset.toLong()).toString()
        byDate[key] ?: (0 to 0)
    }
}

// ── Stats overview card (DNS Queries left | Blocked + Block Rate right) ──────

@Composable
private fun StatsOverviewCard(totalQueries: Int, blockedCount: Int, blockRate: Float) {
    AnalyticsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Left column: DNS Queries ──────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier        = Modifier
                        .size(44.dp)
                        .background(IndigoLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint     = Indigo600,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    formatCount(totalQueries),
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "DNS\nQueries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterVertically),
            )

            // ── Right column: Blocked + Block Rate stacked ────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Blocked Ads & Trackers
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier        = Modifier
                                .size(28.dp)
                                .background(RedLight, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Block, contentDescription = null, tint = Red500, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            formatCount(blockedCount),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "Blocked Ads &\nTrackers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // Block Rate
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier        = Modifier
                                .size(28.dp)
                                .background(GreenLight, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Percent, contentDescription = null, tint = Green500, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            "${(blockRate * 100).roundToInt()}%",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        "Total Block\nPercentage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Top Queries card ──────────────────────────────────────────────────────────

@Composable
private fun TopQueriesCard(
    domains: List<DomainStatsEntity>,
    onViewAll: () -> Unit,
    blockedDomains: Set<String>,
    onBlockToggle: (String, Boolean) -> Unit,
) {
    val maxCount = domains.maxOfOrNull { it.queryCount }?.coerceAtLeast(1) ?: 1

    AnalyticsCard {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Top Queries",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "View All",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.clickable(onClick = onViewAll),
                )
            }
            domains.forEachIndexed { index, domain ->
                QueryRow(
                    rank = index + 1,
                    domain = domain,
                    maxCount = maxCount,
                    isInBlocklist = domain.domain.lowercase() in blockedDomains,
                    onBlockToggle = onBlockToggle,
                )
                if (index < domains.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QueryRow(
    rank: Int,
    domain: DomainStatsEntity,
    maxCount: Int,
    isInBlocklist: Boolean = false,
    onBlockToggle: ((domain: String, block: Boolean) -> Unit)? = null,
) {
    val fraction = (domain.queryCount.toFloat() / maxCount).coerceIn(0f, 1f)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Rank
        Text(
            "$rank",
            style     = MaterialTheme.typography.labelMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier  = Modifier.width(20.dp),
        )
        // Block icon (visible only when this domain was blocked)
        if (domain.blockedCount > 0) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = "Blocked",
                tint     = Red500,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(14.dp))
        }
        // Domain name
        Text(
            domain.domain,
            style     = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color     = MaterialTheme.colorScheme.onSurface,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier.weight(1f),
        )
        // Count
        Text(
            formatCount(domain.queryCount),
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier  = Modifier.width(44.dp),
        )
        // Bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Indigo600),
            )
        }
        // Block/Unblock menu
        if (onBlockToggle != null) {
            BlockToggleMenu(
                domain = domain.domain,
                isInBlocklist = isInBlocklist,
                onBlockToggle = onBlockToggle,
            )
        }
    }
}

// ── Top Blocked Queries card ──────────────────────────────────────────────────

@Composable
private fun TopBlockedQueriesCard(
    domains: List<DomainStatsEntity>,
    onViewAll: () -> Unit,
    blockedDomains: Set<String>,
    onBlockToggle: (String, Boolean) -> Unit,
) {
    val maxCount = domains.maxOfOrNull { it.blockedCount }?.coerceAtLeast(1) ?: 1

    AnalyticsCard {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Top Blocked Queries",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "View All",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.clickable(onClick = onViewAll),
                )
            }
            domains.forEachIndexed { index, domain ->
                BlockedQueryRow(
                    rank = index + 1,
                    domain = domain,
                    maxCount = maxCount,
                    isInBlocklist = domain.domain.lowercase() in blockedDomains,
                    onBlockToggle = onBlockToggle,
                )
                if (index < domains.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BlockedQueryRow(
    rank: Int,
    domain: DomainStatsEntity,
    maxCount: Int,
    isInBlocklist: Boolean = false,
    onBlockToggle: ((domain: String, block: Boolean) -> Unit)? = null,
) {
    val fraction = (domain.blockedCount.toFloat() / maxCount).coerceIn(0f, 1f)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$rank",
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(20.dp),
        )
        // Block icon — always shown since every entry here was blocked
        Icon(
            Icons.Outlined.Block,
            contentDescription = "Blocked",
            tint     = Red500,
            modifier = Modifier.size(14.dp),
        )
        Text(
            domain.domain,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.weight(1f),
        )
        Text(
            formatCount(domain.blockedCount),
            style      = MaterialTheme.typography.bodySmall,
            color      = Red500,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.width(44.dp),
        )
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Red500),
            )
        }
        // Block/Unblock menu
        if (onBlockToggle != null) {
            BlockToggleMenu(
                domain = domain.domain,
                isInBlocklist = isInBlocklist,
                onBlockToggle = onBlockToggle,
            )
        }
    }
}

// ── Top Blocked Apps card ─────────────────────────────────────────────────────

@Composable
private fun TopBlockedAppsCard(apps: List<AppEntry>, onViewAll: () -> Unit) {
    val maxCount = apps.maxOfOrNull { it.blockedCount }?.coerceAtLeast(1) ?: 1

    AnalyticsCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Top Blocked Apps",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "View All",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary,
                    modifier   = Modifier.clickable(onClick = onViewAll),
                )
            }
            apps.forEachIndexed { index, app ->
                AppRow(rank = index + 1, app = app, maxCount = maxCount)
                if (index < apps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppRow(rank: Int, app: AppEntry, maxCount: Int) {
    val fraction = (app.blockedCount.toFloat() / maxCount).coerceIn(0f, 1f)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$rank",
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.width(20.dp),
        )
        // App icon or initials fallback
        if (app.icon != null) {
            val bitmap = remember(app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier        = Modifier
                    .size(32.dp)
                    .background(IndigoLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1).uppercase(),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Indigo600,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.label,
                style     = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color     = MaterialTheme.colorScheme.onSurface,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
            Text(
                "${app.blockedCount} blocked",
                style = MaterialTheme.typography.labelSmall,
                color = Red500,
            )
        }
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Red500),
            )
        }
    }
}

// ── Shared card shell ─────────────────────────────────────────────────────────

@Composable
private fun AnalyticsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier        = Modifier
                .size(80.dp)
                .background(IndigoLight, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.BarChart,
                contentDescription = null,
                modifier           = Modifier.size(40.dp),
                tint               = Indigo600,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No data yet",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enable the VPN to start\ncollecting stats.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Block toggle menu (shared by QueryRow & BlockedQueryRow) ──────────────────

@Composable
internal fun BlockToggleMenu(
    domain: String,
    isInBlocklist: Boolean,
    onBlockToggle: (domain: String, block: Boolean) -> Unit,
    paywallEnabled: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showProDialog by rememberSaveable { mutableStateOf(false) }

    if (showProDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showProDialog = false },
            icon = {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    "Pro Feature",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text("Custom blocklist is a Pro feature. Upgrade to block or unblock individual domains.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showProDialog = false }) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    Box {
        IconButton(
            onClick  = {
                if (paywallEnabled) {
                    showProDialog = true
                } else {
                    expanded = true
                }
            },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (isInBlocklist) {
                DropdownMenuItem(
                    text = { Text("Unblock") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = Green500,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onBlockToggle(domain, false)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Block") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = null,
                            tint = Red500,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onBlockToggle(domain, true)
                    },
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000f)}M"
    n >= 1_000     -> "${"%.1f".format(n / 1_000f)}K"
    else           -> n.toString()
}

// ── Pro Feature Card ──────────────────────────────────────────────────────────

@Composable
private fun ProFeatureCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4F46E5).copy(alpha = 0.15f),
                                Color(0xFF7C3AED).copy(alpha = 0.15f),
                            )
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Indigo600,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = -90f },
            )
        }
    }
}
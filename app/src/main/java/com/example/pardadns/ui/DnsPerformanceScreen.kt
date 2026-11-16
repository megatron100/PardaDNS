package com.example.pardadns.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.ui.analytics.DnsPerformanceViewModel
import kotlinx.coroutines.delay

// ── Palette ───────────────────────────────────────────────────────────────────
private val PerfGreen500    = Color(0xFF22C55E)
private val PerfGreenLight  = Color(0xFFF0FDF4)
private val PerfYellow500   = Color(0xFFF59E0B)
private val PerfYellowLight = Color(0xFFFEFCE8)
private val PerfOrange500   = Color(0xFFF97316)
private val PerfOrangeLight = Color(0xFFFFF7ED)
private val PerfRed500      = Color(0xFFEF4444)
private val PerfRedLight    = Color(0xFFFEF2F2)
private val PerfBlue500     = Color(0xFF3B82F6)
private val PerfBlueLight   = Color(0xFFEFF6FF)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsPerformanceScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    vm: DnsPerformanceViewModel = hiltViewModel(),
) {
    val latencies by vm.latencies.collectAsStateWithLifecycle()
    val hasLoaded by vm.hasLoaded.collectAsStateWithLifecycle()

    // Poll every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            vm.poll()
            delay(2000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("DNS Performance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        if (!hasLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (latencies.isEmpty()) {
            EmptyPerformanceState(innerPadding = innerPadding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding()
                        + contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Summary card
                item {
                    PerformanceSummaryCard(latencies = latencies)
                }

                // Provider comparison header
                item {
                    Text(
                        "Provider Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                // Provider cards
                itemsIndexed(latencies, key = { _, l -> l.providerName }) { index, latency ->
                    ProviderLatencyCard(
                        rank = index + 1,
                        latency = latency,
                        maxAvgLatency = latencies.maxOfOrNull { it.avgLatencyMs }?.coerceAtLeast(1) ?: 1,
                    )
                }

                // Legend
                item {
                    LatencyLegend()
                }
            }
        }
    }
}

@Composable
private fun EmptyPerformanceState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Speed,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No performance data yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect to a DNS provider to start measuring response times",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun PerformanceSummaryCard(latencies: List<DnsLatencyEntity>) {
    val totalQueries = latencies.sumOf { it.totalQueries }
    val bestProvider = latencies.minByOrNull { it.avgLatencyMs }
    val worstProvider = latencies.maxByOrNull { it.avgLatencyMs }
    val overallAvg = if (totalQueries > 0) {
        latencies.sumOf { it.sumLatencyMs } / totalQueries
    } else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PerfBlueLight),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PerfBlue500.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = PerfBlue500,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Performance Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${perfFormatCount(totalQueries)} queries across ${latencies.size} providers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(
                    label = "Avg Response",
                    value = "${overallAvg}ms",
                    color = getLatencyColor(overallAvg.toInt()),
                )
                if (bestProvider != null) {
                    StatColumn(
                        label = "Fastest",
                        value = bestProvider.providerName,
                        subValue = "${bestProvider.avgLatencyMs}ms",
                        color = PerfGreen500,
                    )
                }
                if (worstProvider != null && latencies.size > 1) {
                    StatColumn(
                        label = "Slowest",
                        value = worstProvider.providerName,
                        subValue = "${worstProvider.avgLatencyMs}ms",
                        color = PerfRed500,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    subValue: String? = null,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        if (subValue != null) {
            Text(
                subValue,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ProviderLatencyCard(
    rank: Int,
    latency: DnsLatencyEntity,
    maxAvgLatency: Int,
) {
    val latencyColor = getLatencyColor(latency.avgLatencyMs)
    val progress = (latency.avgLatencyMs.toFloat() / maxAvgLatency).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> PerfGreen500.copy(alpha = 0.15f)
                            2 -> PerfYellow500.copy(alpha = 0.15f)
                            3 -> PerfOrange500.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        1 -> PerfGreen500
                        2 -> PerfYellow500
                        3 -> PerfOrange500
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    latency.providerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                
                // Latency bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        latencyColor.copy(alpha = 0.6f),
                                        latencyColor,
                                    )
                                )
                            ),
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${perfFormatCount(latency.totalQueries)} queries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "min: ${latency.minLatencyMs}ms • max: ${latency.maxLatencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Avg latency
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${latency.avgLatencyMs}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = latencyColor,
                )
                Text(
                    "ms avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LatencyLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Latency Guide",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                LegendItem(color = PerfGreen500, label = "< 50ms", description = "Excellent")
                LegendItem(color = PerfYellow500, label = "50-100ms", description = "Good")
                LegendItem(color = PerfOrange500, label = "100-200ms", description = "Fair")
                LegendItem(color = PerfRed500, label = "> 200ms", description = "Slow")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, description: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun getLatencyColor(latencyMs: Int): Color = when {
    latencyMs < 50 -> PerfGreen500
    latencyMs < 100 -> PerfYellow500
    latencyMs < 200 -> PerfOrange500
    else -> PerfRed500
}

private fun perfFormatCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000f)
    count >= 1_000     -> "%.1fK".format(count / 1_000f)
    else               -> count.toString()
}

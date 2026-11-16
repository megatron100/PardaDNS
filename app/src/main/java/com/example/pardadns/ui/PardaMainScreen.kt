package com.example.pardadns.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pardadns.R
import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.domain.model.VpnState
import com.example.pardadns.service.TrafficSnapshot
import com.example.pardadns.ui.components.CollapsibleShieldLayout
import com.example.pardadns.ui.components.ProviderCard
import com.example.pardadns.ui.components.ShieldHeader
import com.example.pardadns.ui.theme.StatusGreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PardaMainScreen(
    vpnState: VpnState,
    providers: List<DnsProvider>,
    selectedProvider: DnsProvider?,
    onSelectProvider: (DnsProvider) -> Unit,
    trafficStats: TrafficSnapshot = TrafficSnapshot.EMPTY,
    onAnalyticsClick: () -> Unit = {},
    latency: Map<String, Long?> = emptyMap(),
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRemoveProvider: ((String) -> Unit)? = null,
    pauseUnlocked: Boolean = false,
    onPause: (minutes: Int) -> Unit = {},
    onResume: () -> Unit = {},
    onProRequired: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val layoutDir = LocalLayoutDirection.current
    val sectionHorizontalPadding = (screenWidth * 0.04f).coerceIn(14.dp, 24.dp)
    val listItemGap = (screenWidth * 0.025f).coerceIn(8.dp, 14.dp)

    // Trigger latency measurement once when the screen enters composition.
    LaunchedEffect(Unit) { onRefresh() }

    // "Fastest" badge: lowest ping among listed providers, once at least two have answered.
    val fastestId = remember(providers, latency) {
        providers.mapNotNull { p -> latency[p.id]?.let { p.id to it } }
            .takeIf { it.size >= 2 }
            ?.minByOrNull { it.second }
            ?.first
    }

    val onShieldTap: () -> Unit = {
        val target = selectedProvider ?: providers.firstOrNull()
        target?.let(onSelectProvider)
    }

    // Header heights
    val expandedHeaderHeight = 270.dp
    val collapsedHeaderHeight = 56.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDir),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDir),
            ),
    ) {
        // Pull-to-refresh wraps the collapsing layout so it only receives scroll
        // left over after the list is at the top and the header is fully expanded.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            CollapsibleShieldLayout(
                expandedHeight = expandedHeaderHeight,
                collapsedHeight = collapsedHeaderHeight,
                modifier = Modifier.fillMaxSize(),
                header = { collapseProgress ->
                    ShieldHeader(
                        vpnState = vpnState,
                        trafficStats = trafficStats,
                        onTap = onShieldTap,
                        collapseProgress = collapseProgress,
                        pauseUnlocked = pauseUnlocked,
                        onPause = onPause,
                        onResume = onResume,
                        onProRequired = onProRequired,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = sectionHorizontalPadding,
                                vertical = 4.dp,
                            ),
                    )
                },
                content = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = sectionHorizontalPadding,
                            top = 8.dp,
                            end = sectionHorizontalPadding,
                            bottom = contentPadding.calculateBottomPadding() + sectionHorizontalPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(listItemGap),
                    ) {
                        // ── Analytics card (always visible) ───────────────────
                        item(key = "analytics") {
                            AnalyticsCard(onClick = onAnalyticsClick)
                        }

                        // ── Section label ─────────────────────────────────────
                        item(key = "label") {
                            Text(
                                text = stringResource(R.string.label_select_provider),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }

                        // ── Provider cards with item animation ────────────────
                        items(providers, key = { it.id }) { provider ->
                            ProviderCard(
                                provider = provider,
                                isSelected = provider.id == selectedProvider?.id,
                                onSelect = { onSelectProvider(provider) },
                                latencyMs = latency[provider.id],
                                isFastest = provider.id == fastestId,
                                onRemove = onRemoveProvider?.let { callback -> { callback(provider.id) } },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                },
            )
        }
    }
}

// ── Analytics quick-access card (lives in the list, not the header) ────────────

@Composable
private fun AnalyticsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatusGreen.copy(alpha = 0.08f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = StatusGreen,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Detailed Analytics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = StatusGreen,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = StatusGreen.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

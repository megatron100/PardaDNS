package com.example.pardadns.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.ui.analytics.AllQueriesViewModel
import com.example.pardadns.ui.analytics.AppEntry
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class AllQueriesType { QUERIES, BLOCKED_QUERIES, BLOCKED_APPS }

private enum class AppSortType { QUERIES, BLOCKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllQueriesScreen(
    type: AllQueriesType,
    onBack: () -> Unit,
    blockedDomains: Set<String> = emptySet(),
    onBlockToggle: (domain: String, block: Boolean) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    vm: AllQueriesViewModel = hiltViewModel(),
) {
    when (type) {
        AllQueriesType.QUERIES, AllQueriesType.BLOCKED_QUERIES -> {
            TabbedDomainsScreen(
                initialTab = if (type == AllQueriesType.BLOCKED_QUERIES) 1 else 0,
                onBack = onBack,
                blockedDomains = blockedDomains,
                onBlockToggle = onBlockToggle,
                contentPadding = contentPadding,
                vm = vm,
            )
        }
        AllQueriesType.BLOCKED_APPS -> {
            TabbedAppsScreen(
                onBack = onBack,
                contentPadding = contentPadding,
                vm = vm,
            )
        }
    }
}

// ── Tabbed domains (All Queries + Blocked Queries) ────────────────────────────

private val domainTabs = listOf("All Queries", "Blocked Queries")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabbedDomainsScreen(
    initialTab: Int,
    onBack: () -> Unit,
    blockedDomains: Set<String>,
    onBlockToggle: (String, Boolean) -> Unit,
    contentPadding: PaddingValues,
    vm: AllQueriesViewModel,
) {
    val pagerState = rememberPagerState(initialPage = initialTab) { domainTabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Domain Queries", fontWeight = FontWeight.Bold) },
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
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    domainTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    val domains by vm.allDomains.collectAsStateWithLifecycle()
                    AllDomainsList(
                        domains = domains,
                        innerPadding = innerPadding,
                        contentPadding = contentPadding,
                        label = "domains",
                        blockedDomains = blockedDomains,
                        onBlockToggle = onBlockToggle,
                        renderRow = { index, domain, maxCount ->
                            QueryRow(
                                rank = index + 1,
                                domain = domain,
                                maxCount = maxCount,
                                isInBlocklist = domain.domain.lowercase() in blockedDomains,
                                onBlockToggle = onBlockToggle,
                            )
                        },
                        countSelector = { it.queryCount },
                    )
                }
                1 -> {
                    val domains by vm.allBlockedDomains.collectAsStateWithLifecycle()
                    AllDomainsList(
                        domains = domains,
                        innerPadding = innerPadding,
                        contentPadding = contentPadding,
                        label = "blocked domains",
                        blockedDomains = blockedDomains,
                        onBlockToggle = onBlockToggle,
                        renderRow = { index, domain, maxCount ->
                            BlockedQueryRow(
                                rank = index + 1,
                                domain = domain,
                                maxCount = maxCount,
                                isInBlocklist = domain.domain.lowercase() in blockedDomains,
                                onBlockToggle = onBlockToggle,
                            )
                        },
                        countSelector = { it.blockedCount },
                    )
                }
            }
        }
    }
}

// ── Tabbed Apps (All Apps + Blocked Apps) ─────────────────────────────────────

private val appTabs = listOf("All Apps", "Blocked Apps")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabbedAppsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    vm: AllQueriesViewModel,
) {
    // Default to "Blocked Apps" tab since users come from TopBlockedAppsCard
    val pagerState = rememberPagerState(initialPage = 1) { appTabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("App Statistics", fontWeight = FontWeight.Bold) },
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
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    appTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    val apps by vm.allApps.collectAsStateWithLifecycle()
                    AllAppsList(
                        apps = apps,
                        innerPadding = innerPadding,
                        contentPadding = contentPadding,
                        label = "apps",
                        sortBy = AppSortType.QUERIES,
                    )
                }
                1 -> {
                    val apps by vm.allBlockedApps.collectAsStateWithLifecycle()
                    AllAppsList(
                        apps = apps,
                        innerPadding = innerPadding,
                        contentPadding = contentPadding,
                        label = "blocked apps",
                        sortBy = AppSortType.BLOCKED,
                    )
                }
            }
        }
    }
}

// ── Shared list composables ───────────────────────────────────────────────────

@Composable
private fun AllDomainsList(
    domains: List<DomainStatsEntity>,
    innerPadding: PaddingValues,
    contentPadding: PaddingValues,
    label: String,
    blockedDomains: Set<String>,
    onBlockToggle: (String, Boolean) -> Unit,
    renderRow: @Composable (index: Int, domain: DomainStatsEntity, maxCount: Int) -> Unit,
    countSelector: (DomainStatsEntity) -> Int,
) {
    val maxCount = domains.maxOfOrNull { countSelector(it) }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding()
                + contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Text(
                "${formatCount(domains.size)} $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(domains, key = { _, d -> d.domain }) { index, domain ->
            renderRow(index, domain, maxCount)
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

@Composable
private fun AllAppsList(
    apps: List<AppEntry>,
    innerPadding: PaddingValues,
    contentPadding: PaddingValues,
    label: String,
    sortBy: AppSortType,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding()
                + contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item {
            Text(
                "${formatCount(apps.size)} $label",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        // Column headers - different for each tab
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(20.dp))   // rank
                Spacer(modifier = Modifier.width(10.dp))
                Spacer(modifier = Modifier.size(32.dp))     // avatar
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "App",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when (sortBy) {
                    AppSortType.QUERIES -> {
                        // All Apps tab: only show Queries
                        Text(
                            "Queries",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Indigo600,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                    AppSortType.BLOCKED -> {
                        // Blocked Apps tab: show Queries, Blocked, %
                        Text(
                            "Queries",
                            style = MaterialTheme.typography.labelSmall,
                            color = Indigo600,
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            "Blocked",
                            style = MaterialTheme.typography.labelSmall,
                            color = Red500,
                            modifier = Modifier.width(50.dp),
                        )
                        Text(
                            "%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Red500,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        itemsIndexed(apps, key = { _, a -> a.packageName }) { index, app ->
            AllAppsRow(rank = index + 1, app = app, sortBy = sortBy)
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

@Composable
private fun AllAppsRow(rank: Int, app: AppEntry, sortBy: AppSortType) {
    val percentage = if (app.queryCount > 0) {
        (app.blockedCount.toFloat() / app.queryCount * 100).roundToInt()
    } else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Rank
        Text(
            "$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
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
                modifier = Modifier
                    .size(32.dp)
                    .background(IndigoLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Indigo600,
                )
            }
        }
        // App name
        Text(
            app.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when (sortBy) {
            AppSortType.QUERIES -> {
                // All Apps tab: only show Queries
                Text(
                    formatCount(app.queryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Indigo600,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp),
                )
            }
            AppSortType.BLOCKED -> {
                // Blocked Apps tab: show Queries, Blocked, %
                Text(
                    formatCount(app.queryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Indigo600,
                    modifier = Modifier.width(50.dp),
                )
                Text(
                    formatCount(app.blockedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Red500,
                    modifier = Modifier.width(50.dp),
                )
                Text(
                    "$percentage%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Red500,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

package com.example.pardadns.ui

import android.content.Context
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.db.entity.QueryLogEntity
import com.example.pardadns.ui.analytics.QueryLogViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Palette ───────────────────────────────────────────────────────────────────
private val LogGreen500 = Color(0xFF22C55E)
private val LogRed500 = Color(0xFFEF4444)
private val LogBlue500 = Color(0xFF3B82F6)

enum class QueryFilter { ALL, BLOCKED, ALLOWED }

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryLogScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    vm: QueryLogViewModel = hiltViewModel(),
) {
    val queries by vm.queries.collectAsStateWithLifecycle()
    val hasLoaded by vm.hasLoaded.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val appIcons = remember { mutableMapOf<String, Drawable?>() }

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
                title = { Text("DNS Query Log", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Search bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { vm.setSearchQuery(it) },
                onSearch = { keyboardController?.hide() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Filter chips
            FilterChipRow(
                selectedFilter = filter,
                onFilterChange = { vm.setFilter(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Results count
            Text(
                "${queries.size} ${if (searchQuery.isNotBlank()) "results" else "queries"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            if (!hasLoaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (queries.isEmpty()) {
                EmptyQueryLogState(hasSearch = searchQuery.isNotBlank() || filter != QueryFilter.ALL)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(queries, key = { _, q -> q.id }) { _, query ->
                        QueryLogItem(
                            query = query,
                            appIcon = query.packageName?.let { pkg ->
                                appIcons.getOrPut(pkg) { getAppIcon(context, pkg) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search domains...") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LogBlue500,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun FilterChipRow(
    selectedFilter: QueryFilter,
    onFilterChange: (QueryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QueryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            QueryFilter.ALL -> "All"
                            QueryFilter.BLOCKED -> "Blocked"
                            QueryFilter.ALLOWED -> "Allowed"
                        }
                    )
                },
                leadingIcon = if (selectedFilter == filter) {
                    {
                        Icon(
                            when (filter) {
                                QueryFilter.ALL -> Icons.Filled.FilterList
                                QueryFilter.BLOCKED -> Icons.Filled.Block
                                QueryFilter.ALLOWED -> Icons.Outlined.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (filter) {
                        QueryFilter.ALL -> LogBlue500.copy(alpha = 0.15f)
                        QueryFilter.BLOCKED -> LogRed500.copy(alpha = 0.15f)
                        QueryFilter.ALLOWED -> LogGreen500.copy(alpha = 0.15f)
                    },
                    selectedLabelColor = when (filter) {
                        QueryFilter.ALL -> LogBlue500
                        QueryFilter.BLOCKED -> LogRed500
                        QueryFilter.ALLOWED -> LogGreen500
                    },
                    selectedLeadingIconColor = when (filter) {
                        QueryFilter.ALL -> LogBlue500
                        QueryFilter.BLOCKED -> LogRed500
                        QueryFilter.ALLOWED -> LogGreen500
                    },
                ),
            )
        }
    }
}

@Composable
private fun EmptyQueryLogState(hasSearch: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (hasSearch) Icons.Filled.Search else Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasSearch) "No matching queries" else "No queries logged yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasSearch) "Try adjusting your search or filter"
                else "DNS queries will appear here as you browse",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun QueryLogItem(
    query: QueryLogEntity,
    appIcon: Drawable?,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }
    val isToday = remember(query.timestamp) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val queryDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(query.timestamp))
        today == queryDay
    }

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (query.blocked) LogRed500.copy(alpha = 0.1f)
                        else LogGreen500.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.toBitmap(36, 36).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        if (query.blocked) Icons.Filled.Block else Icons.Outlined.CheckCircle,
                        contentDescription = if (query.blocked) "Blocked" else "Allowed",
                        tint = if (query.blocked) LogRed500 else LogGreen500,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    query.domain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Status badge
                    Text(
                        if (query.blocked) "BLOCKED" else "ALLOWED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (query.blocked) LogRed500 else LogGreen500,
                    )
                    
                    // Latency
                    if (query.responseTimeMs > 0) {
                        Text(
                            "${query.responseTimeMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    
                    // Provider
                    query.providerName?.let { provider ->
                        Text(
                            "• $provider",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Timestamp
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    timeFormat.format(Date(query.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isToday) {
                    Text(
                        dateFormat.format(Date(query.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private fun getAppIcon(context: Context, packageName: String): Drawable? {
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }
}

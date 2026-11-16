package com.example.pardadns.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pardadns.data.filter.FilterCategory
import com.example.pardadns.data.repository.FilterList
import com.example.pardadns.data.repository.FilterListRepository
import com.example.pardadns.ui.components.ProUpsellBanner
import com.example.pardadns.ui.theme.StatusGreen

private data class CategoryStyle(val icon: ImageVector, val bg: Color, val tint: Color)

private fun styleFor(category: FilterCategory) = when (category) {
    FilterCategory.ADS     -> CategoryStyle(Icons.Default.Block, Color(0xFFFFEBEE), Color(0xFFC62828))
    FilterCategory.MALWARE -> CategoryStyle(Icons.Default.GppBad, Color(0xFFFFF3E0), Color(0xFFE65100))
    FilterCategory.ADULT   -> CategoryStyle(Icons.Default.NoAdultContent, Color(0xFFF3E5F5), Color(0xFF7B1FA2))
    FilterCategory.CUSTOM  -> CategoryStyle(Icons.Default.CloudDownload, Color(0xFFE3F2FD), Color(0xFF1565C0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(
    viewModel: FiltersViewModel,
    onBack: () -> Unit,
    onOpenCustomDomains: () -> Unit,
    onProRequired: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val customCount by viewModel.customDomainCount.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FilterList?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFromFile)
    }

    // Free users can look around; every action routes to the paywall.
    fun guarded(action: () -> Unit) { if (isPro) action() else onProRequired() }

    if (showUrlDialog) {
        ImportUrlDialog(
            onDismiss = { showUrlDialog = false },
            onImport = { name, url ->
                showUrlDialog = false
                viewModel.importFromUrl(name, url)
            },
        )
    }
    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${list.name}?") },
            text = { Text("Its ${formatEntries(list.entryCount)} domains will no longer be blocked.") },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(list.id); pendingDelete = null }) {
                    Text("Remove", color = Red500)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    val refreshingAll = FilterListRepository.REFRESH_ALL in state.busyIds
    val canRefresh = state.lists.any { it.enabled && it.url != null }
    // Filters don't apply without Pro, so don't show them as on.
    val shownLists = remember(state.lists, isPro) {
        if (isPro) state.lists else state.lists.map { it.copy(enabled = false) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Blocklists & Filters", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isPro && canRefresh) {
                        if (refreshingAll) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = viewModel::refreshAll) {
                                Icon(Icons.Default.Refresh, contentDescription = "Update all lists")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                if (isPro) {
                    SummaryCard(activeEntries = state.activeEntries, activeLists = state.lists.count { it.enabled })
                } else {
                    ProUpsellBanner(
                        title = "Unlock smart filtering",
                        subtitle = "Block ads, malware & adult sites with ready-made lists",
                        onClick = onProRequired,
                    )
                }
            }

            item(key = "categories-label") {
                Spacer(Modifier.height(4.dp))
                SectionLabel("CATEGORIES")
            }
            for (category in listOf(FilterCategory.ADS, FilterCategory.MALWARE, FilterCategory.ADULT)) {
                item(key = category.name) {
                    CategoryCard(
                        category = category,
                        lists = shownLists.filter { it.category == category },
                        busyIds = state.busyIds,
                        enabled = state.loaded,
                        onCategoryToggle = { on -> guarded { viewModel.setCategoryEnabled(category, on) } },
                        onListToggle = { id, on -> guarded { viewModel.setListEnabled(id, on) } },
                    )
                }
            }

            item(key = "imports-label") {
                Spacer(Modifier.height(4.dp))
                SectionLabel("IMPORTED LISTS")
            }
            item(key = "imports") {
                ImportsCard(
                    lists = shownLists.filter { it.category == FilterCategory.CUSTOM },
                    busyIds = state.busyIds,
                    onToggle = { id, on -> guarded { viewModel.setListEnabled(id, on) } },
                    onRefresh = { id -> guarded { viewModel.refresh(id) } },
                    onDelete = { list -> pendingDelete = list },
                    onImportUrl = { guarded { showUrlDialog = true } },
                    onImportFile = { guarded { filePicker.launch(arrayOf("text/*", "application/octet-stream")) } },
                )
            }

            item(key = "custom-label") {
                Spacer(Modifier.height(4.dp))
                SectionLabel("YOUR RULES")
            }
            item(key = "custom") {
                SectionCard {
                    NavigationRow(
                        icon = Icons.Default.Edit,
                        iconBg = Color(0xFFFFEBEE),
                        iconTint = Color(0xFFC62828),
                        title = "Custom domains",
                        subtitle = if (customCount == 0) "Block individual domains by hand"
                        else "$customCount domain${if (customCount != 1) "s" else ""} blocked by you",
                        onClick = onOpenCustomDomains,
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = "Lists are matched on your device — no queries leave your phone for filtering. " +
                        "Subdomains of a listed domain are blocked too. Enabled lists refresh weekly when you open this page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun SummaryCard(activeEntries: Int, activeLists: Int) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = Icons.Default.Shield, iconBg = Color(0xFFE8F5E9), iconTint = StatusGreen)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (activeEntries > 0) "${formatEntries(activeEntries)} domains blocked" else "No filters active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (activeLists > 0) "From $activeLists active list${if (activeLists != 1) "s" else ""}"
                    else "Turn on a category below to start blocking",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: FilterCategory,
    lists: List<FilterList>,
    busyIds: Set<String>,
    enabled: Boolean,
    onCategoryToggle: (Boolean) -> Unit,
    onListToggle: (String, Boolean) -> Unit,
) {
    val style = styleFor(category)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = style.icon, iconBg = style.bg, iconTint = style.tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BusyOrSwitch(
                busy = category.name in busyIds,
                checked = lists.any { it.enabled },
                enabled = enabled,
                onCheckedChange = onCategoryToggle,
            )
        }
        for (list in lists) {
            RowDivider()
            FilterListRow(
                list = list,
                busy = list.id in busyIds,
                enabled = enabled,
                onToggle = { onListToggle(list.id, it) },
            )
        }
    }
}

@Composable
private fun FilterListRow(
    list: FilterList,
    busy: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    trailingActions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (list.recommended) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFB300),
                    )
                }
            }
            Text(
                text = list.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listMeta(list),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
        trailingActions()
        Spacer(Modifier.width(4.dp))
        BusyOrSwitch(busy = busy, checked = list.enabled, enabled = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ImportsCard(
    lists: List<FilterList>,
    busyIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onRefresh: (String) -> Unit,
    onDelete: (FilterList) -> Unit,
    onImportUrl: () -> Unit,
    onImportFile: () -> Unit,
) {
    val style = styleFor(FilterCategory.CUSTOM)
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon = style.icon, iconBg = style.bg, iconTint = style.tint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Import a blocklist",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Hosts files, domain lists or AdBlock-style rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onImportUrl, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("From URL", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("From file", fontWeight = FontWeight.SemiBold)
            }
        }
        // A new import is busy before its row exists.
        val importing = busyIds.any { id ->
            (id.startsWith("url-") || id.startsWith("file-")) && lists.none { it.id == id }
        }
        if (importing) {
            RowDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Downloading and compiling…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (list in lists) {
            RowDivider()
            FilterListRow(
                list = list,
                busy = list.id in busyIds,
                enabled = true,
                onToggle = { onToggle(list.id, it) },
                trailingActions = {
                    if (list.url != null) {
                        IconButton(onClick = { onRefresh(list.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Update",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(list) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BusyOrSwitch(busy: Boolean, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (busy) {
        CircularProgressIndicator(Modifier.padding(horizontal = 14.dp).size(22.dp), strokeWidth = 2.dp)
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = StatusGreen,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun ImportUrlDialog(onDismiss: () -> Unit, onImport: (name: String, url: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val valid = url.trim().let { (it.startsWith("https://") || it.startsWith("http://")) && it.length > 10 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from URL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("List URL") },
                    placeholder = { Text("https://example.com/hosts.txt", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(12.dp),
                    isError = url.isNotBlank() && !valid,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(name, url) }, enabled = valid) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun listMeta(list: FilterList): String = when {
    list.entryCount > 0 -> "${formatEntries(list.entryCount)} domains · Updated ${formatAgo(list.updatedAt)}"
    list.approxEntries > 0 -> "~${formatEntries(list.approxEntries)} domains · Downloads when enabled"
    else -> "Not downloaded"
}

private fun formatEntries(n: Int): String = when {
    n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000f)}M"
    n >= 10_000    -> "${n / 1_000}K"
    n >= 1_000     -> "${"%.1f".format(n / 1_000f)}K"
    else           -> n.toString()
}

private fun formatAgo(epochMillis: Long): String {
    if (epochMillis <= 0L) return "never"
    val minutes = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        minutes < 1      -> "just now"
        minutes < 60     -> "${minutes}m ago"
        minutes < 1_440  -> "${minutes / 60}h ago"
        else             -> "${minutes / 1_440}d ago"
    }
}

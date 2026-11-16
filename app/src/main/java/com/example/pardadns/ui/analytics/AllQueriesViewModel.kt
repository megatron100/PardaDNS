package com.example.pardadns.ui.analytics

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.db.entity.AppQueryStatsEntity
import com.example.pardadns.data.db.entity.DomainStatsEntity
import com.example.pardadns.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllQueriesViewModel @Inject constructor(
    repo: StatsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val appIcons = MutableStateFlow<Map<String, Drawable>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val (labels, icons) = buildAppInfoMaps(context)
            appLabels.value = labels
            appIcons.value = icons
        }
    }

    val allDomains: StateFlow<List<DomainStatsEntity>> = repo.observeAllDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBlockedDomains: StateFlow<List<DomainStatsEntity>> = repo.observeAllBlockedDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allBlockedApps: StateFlow<List<AppEntry>> = combine(
        repo.observeAllBlockedApps(),
        appLabels,
        appIcons,
    ) { list, labels, icons ->
        list.map { it.toEntry(labels, icons) }
            .sortedByDescending { if (it.queryCount > 0) it.blockedCount.toFloat() / it.queryCount else 0f }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allApps: StateFlow<List<AppEntry>> = combine(
        repo.observeAllApps(),
        appLabels,
        appIcons,
    ) { list, labels, icons -> list.map { it.toEntry(labels, icons) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

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

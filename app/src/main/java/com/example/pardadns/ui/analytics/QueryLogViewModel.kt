package com.example.pardadns.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.db.entity.QueryLogEntity
import com.example.pardadns.data.repository.StatsRepository
import com.example.pardadns.ui.QueryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QueryLogViewModel @Inject constructor(
    private val repo: StatsRepository,
) : ViewModel() {

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _queries = MutableStateFlow<List<QueryLogEntity>>(emptyList())
    val queries: StateFlow<List<QueryLogEntity>> = _queries.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(QueryFilter.ALL)
    val filter: StateFlow<QueryFilter> = _filter.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { doLoad() }
    }

    fun setFilter(filter: QueryFilter) {
        _filter.value = filter
        viewModelScope.launch { doLoad() }
    }

    /**
     * Called from the screen's LaunchedEffect every 2 seconds.
     */
    suspend fun poll() = doLoad()

    private suspend fun doLoad() = withContext(Dispatchers.IO) {
        val search = _searchQuery.value.takeIf { it.isNotBlank() }
        val blocked = when (_filter.value) {
            QueryFilter.ALL -> null
            QueryFilter.BLOCKED -> true
            QueryFilter.ALLOWED -> false
        }

        val data = repo.searchAndFilterQueries(
            search = search,
            blocked = blocked,
            packageName = null,
            limit = 500,
        ).first()

        _queries.value = data
        _hasLoaded.value = true
    }
}

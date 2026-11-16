package com.example.pardadns.ui.analytics

import androidx.lifecycle.ViewModel
import com.example.pardadns.data.db.entity.DnsLatencyEntity
import com.example.pardadns.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DnsPerformanceViewModel @Inject constructor(
    private val repo: StatsRepository,
) : ViewModel() {

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _latencies = MutableStateFlow<List<DnsLatencyEntity>>(emptyList())
    val latencies: StateFlow<List<DnsLatencyEntity>> = _latencies.asStateFlow()

    /**
     * Called from the screen's LaunchedEffect every 2 seconds.
     */
    suspend fun poll() = withContext(Dispatchers.IO) {
        val data = repo.observeAllLatencies().first()
        // Sort by average latency (fastest first)
        _latencies.value = data.sortedBy { it.avgLatencyMs }
        _hasLoaded.value = true
    }
}

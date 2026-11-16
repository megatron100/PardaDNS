package com.example.pardadns.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pardadns.data.filter.FilterCategory
import com.example.pardadns.data.repository.BlocklistRepository
import com.example.pardadns.data.repository.FilterListRepository
import com.example.pardadns.data.repository.FilterState
import com.example.pardadns.data.repository.ProRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val filters: FilterListRepository,
    proRepository: ProRepository,
    blocklistRepository: BlocklistRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val state: StateFlow<FilterState> = filters.state
    val isPro: StateFlow<Boolean> = proRepository.isPro
    val messages: SharedFlow<String> = filters.messages

    val customDomainCount: StateFlow<Int> = blocklistRepository.domains
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // Keep enabled lists fresh without any background job: refresh on visit if > 7 days old.
        if (proRepository.isPro.value) filters.refreshStale()
    }

    fun setListEnabled(id: String, enabled: Boolean) = filters.setEnabled(id, enabled)
    fun setCategoryEnabled(category: FilterCategory, enabled: Boolean) = filters.setCategoryEnabled(category, enabled)
    fun refresh(id: String) = filters.refresh(id)
    fun refreshAll() = filters.refreshAll()
    fun remove(id: String) = filters.remove(id)

    fun importFromUrl(name: String, url: String) {
        val trimmed = url.trim()
        val fallbackName = runCatching { Uri.parse(trimmed).host }.getOrNull().orEmpty()
        filters.importFromUrl(name.trim().ifEmpty { fallbackName }, trimmed)
    }

    fun importFromFile(uri: Uri) {
        val name = displayName(uri)?.substringBeforeLast('.') ?: "Imported file"
        filters.importFromStream(name) { context.contentResolver.openInputStream(uri) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

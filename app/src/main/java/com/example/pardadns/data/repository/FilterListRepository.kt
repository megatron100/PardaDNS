package com.example.pardadns.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pardadns.data.filter.BlocklistParser
import com.example.pardadns.data.filter.FilterCatalog
import com.example.pardadns.data.filter.FilterCategory
import com.example.pardadns.data.filter.HashIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A blocklist as shown in the UI — either a curated list or a user import. */
data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    val category: FilterCategory,
    /** Source URL; null for lists imported from a file (they can't be refreshed). */
    val url: String?,
    val curated: Boolean,
    val recommended: Boolean,
    val approxEntries: Int,
    val enabled: Boolean,
    /** Unique domains in the compiled list; 0 until it has been downloaded. */
    val entryCount: Int,
    /** Epoch millis of the last successful download; 0 = never. */
    val updatedAt: Long,
)

data class FilterState(
    val lists: List<FilterList> = emptyList(),
    /** Unique domains across all enabled lists. */
    val activeEntries: Int = 0,
    /** Lists currently downloading or compiling. */
    val busyIds: Set<String> = emptySet(),
    val loaded: Boolean = false,
)

/**
 * Downloads, compiles and merges blocklists into one memory-mapped [HashIndex] that the
 * VPN loop queries via [isBlocked]. All work runs on a background scope that outlives
 * the screen, serialised by a mutex so concurrent taps can't corrupt the index.
 */
@Singleton
class FilterListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "filters")
    private val activeFile = File(dir, "active.bin")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Hot-path index — swapped atomically after each rebuild. */
    @Volatile private var index: HashIndex = HashIndex.EMPTY

    private val _state = MutableStateFlow(FilterState())
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-shot user-facing results (errors, import summaries). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    init {
        scope.launch {
            mutex.withLock {
                val lists = loadLists()
                val enabled = lists.filter { it.enabled && it.entryCount > 0 }
                index = when {
                    enabled.size == 1 -> HashIndex.open(fileFor(enabled[0].id))
                    enabled.isNotEmpty() -> HashIndex.open(activeFile)
                    else -> HashIndex.EMPTY
                }
                _state.value = FilterState(lists = lists, activeEntries = index.size, loaded = true)
            }
        }
    }

    /** O(log n) per label — called on every DNS query while Pro filters are active. */
    fun isBlocked(domain: String): Boolean = index.containsDomainOrParent(domain)

    // ── User actions ──────────────────────────────────────────────────────────

    fun setEnabled(id: String, enabled: Boolean) = launchLocked(id) {
        val list = find(id) ?: return@launchLocked
        if (enabled && list.entryCount == 0) {
            if (!downloadInto(list)) return@launchLocked
        }
        updateList(id) { it.copy(enabled = enabled) }
        rebuildIndex()
    }

    /** Category switch: on = enable its recommended lists, off = disable every list in it. */
    fun setCategoryEnabled(category: FilterCategory, enabled: Boolean) = launchLocked(category.name) {
        val inCategory = _state.value.lists.filter { it.category == category }
        val targets = if (enabled) {
            inCategory.filter { it.recommended }.ifEmpty { inCategory.take(1) }
        } else inCategory
        for (list in targets) {
            if (enabled && list.entryCount == 0 && !downloadInto(list)) continue
            updateList(list.id) { it.copy(enabled = enabled) }
        }
        rebuildIndex()
    }

    fun refresh(id: String) = launchLocked(id) {
        val list = find(id) ?: return@launchLocked
        if (list.url == null) return@launchLocked
        if (downloadInto(list)) rebuildIndex()
    }

    fun refreshAll() = launchLocked(REFRESH_ALL) {
        var changed = false
        for (list in _state.value.lists) {
            if (list.enabled && list.url != null) changed = downloadInto(list) || changed
        }
        if (changed) rebuildIndex()
        _messages.tryEmit("Filters updated")
    }

    /** Quietly refreshes enabled URL lists older than [STALE_AFTER_MS]. */
    fun refreshStale() = launchLocked(REFRESH_ALL) {
        val now = System.currentTimeMillis()
        var changed = false
        for (list in _state.value.lists) {
            if (list.enabled && list.url != null && now - list.updatedAt > STALE_AFTER_MS) {
                changed = downloadInto(list, quiet = true) || changed
            }
        }
        if (changed) rebuildIndex()
    }

    fun importFromUrl(name: String, url: String) {
        val id = "url-" + Integer.toHexString(url.hashCode())
        launchLocked(id) {
            if (find(id) != null) {
                _messages.tryEmit("This list is already imported")
                return@launchLocked
            }
            val list = newImport(id, name, url)
            val count = compile(id) { openUrl(url) } ?: return@launchLocked
            addImport(list.copy(entryCount = count, updatedAt = System.currentTimeMillis()))
        }
    }

    fun importFromStream(name: String, open: () -> InputStream?) {
        val id = "file-" + System.currentTimeMillis().toString(36)
        launchLocked(id) {
            val count = compile(id) { open() ?: throw IOException("Could not open file") } ?: return@launchLocked
            addImport(newImport(id, name, url = null).copy(entryCount = count, updatedAt = System.currentTimeMillis()))
        }
    }

    fun remove(id: String) = launchLocked(id) {
        val list = find(id) ?: return@launchLocked
        if (list.curated) return@launchLocked
        fileFor(id).delete()
        _state.update { s -> s.copy(lists = s.lists.filterNot { it.id == id }) }
        persist()
        rebuildIndex()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun launchLocked(busyKey: String, block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(busyIds = it.busyIds + busyKey) }
            try {
                mutex.withLock { block() }
            } finally {
                _state.update { it.copy(busyIds = it.busyIds - busyKey) }
            }
        }
    }

    private fun find(id: String) = _state.value.lists.firstOrNull { it.id == id }

    private fun fileFor(id: String) = File(dir, "$id.bin")

    private fun newImport(id: String, name: String, url: String?) = FilterList(
        id = id,
        name = name.ifBlank { "Imported list" },
        description = url?.substringAfter("://")?.take(60) ?: "Imported from file",
        category = FilterCategory.CUSTOM,
        url = url,
        curated = false,
        recommended = false,
        approxEntries = 0,
        enabled = true,
        entryCount = 0,
        updatedAt = 0,
    )

    private fun addImport(list: FilterList) {
        _state.update { s -> s.copy(lists = s.lists + list) }
        persist()
        rebuildIndex()
        _messages.tryEmit("Imported ${list.entryCount} domains from ${list.name}")
    }

    /** Downloads [list] into its compiled file. Returns true on success. */
    private fun downloadInto(list: FilterList, quiet: Boolean = false): Boolean {
        val url = list.url ?: return false
        val count = compile(list.id, quiet) { openUrl(url) } ?: return false
        updateList(list.id) { it.copy(entryCount = count, updatedAt = System.currentTimeMillis()) }
        return true
    }

    /** Compiles a source into the list's file. Returns the entry count, or null on failure. */
    private fun compile(id: String, quiet: Boolean = false, open: () -> InputStream): Int? = try {
        val result = open().use { BlocklistParser.compile(it, fileFor(id)) }
        when {
            result.entries == 0 -> {
                fileFor(id).delete()
                if (!quiet) _messages.tryEmit("No domains found in this list")
                null
            }
            else -> {
                if (result.truncated && !quiet) {
                    _messages.tryEmit("List is very large — kept the first ${BlocklistParser.MAX_ENTRIES} domains")
                }
                result.entries
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to compile list $id", e)
        if (!quiet) _messages.tryEmit("Download failed: ${e.message ?: "network error"}")
        null
    }

    private fun openUrl(url: String): InputStream {
        val response = client.newCall(
            Request.Builder().url(url).header("User-Agent", "PandaDNS/1.0").build(),
        ).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }
        return response.body?.byteStream() ?: run { response.close(); throw IOException("Empty response") }
    }

    private fun updateList(id: String, transform: (FilterList) -> FilterList) {
        _state.update { s -> s.copy(lists = s.lists.map { if (it.id == id) transform(it) else it }) }
        persist()
    }

    private fun rebuildIndex() {
        val sources = _state.value.lists
            .filter { it.enabled && it.entryCount > 0 }
            .map { fileFor(it.id) }
            .filter { it.exists() }
        index = when (sources.size) {
            0 -> HashIndex.EMPTY.also { activeFile.delete() }
            // A single list is already sorted & unique — map it directly, no copy.
            1 -> HashIndex.open(sources[0]).also { activeFile.delete() }
            else -> {
                HashIndex.merge(sources, activeFile)
                HashIndex.open(activeFile)
            }
        }
        _state.update { it.copy(activeEntries = index.size) }
    }

    private fun loadLists(): List<FilterList> {
        val saved = HashMap<String, JSONObject>()
        val imports = ArrayList<FilterList>()
        runCatching {
            val arr = JSONArray(prefs.getString(KEY_LISTS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                if (o.optBoolean("curated", true)) {
                    saved[id] = o
                } else {
                    imports += FilterList(
                        id = id,
                        name = o.optString("name", "Imported list"),
                        description = o.optString("description", ""),
                        category = FilterCategory.CUSTOM,
                        url = o.optString("url", "").ifEmpty { null },
                        curated = false,
                        recommended = false,
                        approxEntries = 0,
                        enabled = o.optBoolean("enabled", true),
                        entryCount = o.optInt("count", 0),
                        updatedAt = o.optLong("updatedAt", 0),
                    )
                }
            }
        }
        val curated = FilterCatalog.curated.map { c ->
            val o = saved[c.id]
            FilterList(
                id = c.id,
                name = c.name,
                description = c.description,
                category = c.category,
                url = c.url,
                curated = true,
                recommended = c.recommended,
                approxEntries = c.approxEntries,
                enabled = o?.optBoolean("enabled", false) ?: false,
                entryCount = o?.optInt("count", 0) ?: 0,
                updatedAt = o?.optLong("updatedAt", 0) ?: 0,
            )
        }
        return curated + imports
    }

    private fun persist() {
        val arr = JSONArray()
        for (l in _state.value.lists) {
            // Curated metadata lives in code; only persist per-list state for those.
            if (l.curated && !l.enabled && l.entryCount == 0) continue
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("curated", l.curated)
                put("enabled", l.enabled)
                put("count", l.entryCount)
                put("updatedAt", l.updatedAt)
                if (!l.curated) {
                    put("name", l.name)
                    put("description", l.description)
                    l.url?.let { put("url", it) }
                }
            })
        }
        prefs.edit().putString(KEY_LISTS, arr.toString()).apply()
    }

    companion object {
        private const val TAG = "PardaDNS/Filters"
        private const val PREFS_NAME = "parda_filters"
        private const val KEY_LISTS = "lists_json"
        const val REFRESH_ALL = "refresh_all"
        private const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    }
}

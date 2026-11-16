package com.example.pardadns.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the user's custom domain blocklist.
 *
 * Domains are stored in SharedPreferences as a Set<String> and kept in a
 * [ConcurrentHashMap]-backed set for O(1) hot-path lookups inside the VPN loop.
 *
 * All domains are stored and compared in **lowercase**.
 */
@Singleton
class BlocklistRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Thread-safe O(1) lookup set — the hot-path data structure. */
    private val blockedSet: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()

    /** Observable list for UI. */
    private val _domains = MutableStateFlow<List<String>>(emptyList())
    val domains: StateFlow<List<String>> = _domains.asStateFlow()

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        initScope.launch {
            val saved = prefs.getStringSet(KEY_BLOCKED_DOMAINS, emptySet()) ?: emptySet()
            blockedSet.addAll(saved)
            _domains.value = saved.sorted()
        }
    }

    /** O(1) check — called on every DNS query when the feature flag is on. */
    fun isBlocked(domain: String): Boolean = blockedSet.contains(domain.lowercase())

    fun addDomain(domain: String) {
        val d = domain.trim().lowercase()
        if (d.isBlank() || !blockedSet.add(d)) return
        persist()
    }

    fun removeDomain(domain: String) {
        val d = domain.trim().lowercase()
        if (!blockedSet.remove(d)) return
        persist()
    }

    fun clearAll() {
        blockedSet.clear()
        persist()
    }

    private fun persist() {
        val snapshot = blockedSet.toSet()
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, snapshot).apply()
        _domains.value = snapshot.sorted()
    }

    companion object {
        const val PREFS_NAME = "parda_blocklist"
        const val KEY_BLOCKED_DOMAINS = "blocked_domains"
    }
}

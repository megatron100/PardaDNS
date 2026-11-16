package com.example.pardadns.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time, in-memory traffic counters for the active VPN session.
 *
 * The hot path ([recordDnsQuery]) uses lock-free atomic increments with zero allocation —
 * safe to call from the VPN packet-processing loop without impacting throughput.
 *
 * UI observes via [observeStats], a polling [Flow] that samples the atomic counters at a
 * configurable interval (default 500 ms). The flow is only active while collected, so
 * there is zero overhead when the screen is off or the composable is not mounted.
 *
 * Also keeps a "blocked today" count for the home screen. Unlike the session counters it
 * survives reconnects and restarts (saved to prefs at most every [PERSIST_INTERVAL_MS]),
 * resets at local midnight, and works whether or not DNS Monitoring is on.
 */
@Singleton
class TrafficMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _totalDnsQueries = AtomicLong(0)
    private val _blockedDnsQueries = AtomicLong(0)
    private val _blockedConnections = AtomicLong(0)

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val _blockedToday = AtomicLong(0)
    /** Local date (epoch day) that [_blockedToday] counts for. */
    @Volatile private var todayKey = 0L
    /** Epoch millis of the next local midnight; 0 until today's saved count is loaded. */
    @Volatile private var nextMidnightMillis = 0L
    @Volatile private var lastPersistMillis = 0L

    // ── Hot path (called from VPN packet loop) ────────────────────────────────

    /** Record a completed DNS query/response pair. Lock-free; allocates only when saving. */
    fun recordDnsQuery(blocked: Boolean) {
        _totalDnsQueries.incrementAndGet()
        if (!blocked) return
        _blockedDnsQueries.incrementAndGet()
        val now = System.currentTimeMillis()
        rollDayIfNeeded(now)
        _blockedToday.incrementAndGet()
        if (now - lastPersistMillis >= PERSIST_INTERVAL_MS) persistBlockedToday(now)
    }

    /**
     * Record a connection attempt to a known-blocked IP (0.0.0.0, ::, 127.0.0.1).
     * These are TCP/UDP packets that the OS sends after caching a blocked DNS response.
     */
    fun recordBlockedConnection() {
        _blockedConnections.incrementAndGet()
    }

    // ── Observation (UI layer) ────────────────────────────────────────────────

    /**
     * Polling-based flow that emits a [TrafficSnapshot] every [intervalMs].
     * Only active while a collector is subscribed — zero cost when UI is invisible.
     */
    fun observeStats(intervalMs: Long = 500L): Flow<TrafficSnapshot> = flow {
        while (true) {
            emit(snapshot())
            delay(intervalMs)
        }
    }

    /** Instant read of current counters. */
    fun snapshot(): TrafficSnapshot {
        rollDayIfNeeded(System.currentTimeMillis())
        return TrafficSnapshot(
            totalDnsQueries = _totalDnsQueries.get(),
            blockedDnsQueries = _blockedDnsQueries.get(),
            blockedConnections = _blockedConnections.get(),
            blockedToday = _blockedToday.get(),
        )
    }

    /** Reset session counters — call at the start of each new VPN session. Keeps today's count. */
    fun resetSession() {
        _totalDnsQueries.set(0)
        _blockedDnsQueries.set(0)
        _blockedConnections.set(0)
    }

    /** Save today's blocked count now — call when the tunnel stops. */
    fun flush() {
        if (nextMidnightMillis != 0L) persistBlockedToday(System.currentTimeMillis())
    }

    // ── Blocked today ─────────────────────────────────────────────────────────

    /** Loads today's saved count on first use and starts from zero after midnight. */
    private fun rollDayIfNeeded(now: Long) {
        if (now < nextMidnightMillis) return
        synchronized(this) {
            if (now < nextMidnightMillis) return
            val today = LocalDate.now()
            val key = today.toEpochDay()
            val count = when {
                nextMidnightMillis != 0L -> 0L
                prefs.getLong(KEY_DAY, -1L) == key -> prefs.getLong(KEY_BLOCKED, 0L)
                else -> 0L
            }
            _blockedToday.set(count)
            todayKey = key
            nextMidnightMillis = today.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    private fun persistBlockedToday(now: Long) {
        lastPersistMillis = now
        prefs.edit()
            .putLong(KEY_DAY, todayKey)
            .putLong(KEY_BLOCKED, _blockedToday.get())
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "parda_traffic"
        const val KEY_DAY = "blocked_today_day"
        const val KEY_BLOCKED = "blocked_today_count"
        const val PERSIST_INTERVAL_MS = 5_000L
    }
}

/**
 * Immutable snapshot of traffic counters at a point in time.
 */
data class TrafficSnapshot(
    val totalDnsQueries: Long,
    val blockedDnsQueries: Long,
    val blockedConnections: Long,
    /** Ads and trackers blocked since local midnight, across all sessions. */
    val blockedToday: Long = 0,
) {
    /** Total items that were blocked (DNS + connection-level). */
    val totalBlocked: Long get() = blockedDnsQueries + blockedConnections

    /** Block rate as a 0..1 float. DNS queries are the denominator since every
     *  network request begins with a DNS lookup. */
    val blockRate: Float
        get() = if (totalDnsQueries == 0L) 0f
                else totalBlocked.toFloat() / totalDnsQueries

    companion object {
        val EMPTY = TrafficSnapshot(0, 0, 0)
    }
}

package com.example.pardadns.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily protection window. Protection turns on at [startMinute] on each selected day and
 * off at [endMinute]; an end at or before the start means the window runs overnight.
 * Start == end means the whole day.
 */
data class ProtectionSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 7 * 60,
    val endMinute: Int = 23 * 60,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = ALL_DAYS,
) {
    fun isDaySelected(day: DayOfWeek) = days and (1 shl (day.value - 1)) != 0

    private val durationMinutes: Long
        get() = (if (endMinute > startMinute) endMinute - startMinute else endMinute + 1440 - startMinute).toLong()

    fun isActiveAt(t: LocalDateTime): Boolean {
        val today = t.toLocalDate()
        // A window that started yesterday can still be running (overnight).
        for (date in listOf(today, today.minusDays(1))) {
            if (!isDaySelected(date.dayOfWeek)) continue
            val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
            val end = start.plusMinutes(durationMinutes)
            if (!t.isBefore(start) && t.isBefore(end)) return true
        }
        return false
    }

    /** The next time protection should turn on or off after [t], or null if no day is selected. */
    fun nextChangeAfter(t: LocalDateTime): LocalDateTime? = boundariesAfter(t, includeEnds = true)

    /** The next window start after [t]. */
    fun nextStartAfter(t: LocalDateTime): LocalDateTime? = boundariesAfter(t, includeEnds = false)

    private fun boundariesAfter(t: LocalDateTime, includeEnds: Boolean): LocalDateTime? {
        var best: LocalDateTime? = null
        val today = t.toLocalDate()
        for (offset in -1L..7L) {
            val date = today.plusDays(offset)
            if (!isDaySelected(date.dayOfWeek)) continue
            val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
            val candidates = if (includeEnds) listOf(start, start.plusMinutes(durationMinutes)) else listOf(start)
            for (c in candidates) {
                if (c.isAfter(t) && (best == null || c.isBefore(best))) best = c
            }
        }
        return best
    }

    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

/** Auto-connect and schedule preferences (Pro). Pause state lives in the VPN service. */
@Singleton
class AutomationRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = prefs(context)

    private val _schedule = MutableStateFlow(readSchedule(prefs))
    val schedule: StateFlow<ProtectionSchedule> = _schedule.asStateFlow()

    private val _connectOnBoot = MutableStateFlow(prefs.getBoolean(KEY_CONNECT_ON_BOOT, false))
    val connectOnBoot: StateFlow<Boolean> = _connectOnBoot.asStateFlow()

    private val _connectOnLaunch = MutableStateFlow(prefs.getBoolean(KEY_CONNECT_ON_LAUNCH, false))
    val connectOnLaunch: StateFlow<Boolean> = _connectOnLaunch.asStateFlow()

    fun setSchedule(schedule: ProtectionSchedule) {
        _schedule.value = schedule
        prefs.edit()
            .putBoolean(KEY_SCHEDULE_ENABLED, schedule.enabled)
            .putInt(KEY_SCHEDULE_START, schedule.startMinute)
            .putInt(KEY_SCHEDULE_END, schedule.endMinute)
            .putInt(KEY_SCHEDULE_DAYS, schedule.days)
            .apply()
    }

    fun setConnectOnBoot(enabled: Boolean) {
        _connectOnBoot.value = enabled
        prefs.edit().putBoolean(KEY_CONNECT_ON_BOOT, enabled).apply()
    }

    fun setConnectOnLaunch(enabled: Boolean) {
        _connectOnLaunch.value = enabled
        prefs.edit().putBoolean(KEY_CONNECT_ON_LAUNCH, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "parda_automation"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START = "schedule_start"
        private const val KEY_SCHEDULE_END = "schedule_end"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"
        private const val KEY_CONNECT_ON_BOOT = "connect_on_boot"
        private const val KEY_CONNECT_ON_LAUNCH = "connect_on_launch"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun readSchedule(prefs: SharedPreferences) = ProtectionSchedule(
            enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
            startMinute = prefs.getInt(KEY_SCHEDULE_START, 7 * 60),
            endMinute = prefs.getInt(KEY_SCHEDULE_END, 23 * 60),
            days = prefs.getInt(KEY_SCHEDULE_DAYS, ProtectionSchedule.ALL_DAYS),
        )

        /** Synchronous reads for alarm/boot receivers. */
        fun loadSchedule(context: Context): ProtectionSchedule = readSchedule(prefs(context))
        fun isConnectOnBoot(context: Context): Boolean = prefs(context).getBoolean(KEY_CONNECT_ON_BOOT, false)
    }
}

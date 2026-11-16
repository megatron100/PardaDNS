package com.example.pardadns.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import com.example.pardadns.MainActivity
import com.example.pardadns.R
import com.example.pardadns.data.repository.AutomationRepository
import com.example.pardadns.data.repository.ProRepository
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Arms the single AlarmManager alarm for the next schedule boundary and the pause-resume
 * alarm, and turns those alarms into service commands. Alarms (not a polling loop) keep
 * this at zero cost between boundaries.
 */
object ProtectionScheduler {
    private const val TAG = "PardaDNS/Scheduler"

    const val ACTION_SCHEDULE_TICK = "com.example.pardadns.SCHEDULE_TICK"
    const val ACTION_PAUSE_EXPIRED = "com.example.pardadns.PAUSE_EXPIRED"
    private const val REQ_SCHEDULE = 2001
    private const val REQ_RESUME = 2002

    private const val CHANNEL_ID = "parda_automation"
    private const val NOTIF_ID = 1002

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Re-arms the schedule alarm for the next on/off boundary (or cancels it). */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context, ACTION_SCHEDULE_TICK, REQ_SCHEDULE)
        am.cancel(pi)
        val schedule = AutomationRepository.loadSchedule(context)
        if (!schedule.enabled || !ProRepository.isPro(context)) return
        val next = schedule.nextChangeAfter(LocalDateTime.now()) ?: return
        setAlarm(context, am, next.toEpochMillis(), pi)
        Log.i(TAG, "Schedule alarm armed for $next")
    }

    fun armResume(context: Context, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        setAlarm(context, am, atMillis, alarmIntent(context, ACTION_PAUSE_EXPIRED, REQ_RESUME))
    }

    fun cancelResume(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(alarmIntent(context, ACTION_PAUSE_EXPIRED, REQ_RESUME))
    }

    /** Called when a schedule boundary is reached: turn protection on or off to match. */
    fun onScheduleTick(context: Context) {
        val schedule = AutomationRepository.loadSchedule(context)
        if (schedule.enabled && ProRepository.isPro(context)) {
            val now = LocalDateTime.now()
            if (schedule.isActiveAt(now)) {
                startProtection(context)
            } else {
                val nextStart = schedule.nextStartAfter(now)
                if (nextStart != null) {
                    // Only acts if the tunnel is up; a plain startService fails harmlessly otherwise.
                    runCatching {
                        context.startService(serviceIntent(context, PardaVpnService.ACTION_PAUSE).apply {
                            putExtra(PardaVpnService.EXTRA_PAUSE_UNTIL, nextStart.toEpochMillis())
                            putExtra(PardaVpnService.EXTRA_PAUSE_BY_SCHEDULE, true)
                        })
                    }.onFailure { Log.d(TAG, "Schedule off: service not running (${it.message})") }
                }
            }
        }
        reschedule(context)
    }

    fun onPauseExpired(context: Context) {
        startProtection(context)
    }

    fun onBoot(context: Context, isBoot: Boolean) {
        reschedule(context)
        if (!isBoot || !ProRepository.isPro(context) || !AutomationRepository.isConnectOnBoot(context)) return
        val schedule = AutomationRepository.loadSchedule(context)
        if (schedule.enabled && !schedule.isActiveAt(LocalDateTime.now())) return
        if (VpnService.prepare(context) != null) return   // VPN consent not granted yet
        startProtection(context)
    }

    /**
     * Resumes a pause or reconnects to the last provider. Background foreground-service starts
     * can be refused (Android 12+) — then we post a notification the user can tap instead.
     */
    private fun startProtection(context: Context) {
        try {
            context.startForegroundService(serviceIntent(context, PardaVpnService.ACTION_RESUME))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start protection from background: ${e.message}")
            notifyTapToConnect(context)
        }
    }

    private fun notifyTapToConnect(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Scheduled protection", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            NOTIF_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_shield)
                .setContentTitle("Protection is scheduled to be on")
                .setContentText("Tap to turn Panda DNS back on")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun setAlarm(context: Context, am: AlarmManager, atMillis: Long, pi: PendingIntent) {
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            // Inexact: may be deferred by a few minutes in Doze, but needs no special permission.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun alarmIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ProtectionAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceIntent(context: Context, action: String) =
        Intent(context, PardaVpnService::class.java).setAction(action)

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Receives our own schedule and pause alarms. Not exported. */
class ProtectionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ProtectionScheduler.ACTION_SCHEDULE_TICK -> ProtectionScheduler.onScheduleTick(context)
            ProtectionScheduler.ACTION_PAUSE_EXPIRED -> ProtectionScheduler.onPauseExpired(context)
        }
    }
}

/** Re-arms alarms after reboot/update and performs connect-on-boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ProtectionScheduler.onBoot(context, isBoot = true)
            Intent.ACTION_MY_PACKAGE_REPLACED -> ProtectionScheduler.onBoot(context, isBoot = false)
        }
    }
}

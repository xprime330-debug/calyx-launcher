package com.calyx.launcher

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar
import java.util.Locale

/** Optional daily schedules. Alarms are intentionally inexact and use local wall-clock time. */
object CenterSchedules {
    const val DND_START = "com.calyx.launcher.DND_START"
    const val DND_END = "com.calyx.launcher.DND_END"
    const val DIGEST = "com.calyx.launcher.DIGEST"
    const val BOOT = "android.intent.action.BOOT_COMPLETED"
    private const val DND_START_ID = 7401
    private const val DND_END_ID = 7402
    private const val DIGEST_ID = 7403
    const val DIGEST_CHANNEL = "calyx_digest"

    /** Keep existing alarms when merely returning to the launcher; avoids moving today's digest. */
    fun refresh(context: Context) {
        scheduleDnd(context, replace = false)
        scheduleDigest(context, replace = false)
    }

    /** Wall-clock and timezone changes require rebuilding the next local-time alarms. */
    fun refreshAfterClockChange(context: Context) {
        scheduleDnd(context, replace = true)
        scheduleDigest(context, replace = true)
    }

    fun scheduleDnd(context: Context, replace: Boolean = true) {
        val prefs = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dndScheduleEnabled", false)) {
            cancel(context, DND_START, DND_START_ID)
            cancel(context, DND_END, DND_END_ID)
            applyDnd(context, false)
            return
        }
        val start = prefs.getInt("dndStartMinute", 22 * 60).coerceIn(0, 1439)
        val end = prefs.getInt("dndEndMinute", 7 * 60).coerceIn(0, 1439)
        if (start == end) {
            cancel(context, DND_START, DND_START_ID)
            cancel(context, DND_END, DND_END_ID)
            applyDnd(context, false)
            return
        }
        val nowMinute = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val inQuietWindow = if (start < end) nowMinute in start until end else nowMinute >= start || nowMinute < end
        applyDnd(context, inQuietWindow)
        val now = System.currentTimeMillis()
        if (replace || !hasPending(context, DND_START, DND_START_ID)) {
            schedule(context, DND_START, DND_START_ID, nextTime(start, now))
        }
        if (replace || !hasPending(context, DND_END, DND_END_ID)) {
            schedule(context, DND_END, DND_END_ID, nextTime(end, now))
        }
    }

    fun scheduleDigest(context: Context, replace: Boolean = true) {
        val prefs = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("digestEnabled", false)) {
            cancel(context, DIGEST, DIGEST_ID)
            return
        }
        val minute = prefs.getInt("digestMinute", 18 * 60).coerceIn(0, 1439)
        if (replace || !hasPending(context, DIGEST, DIGEST_ID)) {
            schedule(context, DIGEST, DIGEST_ID, nextTime(minute, System.currentTimeMillis()))
        }
    }

    /** DND schedule restores its prior filter only if the user has not changed the filter meanwhile. */
    fun applyDnd(context: Context, quiet: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < 23 || !manager.isNotificationPolicyAccessGranted) return
        val prefs = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)
        try {
            if (quiet) {
                if (!prefs.getBoolean("dndScheduleApplied", false)) {
                    prefs.edit().putInt("dndPriorFilter", manager.currentInterruptionFilter)
                        .putBoolean("dndScheduleApplied", true).apply()
                }
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else if (prefs.getBoolean("dndScheduleApplied", false)) {
                val previous = prefs.getInt("dndPriorFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
                if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    manager.setInterruptionFilter(previous)
                }
                prefs.edit().putBoolean("dndScheduleApplied", false).apply()
            }
        } catch (_: SecurityException) { }
    }

    private fun nextTime(minuteOfDay: Int, nowMs: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun schedule(context: Context, action: String, id: Int, at: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, action, id))
    }

    private fun cancel(context: Context, action: String, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending(context, action, id))
    }

    private fun hasPending(context: Context, action: String, id: Int): Boolean {
        val intent = Intent(context, CalyxCenterReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
    }

    private fun pending(context: Context, action: String, id: Int): PendingIntent {
        val intent = Intent(context, CalyxCenterReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class CalyxCenterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CenterSchedules.DND_START -> {
                val enabled = context.getSharedPreferences("calyx", Context.MODE_PRIVATE).getBoolean("dndScheduleEnabled", false)
                CenterSchedules.applyDnd(context, enabled)
            }
            CenterSchedules.DND_END -> CenterSchedules.applyDnd(context, false)
            CenterSchedules.DIGEST -> postDigest(context)
            CenterSchedules.BOOT -> CenterSchedules.refreshAfterClockChange(context)
        }
        if (intent.action == CenterSchedules.DND_START || intent.action == CenterSchedules.DND_END) CenterSchedules.scheduleDnd(context)
        if (intent.action == CenterSchedules.DIGEST) CenterSchedules.scheduleDigest(context)
    }

    private fun postDigest(context: Context) {
        val prefs = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("digestEnabled", false) || !CalyxNotificationService.hasAccess(context)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val calmCount = CalyxNotificationService.snapshot().count { it.notification.priority < Notification.PRIORITY_DEFAULT }
        if (calmCount == 0) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CenterSchedules.DIGEST_CHANNEL, "Calyx digest", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(context, 7404, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val notification = Notification.Builder(context, CenterSchedules.DIGEST_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Calyx digest")
            .setContentText(String.format(Locale.getDefault(), "%d quieter notifications are ready to review", calmCount))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .build()
        manager.notify(7405, notification)
    }
}

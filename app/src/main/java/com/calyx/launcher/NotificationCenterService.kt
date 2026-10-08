package com.calyx.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.RemoteInput
import org.json.JSONArray
import org.json.JSONObject

/** The device-local, system-bound source of active notifications and app badge counts. */
class CalyxNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        val all = try { activeNotifications?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        synchronized(active) { active.clear(); all.filterNot(::isOurs).forEach { active[it.key] = it } }
        publishChanged()
    }

    override fun onListenerDisconnected() {
        current = null
        synchronized(active) { active.clear() }
        publishChanged()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isOurs(sbn)) return
        synchronized(active) { active[sbn.key] = sbn }
        NotificationHistory.record(this, sbn)
        publishChanged()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(active) { active.remove(sbn.key) }
        publishChanged()
    }

    private fun isOurs(sbn: StatusBarNotification): Boolean =
        sbn.packageName == packageName || (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

    private fun publishChanged() {
        val counts = synchronized(active) { active.values.groupingBy { it.packageName }.eachCount() }
        val edit = getSharedPreferences(BADGE_PREFS, Context.MODE_PRIVATE).edit().clear()
        counts.forEach { (pkg, count) -> edit.putInt(pkg, count) }
        edit.apply()
        sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
    }

    companion object {
        const val ACTION_CHANGED = "com.calyx.launcher.NOTIFICATION_STATE_CHANGED"
        const val BADGE_PREFS = "calyx_badges"
        private val active = LinkedHashMap<String, StatusBarNotification>()
        @Volatile private var current: CalyxNotificationService? = null

        fun hasAccess(context: Context): Boolean {
            val raw = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return raw.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }

        fun snapshot(): List<StatusBarNotification> = synchronized(active) { active.values.toList() }

        fun countFor(context: Context, packageName: String): Int {
            if (!hasAccess(context)) return 0
            return context.getSharedPreferences(BADGE_PREFS, Context.MODE_PRIVATE).getInt(packageName, 0)
        }

        fun dismiss(key: String): Boolean {
            val service = current ?: return false
            return try { service.cancelNotification(key); true } catch (_: Exception) { false }
        }

        fun dismissAll(): Boolean {
            val service = current ?: return false
            return try { service.cancelAllNotifications(); true } catch (_: Exception) { false }
        }

        fun snooze(key: String, durationMs: Long): Boolean {
            val service = current ?: return false
            return try { service.snoozeNotification(key, durationMs); true } catch (_: Exception) { false }
        }

        fun open(key: String): Boolean {
            val sbn = synchronized(active) { active[key] } ?: return false
            return try {
                val pending = sbn.notification.contentIntent ?: return false
                pending.send()
                if ((sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) dismiss(key)
                true
            } catch (_: Exception) { false }
        }

        fun sendReply(key: String, actionIndex: Int, reply: String): Boolean {
            val service = current ?: return false
            val sbn = synchronized(active) { active[key] } ?: return false
            val action = sbn.notification.actions?.getOrNull(actionIndex) ?: return false
            val inputs = action.remoteInputs?.takeIf { it.isNotEmpty() } ?: return false
            val input = inputs.firstOrNull { it.allowFreeFormInput } ?: return false
            return try {
                val fillIn = Intent()
                RemoteInput.addResultsToIntent(inputs, fillIn, Bundle().apply { putCharSequence(input.resultKey, reply) })
                action.actionIntent.send(service, 0, fillIn)
                true
            } catch (_: Exception) { false }
        }

        fun fireAction(key: String, actionIndex: Int): Boolean {
            val service = current ?: return false
            val action = synchronized(active) { active[key]?.notification?.actions?.getOrNull(actionIndex) } ?: return false
            return try { action.actionIntent.send(service, 0, Intent()); true } catch (_: Exception) { false }
        }
    }
}

data class CalyxNotificationHistoryEntry(val packageName: String, val title: String, val text: String, val time: Long)

/** History is stored separately from launcher backups and is disabled unless the user opts in. */
object NotificationHistory {
    private const val FILE = "calyx_notification_history"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 50

    fun record(context: Context, sbn: StatusBarNotification) {
        if (!context.getSharedPreferences("calyx", Context.MODE_PRIVATE).getBoolean("notificationHistoryEnabled", false)) return
        val extras = sbn.notification.extras
        val row = JSONObject().put("p", sbn.packageName)
            .put("t", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(160))
            .put("x", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(500))
            .put("d", sbn.postTime)
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray().put(row)
        for (i in 0 until minOf(old.length(), MAX_ENTRIES - 1)) next.put(old.optJSONObject(i))
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun entries(context: Context): List<CalyxNotificationHistoryEntry> {
        val rows = try { JSONArray(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            CalyxNotificationHistoryEntry(row.optString("p"), row.optString("t"), row.optString("x"), row.optLong("d"))
        }
    }

    fun clear(context: Context) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
}

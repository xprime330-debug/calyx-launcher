package com.calyx.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** UI surfaces for the v0.4 Centers milestone. Sensitive access is always user initiated. */
class CalyxCenters(
    private val activity: Activity,
    private val prefs: Prefs,
    private val palette: () -> Themes.Palette,
    private val apps: () -> List<AppInfo>,
    private val resolve: (String) -> AppInfo?,
    private val launch: (AppInfo) -> Unit,
    private val onBadgesChanged: () -> Unit
) {
    private var torchOn = false
    private var pendingTorchToggle = false
    private val notificationManager: NotificationManager
        get() = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun onResume() {
        CenterSchedules.refresh(activity)
        onBadgesChanged()
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CAMERA -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && pendingTorchToggle) {
                pendingTorchToggle = false
                setTorch(!torchOn)
            }
            REQUEST_POST_NOTIFICATIONS -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) CenterSchedules.scheduleDigest(activity)
                else Toast.makeText(activity, "Digest will appear in Calyx when opened; Android notifications remain off.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showNotificationCenter() {
        if (!CalyxNotificationService.hasAccess(activity)) {
            AlertDialog.Builder(activity, dialogTheme())
                .setTitle("Calyx Notification Center")
                .setMessage("Notification access is optional. It lets Calyx show and group your notifications, add app badges, and use the notification's own reply and snooze actions. Calyx does not send notification content to a server. Enable access in Android settings only if you want these features.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Permission Center") { _, _ -> showPermissionCenter() }
                .show()
            return
        }
        val root = column().apply { setPadding(dp(18), dp(12), dp(18), dp(6)) }
        val active = CalyxNotificationService.snapshot()
            .filter { it.packageName !in prefs.mutedNotificationPackages }
            .sortedByDescending { it.postTime }
        val calmCount = active.count { it.notification.priority < Notification.PRIORITY_DEFAULT }
        root.addView(label("NOW · ${active.size} notifications", 12f, palette().accent, true))
        if (prefs.privacyShield) {
            root.addView(label("PRIVACY SHIELD ON · message text is hidden in Calyx", 12f, palette().subtext, false).apply { setPadding(0, dp(5), 0, dp(10)) })
        }
        if (prefs.digestEnabled) {
            root.addView(label("CALM DIGEST · $calmCount quieter notifications · ${minuteText(prefs.digestMinute)}", 12f, palette().accent, true).apply { setPadding(0, dp(8), 0, dp(10)) })
        }
        if (active.isEmpty()) {
            root.addView(label("You're all caught up. Android's system shade is still available outside Calyx.", 15f, palette().subtext, false).apply { setPadding(0, dp(18), 0, dp(18)) })
        } else {
            active.groupBy { it.packageName }.values.forEach { group ->
                val pkg = group.first().packageName
                val groupTitle = label("${appLabel(pkg).uppercase()} · ${group.size}  ▾", 11f, palette().accent, true).apply {
                    setPadding(dp(2), dp(12), dp(2), dp(6)); isClickable = true; isFocusable = true
                }
                val groupCards = column()
                group.forEach { sbn -> groupCards.addView(notificationCard(sbn)) }
                var expanded = true
                groupTitle.setOnClickListener {
                    expanded = !expanded
                    groupCards.visibility = if (expanded) View.VISIBLE else View.GONE
                    groupTitle.text = "${appLabel(pkg).uppercase()} · ${group.size}  ${if (expanded) "▾" else "▸"}"
                }
                root.addView(groupTitle)
                root.addView(groupCards)
            }
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true; addView(root) }
        var notificationDialog: AlertDialog? = null
        val dialog = builder("Notifications").setView(scroll)
            .setNeutralButton("History") { _, _ -> showNotificationHistory() }
            .setNegativeButton("Permissions") { _, _ -> showPermissionCenter() }
            .setPositiveButton("Clear all") { _, _ ->
                AlertDialog.Builder(activity, dialogTheme()).setTitle("Clear all notifications?")
                    .setMessage("This dismisses active notifications from their apps, just like Clear all in Android's shade.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Clear all") { _, _ -> CalyxNotificationService.dismissAll(); onBadgesChanged(); notificationDialog?.dismiss() }
                    .show()
            }.create()
        notificationDialog = dialog
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * .82f).toInt())
    }

    private fun notificationCard(sbn: android.service.notification.StatusBarNotification): View {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: appLabel(sbn.packageName)
        val bodyRaw = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = if (prefs.privacyShield && bodyRaw.isNotBlank()) "Message hidden by Privacy Shield" else bodyRaw
        val card = column().apply {
            background = palette().surface(activity.resources.displayMetrics.density)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { CalyxNotificationService.open(sbn.key); onBadgesChanged() }
        }
        var swipeStartX = 0f
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { swipeStartX = event.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val swiped = abs(event.rawX - swipeStartX) >= dp(72)
                    if (swiped && sbn.isClearable) {
                        if (CalyxNotificationService.dismiss(sbn.key)) card.visibility = View.GONE
                        onBadgesChanged()
                    }
                    swiped && sbn.isClearable
                }
                else -> false
            }
        }
        val time = DateUtils.getRelativeTimeSpanString(sbn.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        card.addView(label("$title  ·  $time", 15f, palette().text, true))
        if (body.isNotBlank()) card.addView(label(body, 13f, palette().subtext, false).apply { setPadding(0, dp(5), 0, dp(8)) })
        val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(8), 0, 0) }
        val replyIndex = n.actions?.indexOfFirst { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true } ?: -1
        if (replyIndex >= 0) actionRow.addView(actionButton("Reply") { showReply(sbn.key, replyIndex, title) })
        actionRow.addView(actionButton("Snooze") { chooseSnooze(sbn.key) })
        actionRow.addView(actionButton("Mute in Calyx") {
            prefs.mutedNotificationPackages = prefs.mutedNotificationPackages + sbn.packageName
            Toast.makeText(activity, "Hidden in Calyx only; Android notifications are unchanged.", Toast.LENGTH_LONG).show()
            onBadgesChanged()
        })
        if (sbn.isClearable) actionRow.addView(actionButton("Dismiss") {
            if (CalyxNotificationService.dismiss(sbn.key)) card.visibility = View.GONE
            onBadgesChanged()
        })
        card.addView(actionRow)
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        card.layoutParams = lp
        return card
    }

    private fun showReply(key: String, actionIndex: Int, title: String) {
        val input = EditText(activity).apply { hint = "Write a reply"; maxLines = 4; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Reply · $title").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty() && !CalyxNotificationService.sendReply(key, actionIndex, text)) {
                    Toast.makeText(activity, "This notification's reply action is no longer available.", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun chooseSnooze(key: String) {
        val labels = arrayOf("15 minutes", "1 hour", "2 hours", "Until tomorrow")
        val durations = longArrayOf(15 * 60_000L, 60 * 60_000L, 2 * 60 * 60_000L, 24 * 60 * 60_000L)
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Snooze notification")
            .setItems(labels) { _, which ->
                if (!CalyxNotificationService.snooze(key, durations[which])) Toast.makeText(activity, "Snooze is unavailable for this notification.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showNotificationHistory() {
        val entries = NotificationHistory.entries(activity)
        val root = column().apply { setPadding(dp(18), dp(12), dp(18), dp(12)) }
        if (!prefs.notificationHistoryEnabled) {
            root.addView(label("History is off. Turn it on in Settings → Centers & privacy to keep a small, device-local log.", 14f, palette().subtext, false))
        } else if (entries.isEmpty()) {
            root.addView(label("No saved notifications yet.", 14f, palette().subtext, false))
        } else entries.forEach { entry ->
            val body = if (prefs.privacyShield && entry.text.isNotBlank()) "Message hidden by Privacy Shield" else entry.text
            root.addView(label("${appLabel(entry.packageName)} · ${DateUtils.getRelativeTimeSpanString(entry.time)}\n${entry.title}${if (body.isBlank()) "" else "\n$body"}", 14f, palette().text, false).apply {
                setPadding(dp(8), dp(9), dp(8), dp(9))
            })
        }
        AlertDialog.Builder(activity, dialogTheme()).setTitle("On-device history · ${entries.size}")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Close", null)
            .setPositiveButton("Clear history") { _, _ -> NotificationHistory.clear(activity) }
            .show()
    }

    fun showControlCenter() {
        val root = column().apply { setPadding(dp(18), dp(12), dp(18), dp(12)) }
        root.addView(label("CALYX / CONTROL", 12f, palette().accent, true))
        root.addView(label("Android keeps control of protected system actions. Unsupported toggles open the matching system panel.", 12f, palette().subtext, false).apply { setPadding(0, dp(4), 0, dp(12)) })
        val grid = GridLayout(activity).apply { columnCount = 2 }
        val tileNames = prefs.controlTileOrder.filter { it in supportedTiles() } + supportedTiles().filterNot { it in prefs.controlTileOrder }
        tileNames.forEachIndexed { index, name ->
            val tile = TextView(activity).apply {
                text = "$name\n${tileHint(name)}"
                textSize = 14f
                setTextColor(palette().text)
                setPadding(dp(13), dp(13), dp(13), dp(13))
                gravity = Gravity.CENTER_VERTICAL
                background = palette().surface(activity.resources.displayMetrics.density)
                isClickable = true; isFocusable = true
                setOnClickListener { haptic(it); runTile(name) }
            }
            val height = listOf(92, 74, 62)[prefs.controlTileSize]
            grid.addView(tile, GridLayout.LayoutParams(GridLayout.spec(index / 2), GridLayout.spec(index % 2, 1f)).apply {
                width = 0; this.height = dp(height); setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        root.addView(grid)
        root.addView(label("BRIGHTNESS", 11f, palette().accent, true).apply { setPadding(0, dp(14), 0, 0) })
        root.addView(systemSlider("Brightness", 1, 255, currentBrightness(), canWrite = Settings.System.canWrite(activity)) { value ->
            if (Settings.System.canWrite(activity)) Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
            else requestWriteSettings()
        })
        root.addView(label("VOLUME", 11f, palette().accent, true).apply { setPadding(0, dp(12), 0, 0) })
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        root.addView(systemSlider("Media volume", 0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.getStreamVolume(AudioManager.STREAM_MUSIC), true) { value ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        })
        root.addView(label("RINGER", 11f, palette().accent, true).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(systemSlider("Ring volume", 0, audio.getStreamMaxVolume(AudioManager.STREAM_RING), audio.getStreamVolume(AudioManager.STREAM_RING), true) { value ->
            audio.setStreamVolume(AudioManager.STREAM_RING, value, 0)
        })
        root.addView(actionButton("Screen timeout · ${timeoutLabel()}") { chooseTimeout() }.apply { setPadding(dp(12), dp(12), dp(12), dp(12)) })
        addMediaControls(root)
        val dialog = builder("Control Center").setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Permissions") { _, _ -> showPermissionCenter() }
            .setPositiveButton("Done", null).create()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * .86f).toInt())
    }

    private fun runTile(name: String) {
        when (name) {
            "Flashlight" -> toggleTorch()
            "Do Not Disturb" -> toggleDnd()
            "Rotation lock" -> toggleRotationLock()
            "Wi-Fi" -> if (Build.VERSION.SDK_INT >= 29) safeStart(Intent(Settings.Panel.ACTION_WIFI)) else safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
            "Bluetooth" -> safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "Timer" -> safeStart(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, 300).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
            "Alarm" -> safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            "Camera" -> safeStart(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
            "Calculator" -> safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            "Quick note" -> quickNote()
            "QR scanner" -> safeStart(Intent("com.google.zxing.client.android.SCAN"))
            "Magnifier" -> safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun supportedTiles(): List<String> = listOf("Flashlight", "Do Not Disturb", "Rotation lock", "Wi-Fi", "Bluetooth", "Timer", "Alarm", "Camera", "Calculator", "Quick note", "QR scanner", "Magnifier")

    private fun tileHint(name: String): String = when (name) {
        "Flashlight" -> if (torchOn) "On · tap to turn off" else "Tap to turn on"
        "Do Not Disturb" -> "System-wide quiet mode"
        "Rotation lock" -> "Android display orientation"
        "Wi-Fi" -> "Android network controls"
        "Bluetooth" -> "Android device settings"
        "Timer" -> "Start a five-minute timer"
        "Alarm" -> "Open alarms"
        "Camera" -> "Open camera app"
        "Calculator" -> "Open a calculator"
        "Quick note" -> "Save a private note on this device"
        "QR scanner" -> "Use an installed scanner"
        else -> "Accessibility magnifier settings"
    }

    private fun addMediaControls(root: LinearLayout) {
        val media = CalyxNotificationService.snapshot().firstOrNull { sbn ->
            sbn.notification.category == Notification.CATEGORY_TRANSPORT || sbn.notification.actions?.isNotEmpty() == true && sbn.notification.extras.containsKey("android.mediaSession")
        } ?: return
        val actions = media.notification.actions.orEmpty()
        if (actions.isEmpty()) return
        root.addView(label("NOW PLAYING · ${appLabel(media.packageName)}", 11f, palette().accent, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        media.notification.getLargeIcon()?.loadDrawable(activity)?.let { artwork ->
            val cover = ImageView(activity).apply {
                setImageDrawable(artwork)
                contentDescription = "Album art from ${appLabel(media.packageName)}"
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = palette().surface(activity.resources.displayMetrics.density)
                clipToOutline = true
            }
            root.addView(cover, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(6) })
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.take(3).forEachIndexed { index, action ->
            val text = action.title?.toString()?.take(12)?.ifBlank { "Control" } ?: "Control"
            row.addView(actionButton(text) { CalyxNotificationService.fireAction(media.key, index) })
        }
        root.addView(row)
    }

    private fun systemSlider(name: String, min: Int, max: Int, value: Int, canWrite: Boolean, onChange: (Int) -> Unit): View {
        val wrap = column()
        wrap.addView(label(if (canWrite) "$name · $value" else "$name · Android access required", 12f, palette().subtext, false))
        val seek = SeekBar(activity).apply { this.max = (max - min).coerceAtLeast(1); progress = (value - min).coerceIn(0, this.max); isEnabled = canWrite }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser && canWrite) onChange(progress + min) }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        wrap.addView(seek)
        if (!canWrite) wrap.addView(actionButton("Allow Calyx to change $name") { requestWriteSettings() })
        return wrap
    }

    private fun currentBrightness(): Int = try { Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(1, 255) } catch (_: Exception) { 128 }

    private fun toggleDnd() {
        if (Build.VERSION.SDK_INT < 23) { safeStart(Intent(Settings.ACTION_SOUND_SETTINGS)); return }
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            AlertDialog.Builder(activity, dialogTheme()).setTitle("Do Not Disturb access")
                .setMessage("This tile changes Do Not Disturb for the whole device. Calyx will not change it until you grant access in Android settings.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Permission Center") { _, _ -> showPermissionCenter() }.show()
            return
        }
        val quiet = notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        try { notificationManager.setInterruptionFilter(if (quiet) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL) }
        catch (_: SecurityException) { Toast.makeText(activity, "Android did not allow the DND change", Toast.LENGTH_SHORT).show() }
    }

    private fun toggleTorch() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(activity, dialogTheme()).setTitle("Flashlight access")
                .setMessage("Calyx requests camera permission only when you use the flashlight tile. The camera is not opened and no image is taken.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                    pendingTorchToggle = true
                    activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
                }.show()
            return
        }
        setTorch(!torchOn)
    }

    private fun setTorch(enabled: Boolean) {
        try {
            val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (id == null) { Toast.makeText(activity, "No flashlight is available", Toast.LENGTH_SHORT).show(); return }
            manager.setTorchMode(id, enabled)
            torchOn = enabled
        } catch (_: Exception) { Toast.makeText(activity, "Flashlight could not be changed", Toast.LENGTH_SHORT).show() }
    }

    private fun quickNote() {
        val input = EditText(activity).apply {
            hint = "A note stays on this device"
            setText(activity.getSharedPreferences("calyx_quick_notes", Context.MODE_PRIVATE).getString("latest", ""))
            minLines = 3; maxLines = 8
        }
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Quick note · local only").setView(input)
            .setNegativeButton("Close", null)
            .setPositiveButton("Save") { _, _ ->
                activity.getSharedPreferences("calyx_quick_notes", Context.MODE_PRIVATE).edit().putString("latest", input.text.toString()).apply()
                Toast.makeText(activity, "Note saved on this device", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun chooseTimeout() {
        val values = intArrayOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)
        val labels = arrayOf("15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes")
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Screen timeout")
            .setItems(labels) { _, which ->
                if (!Settings.System.canWrite(activity)) requestWriteSettings()
                else try { Settings.System.putInt(activity.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, values[which]) }
                catch (_: Exception) { Toast.makeText(activity, "Android did not allow that setting", Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun timeoutLabel(): String {
        val millis = try { Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000) } catch (_: Exception) { 60_000 }
        return when { millis < 60_000 -> "${millis / 1000}s"; millis % 60_000 == 0 -> "${millis / 60_000}m"; else -> "${millis / 1000}s" }
    }

    private fun toggleRotationLock() {
        if (!Settings.System.canWrite(activity)) { requestWriteSettings(); return }
        try {
            val resolver = activity.contentResolver
            val locked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
            if (!locked) Settings.System.putInt(resolver, Settings.System.USER_ROTATION, 0)
        } catch (_: Exception) { Toast.makeText(activity, "Android did not allow rotation control", Toast.LENGTH_SHORT).show() }
    }

    fun showPermissionCenter() {
        val root = column().apply { setPadding(dp(18), dp(8), dp(18), dp(10)) }
        root.addView(label("Calyx asks only when a feature needs access. Android owns every grant; you can revoke it later in Settings.", 13f, palette().subtext, false).apply { setPadding(0, 0, 0, dp(10)) })
        permissionRow(root, "Notification access", if (CalyxNotificationService.hasAccess(activity)) "On · enables Calyx notifications and badges" else "Off · optional; reads notifications for the Calyx panel") {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        permissionRow(root, "Change system settings", if (Settings.System.canWrite(activity)) "On · brightness, rotation and timeout" else "Off · only needed for system sliders") { requestWriteSettings() }
        permissionRow(root, "Do Not Disturb policy", if (Build.VERSION.SDK_INT < 23 || notificationManager.isNotificationPolicyAccessGranted) "On · allows a system-wide DND change" else "Off · optional; schedule and DND tile need this") {
            if (Build.VERSION.SDK_INT >= 23) safeStart(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) else safeStart(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        val cameraGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        permissionRow(root, "Camera · flashlight only", if (cameraGranted) "On · used only to switch the torch" else "Off · asked only when you tap Flashlight") {
            if (!cameraGranted) activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
        val postGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        permissionRow(root, "Post digest notifications", if (postGranted) "On · optional scheduled digest" else "Off · needed only to send the digest alert") {
            if (Build.VERSION.SDK_INT >= 33 && !postGranted) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        }
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Permission Center")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Close", null).setPositiveButton("Done", null).show()
    }

    private fun permissionRow(root: LinearLayout, title: String, status: String, action: () -> Unit) {
        val row = column().apply {
            background = palette().surface(activity.resources.displayMetrics.density)
            setPadding(dp(14), dp(12), dp(14), dp(12)); isClickable = true; isFocusable = true
            setOnClickListener { action() }
        }
        row.addView(label(title, 14f, palette().text, true))
        row.addView(label(status, 12f, palette().subtext, false).apply { setPadding(0, dp(4), 0, 0) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun requestWriteSettings() {
        if (Build.VERSION.SDK_INT >= 23) safeStart(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }

    fun showPeekPanel() {
        val root = column().apply { setPadding(dp(18), dp(14), dp(18), dp(14)) }
        root.addView(label("PEEK / YOUR SHORTCUTS", 12f, palette().accent, true))
        root.addView(label("A small edge panel. Your picks stay on this device.", 12f, palette().subtext, false).apply { setPadding(0, dp(5), 0, dp(12)) })
        val favoriteKeys = prefs.peekFavorites.ifEmpty { (prefs.dockApps + prefs.homeApps).distinct().take(8) }
        favoriteKeys.mapNotNull(resolve).forEach { app ->
            val cell = makeAppCell(activity, app, palette().text, 42, labels = true, shadow = false, shape = prefs.iconShape,
                badgeCount = if (prefs.notificationBadgesEnabled && !app.isFolder && app.component.packageName !in prefs.mutedNotificationPackages) CalyxNotificationService.countFor(activity, app.component.packageName) else 0)
            cell.orientation = LinearLayout.HORIZONTAL
            cell.gravity = Gravity.CENTER_VERTICAL
            cell.setPadding(dp(6), dp(5), dp(6), dp(5))
            cell.setOnClickListener { launch(app) }
            root.addView(cell, LinearLayout.LayoutParams(-1, dp(58)))
        }
        root.addView(actionButton("Choose favorite apps") { managePeekFavorites() })
        root.addView(actionButton("Notifications") { showNotificationCenter() })
        root.addView(actionButton("Control Center") { showControlCenter() })
        root.addView(actionButton("Quick note") { quickNote() })
        val dialog = builder("Peek Panel").setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Close", null).create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(dp(320).coerceAtMost(activity.resources.displayMetrics.widthPixels), ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setGravity(Gravity.END)
    }

    fun managePeekFavorites() {
        val items = apps().filterNot { it.isFolder }
        val selected = items.map { it.key in prefs.peekFavorites }.toBooleanArray()
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Peek favorites · up to 8")
            .setMultiChoiceItems(items.map { it.label }.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.peekFavorites = items.filterIndexed { index, _ -> selected[index] }.take(8).map { it.key }
                showPeekPanel()
            }.show()
    }

    fun manageMutedApps() {
        val packages = (CalyxNotificationService.snapshot().map { it.packageName } + prefs.mutedNotificationPackages).distinct()
        if (packages.isEmpty()) {
            Toast.makeText(activity, "No notification apps to manage yet", Toast.LENGTH_SHORT).show()
            return
        }
        val checked = packages.map { it in prefs.mutedNotificationPackages }.toBooleanArray()
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Mute inside Calyx only")
            .setMessage("Muted apps still post notifications in Android. This only filters the Calyx panel and badges.")
            .setMultiChoiceItems(packages.map(::appLabel).toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.mutedNotificationPackages = packages.filterIndexed { index, _ -> checked[index] }
                onBadgesChanged()
            }.show()
    }

    fun manageControlTiles() {
        val order = prefs.controlTileOrder.toMutableList().apply { supportedTiles().forEach { if (it !in this) add(it) } }
        AlertDialog.Builder(activity, dialogTheme()).setTitle("Choose a tile to move")
            .setItems(order.toTypedArray()) { _, selected ->
                val tile = order[selected]
                val positions = (0 until order.size).map { "Position ${it + 1}" }.toTypedArray()
                AlertDialog.Builder(activity, dialogTheme()).setTitle("Move $tile to")
                    .setItems(positions) { _, destination ->
                        order.remove(tile); order.add(destination.coerceIn(0, order.size), tile)
                        prefs.controlTileOrder = order
                    }.show()
            }.show()
    }

    fun setDigestEnabled(enabled: Boolean) {
        prefs.digestEnabled = enabled
        if (!enabled) { CenterSchedules.scheduleDigest(activity); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(activity, dialogTheme()).setTitle("Allow digest notifications?")
                .setMessage("Calyx can send one daily, count-only reminder. Notification content stays on this device. You can also view the digest inside Calyx without granting this permission.")
                .setNegativeButton("Use Calyx only") { _, _ -> CenterSchedules.scheduleDigest(activity) }
                .setPositiveButton("Continue") { _, _ -> activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS) }
                .show()
        } else CenterSchedules.scheduleDigest(activity)
    }

    fun configureDigestTime() {
        val minute = prefs.digestMinute
        TimePickerDialog(activity, { _, hour, min ->
            prefs.digestMinute = hour * 60 + min
            if (prefs.digestEnabled) CenterSchedules.scheduleDigest(activity)
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(activity)).show()
    }

    fun configureDigestTimeAndEnable() {
        val minute = prefs.digestMinute
        TimePickerDialog(activity, { _, hour, min ->
            prefs.digestMinute = hour * 60 + min
            setDigestEnabled(true)
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(activity)).show()
    }

    fun toggleDndSchedule() {
        if (prefs.dndScheduleEnabled) {
            prefs.dndScheduleEnabled = false
            CenterSchedules.scheduleDnd(activity)
            Toast.makeText(activity, "DND schedule paused", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && !notificationManager.isNotificationPolicyAccessGranted) {
            AlertDialog.Builder(activity, dialogTheme()).setTitle("DND schedule needs access")
                .setMessage("The schedule changes Do Not Disturb for the whole device. Android requires you to grant Notification Policy Access before Calyx can run it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Permission Center") { _, _ -> showPermissionCenter() }.show()
            return
        }
        val start = prefs.dndStartMinute
        TimePickerDialog(activity, { _, sh, sm ->
            prefs.dndStartMinute = sh * 60 + sm
            val end = prefs.dndEndMinute
            TimePickerDialog(activity, { _, eh, em ->
                if (prefs.dndStartMinute == eh * 60 + em) {
                    Toast.makeText(activity, "Choose different start and end times", Toast.LENGTH_LONG).show()
                } else {
                    prefs.dndEndMinute = eh * 60 + em
                    prefs.dndScheduleEnabled = true
                    CenterSchedules.scheduleDnd(activity)
                    Toast.makeText(activity, "Quiet hours scheduled · ${minuteText(prefs.dndStartMinute)}–${minuteText(prefs.dndEndMinute)}", Toast.LENGTH_LONG).show()
                }
            }, end / 60, end % 60, android.text.format.DateFormat.is24HourFormat(activity)).apply { setTitle("End quiet hours") }.show()
        }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(activity)).apply { setTitle("Start quiet hours") }.show()
    }

    private fun notificationCardName(packageName: String): String = appLabel(packageName)

    private fun appLabel(packageName: String): String = try {
        val info = activity.packageManager.getApplicationInfo(packageName, 0)
        activity.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) { packageName.substringAfterLast('.') }

    private fun minuteText(minute: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

    private fun builder(title: String): AlertDialog.Builder = AlertDialog.Builder(activity, dialogTheme()).setTitle(title)
    private fun dialogTheme(): Int = if (palette().lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK
    private fun column(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun dp(value: Int): Int = activity.dp(value)

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun actionButton(title: String, action: () -> Unit): TextView = TextView(activity).apply {
        text = title; textSize = 12f; setTextColor(palette().text); gravity = Gravity.CENTER
        setPadding(dp(10), dp(8), dp(10), dp(8)); isClickable = true; isFocusable = true
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(palette().search) }
        setOnClickListener { haptic(it); action() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }
    }

    private fun haptic(view: View) {
        if (prefs.controlHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun safeStart(intent: Intent) {
        try { activity.startActivity(intent) } catch (_: Exception) { Toast.makeText(activity, "That Android setting or app is not available on this device", Toast.LENGTH_LONG).show() }
    }

    companion object {
        const val REQUEST_CAMERA = 7801
        const val REQUEST_POST_NOTIFICATIONS = 7802
    }
}

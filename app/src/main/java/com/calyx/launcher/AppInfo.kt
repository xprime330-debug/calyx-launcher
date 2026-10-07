package com.calyx.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

 data class AppInfo(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
    val category: String = "Tools",
    val folderId: String? = null,
    val folderAppKeys: List<String> = emptyList()
) {
    val key: String get() = folderId?.let { "folder:$it" } ?: component.flattenToString()
    val isFolder: Boolean get() = folderId != null
}

object AppLoader {
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { result ->
                val app = result.activityInfo.applicationInfo
                val label = result.loadLabel(pm).toString()
                val category = when {
                    app.category == ApplicationInfo.CATEGORY_GAME -> "Games"
                    listOf("social", "chat", "message", "community").any { "$label ${app.packageName}".contains(it, true) } -> "Social"
                    listOf("music", "video", "camera", "photo", "gallery", "stream").any { "$label ${app.packageName}".contains(it, true) } -> "Media"
                    else -> "Tools"
                }
                AppInfo(label, ComponentName(app.packageName, result.activityInfo.name), result.loadIcon(pm), category)
            }
            .sortedBy { it.label.lowercase() }
    }
}

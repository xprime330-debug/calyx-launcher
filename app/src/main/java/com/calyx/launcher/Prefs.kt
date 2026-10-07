package com.calyx.launcher

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/** Persistent, device-local launcher settings; existing v0.2 keys remain compatible. */
data class CalyxFolder(val id: String, val name: String, val appKeys: List<String>)

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)
    var theme: Int
        get() = sp.getInt("theme", Themes.DARK)
        set(value) { sp.edit().putInt("theme", value).apply() }
    var styleFamily: Int
        get() = sp.getInt("styleFamily", StyleFamily.GLASS).coerceIn(0, StyleFamily.names.lastIndex)
        set(value) { sp.edit().putInt("styleFamily", value.coerceIn(0, StyleFamily.names.lastIndex)).apply() }
    var columns: Int
        get() = sp.getInt("columns", 4).coerceIn(3, 8)
        set(value) { sp.edit().putInt("columns", value.coerceIn(3, 8)).apply() }
    var iconSize: Int
        get() = sp.getInt("iconSize", 58).coerceIn(40, 88)
        set(value) { sp.edit().putInt("iconSize", value.coerceIn(40, 88)).apply() }
    var accentColor: Int
        get() = sp.getInt("accentColor", 0)
        set(value) { sp.edit().putInt("accentColor", value).apply() }
    var iconShape: Int
        get() = sp.getInt("iconShape", IconShape.SQUIRCLE)
        set(value) { sp.edit().putInt("iconShape", value).apply() }
    var transition: Int
        get() = sp.getInt("transition", PageTransition.SLIDE)
        set(value) { sp.edit().putInt("transition", value).apply() }
    var animationSpeed: Int
        get() = sp.getInt("animationSpeed", 100).coerceIn(50, 150)
        set(value) { sp.edit().putInt("animationSpeed", value.coerceIn(50, 150)).apply() }
    var drawerBlur: Boolean
        get() = sp.getBoolean("drawerBlur", true)
        set(value) { sp.edit().putBoolean("drawerBlur", value).apply() }
    var drawerStyle: Int
        get() = sp.getInt("drawerStyle", 0).coerceIn(0, 3)
        set(value) { sp.edit().putInt("drawerStyle", value.coerceIn(0, 3)).apply() }
    var drawerColumns: Int
        get() = sp.getInt("drawerColumns", 4).coerceIn(3, 6)
        set(value) { sp.edit().putInt("drawerColumns", value.coerceIn(3, 6)).apply() }
    var drawerCategory: String
        get() = sp.getString("drawerCategory", "All") ?: "All"
        set(value) { sp.edit().putString("drawerCategory", value).apply() }
    var frequentFirst: Boolean
        get() = sp.getBoolean("frequentFirst", false)
        set(value) { sp.edit().putBoolean("frequentFirst", value).apply() }
    var folderStyle: Int
        get() = sp.getInt("folderStyle", 0).coerceIn(0, 2)
        set(value) { sp.edit().putInt("folderStyle", value.coerceIn(0, 2)).apply() }
    var dockCapacity: Int
        get() = sp.getInt("dockCapacity", 4).coerceIn(4, 7)
        set(value) { sp.edit().putInt("dockCapacity", value.coerceIn(4, 7)).apply() }
    var weatherCity: String
        get() = sp.getString("weatherCity", "") ?: ""
        set(value) { sp.edit().putString("weatherCity", value.trim()).apply() }
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(value) { sp.edit().putBoolean("seeded", value).apply() }
    var homeApps: List<String>
        get() = readList("home")
        set(value) { writeList("home", value) }
    var dockApps: List<String>
        get() = readList("dock")
        set(value) { writeList("dock", value) }
    var hiddenApps: List<String>
        get() = readList("hiddenApps")
        set(value) { writeList("hiddenApps", value) }
    var appWidgetIds: List<Int>
        get() = readList("widgetIds").mapNotNull { it.toIntOrNull() }
        set(value) { writeList("widgetIds", value.map(Int::toString)) }
    var builtinWidgets: List<String>
        get() = readList("builtinWidgets")
        set(value) { writeList("builtinWidgets", value.distinct()) }
    val recentApps: List<String> get() = readList("recentApps")
    var folders: List<CalyxFolder>
        get() = readList("folders").mapNotNull { row ->
            val parts = row.split('|')
            if (parts.size != 3) null else try {
                CalyxFolder(parts[0], decode(parts[1]), parts[2].split(',').filter(String::isNotBlank).map(::decode))
            } catch (_: Exception) { null }
        }
        set(value) { writeList("folders", value.map { folder ->
            "${folder.id}|${encode(folder.name)}|${folder.appKeys.joinToString(",", transform = ::encode)}"
        }) }

    fun incrementLaunch(key: String) {
        val counts = sp.getString("launchCounts", "")?.lines().orEmpty().mapNotNull {
            val pair = it.split('\t'); if (pair.size == 2) pair[0] to (pair[1].toIntOrNull() ?: 0) else null
        }.toMap().toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        val recent = (listOf(key) + recentApps.filterNot { it == key }).take(8)
        sp.edit().putString("launchCounts", counts.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .putString("recentApps", recent.joinToString("\n")).apply()
    }
    fun launchCount(key: String): Int = sp.getString("launchCounts", "")?.lineSequence()?.mapNotNull {
        val pair = it.split('\t'); if (pair.size == 2 && pair[0] == key) pair[1].toIntOrNull() else null
    }?.firstOrNull() ?: 0

    fun export(): String = sp.all.entries.filterNot { it.key == "widgetIds" || it.key == "launchCounts" || it.key == "recentApps" }.sortedBy { it.key }.joinToString("\n") { (key, value) ->
        val encoded = when (value) {
            is String -> "s:" + value.replace("\\", "\\\\").replace("\n", "\\n")
            is Int -> "i:$value"
            is Boolean -> "b:$value"
            else -> return@joinToString ""
        }
        "$key=$encoded"
    }.lines().filter { it.isNotEmpty() }.joinToString("\n")

    fun import(serialized: String): Boolean {
        val editor = sp.edit().clear()
        try {
            serialized.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val split = line.indexOf('='); require(split > 0)
                val key = line.substring(0, split); val value = line.substring(split + 1)
                when {
                    value.startsWith("s:") -> editor.putString(key, value.drop(2).replace("\\n", "\n").replace("\\\\", "\\"))
                    value.startsWith("i:") -> editor.putInt(key, value.drop(2).toInt())
                    value.startsWith("b:") -> editor.putBoolean(key, value.drop(2).toBooleanStrict())
                    else -> error("Unsupported setting")
                }
            }
            editor.apply(); return true
        } catch (_: Exception) { return false }
    }

    private fun encode(s: String): String = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun decode(s: String): String = String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
    private fun readList(key: String): List<String> = (sp.getString(key, "") ?: "").takeIf(String::isNotEmpty)?.split('\n') ?: emptyList()
    private fun writeList(key: String, list: List<String>) { sp.edit().putString(key, list.joinToString("\n")).apply() }
}

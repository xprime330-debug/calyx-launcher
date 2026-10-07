package com.calyx.launcher

import android.content.Context
import android.content.SharedPreferences

/** Persistent, device-local launcher settings. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("calyx", Context.MODE_PRIVATE)

    var theme: Int
        get() = sp.getInt("theme", Themes.DARK)
        set(value) { sp.edit().putInt("theme", value).apply() }

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

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(value) { sp.edit().putBoolean("seeded", value).apply() }

    var homeApps: List<String>
        get() = readList("home")
        set(value) { writeList("home", value) }

    var dockApps: List<String>
        get() = readList("dock")
        set(value) { writeList("dock", value) }

    fun export(): String = sp.all.entries.sortedBy { it.key }.joinToString("\n") { (key, value) ->
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
                val split = line.indexOf('=')
                require(split > 0)
                val key = line.substring(0, split)
                val value = line.substring(split + 1)
                when {
                    value.startsWith("s:") -> editor.putString(key, value.drop(2).replace("\\n", "\n").replace("\\\\", "\\"))
                    value.startsWith("i:") -> editor.putInt(key, value.drop(2).toInt())
                    value.startsWith("b:") -> editor.putBoolean(key, value.drop(2).toBooleanStrict())
                    else -> error("Unsupported setting")
                }
            }
            editor.apply()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun readList(key: String): List<String> {
        val raw = sp.getString(key, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    private fun writeList(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }
}

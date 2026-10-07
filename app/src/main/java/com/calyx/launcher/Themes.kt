package com.calyx.launcher

import android.graphics.drawable.GradientDrawable

object Themes {
    const val LIGHT = 0
    const val DARK = 1
    const val COLORFUL = 2
    val names = arrayOf("Light", "Dark", "Colorful")

    class Palette(
        val text: Int,
        val subtext: Int,
        val scrim: Int,
        val dock: Int,
        val search: Int,
        val gradient: IntArray?,
        val lightDialog: Boolean,
        val accent: Int
    ) {
        fun drawerBackground(): GradientDrawable {
            val g = gradient
            return if (g != null) GradientDrawable(GradientDrawable.Orientation.TL_BR, g)
            else GradientDrawable().also { it.setColor(scrim) }
        }
    }

    fun get(theme: Int, accent: Int? = null): Palette {
        val base = when (theme) {
            LIGHT -> Palette(0xFF1B1B1F.toInt(), 0xFF6A6A75.toInt(), 0xEBF5F5F8.toInt(), 0xB3FFFFFF.toInt(), 0xFFE4E4EB.toInt(), null, true, 0xFF6750A4.toInt())
            COLORFUL -> Palette(0xFFFFFFFF.toInt(), 0xFFEDE7FF.toInt(), 0xF2C44DFF.toInt(), 0x66FFFFFF.toInt(), 0x44FFFFFF.toInt(), intArrayOf(0xF2FF6B6B.toInt(), 0xF2C44DFF.toInt(), 0xF23D6BFF.toInt()), false, 0xFFFFC857.toInt())
            else -> Palette(0xFFFFFFFF.toInt(), 0xFFB5B5C0.toInt(), 0xEB0E0E12.toInt(), 0x802A2A30.toInt(), 0xFF26262C.toInt(), null, false, 0xFF9B8CFF.toInt())
        }
        return if (accent == null) base else Palette(base.text, base.subtext, base.scrim, base.dock, base.search, base.gradient, base.lightDialog, accent)
    }
}

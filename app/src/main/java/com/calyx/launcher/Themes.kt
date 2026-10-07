package com.calyx.launcher

import android.graphics.drawable.GradientDrawable

object StyleFamily {
    const val GLASS = 0
    const val FLOW = 1
    const val DYNAMIC = 2
    val names = arrayOf("Calyx Glass", "Calyx Flow", "Calyx Dynamic")
}

object Themes {
    const val LIGHT = 0
    const val DARK = 1
    const val COLORFUL = 2
    val names = arrayOf("Light", "Dark", "Colorful")

    class Palette(
        val text: Int, val subtext: Int, val scrim: Int, val dock: Int,
        val search: Int, val gradient: IntArray?, val lightDialog: Boolean,
        val accent: Int, val card: Int, val radiusDp: Int
    ) {
        fun drawerBackground(density: Float = 1f): GradientDrawable {
            val drawable = if (gradient != null) GradientDrawable(GradientDrawable.Orientation.TL_BR, gradient)
            else GradientDrawable().also { it.setColor(scrim) }
            drawable.cornerRadius = radiusDp * density
            return drawable
        }
        fun surface(density: Float = 1f): GradientDrawable = GradientDrawable().apply {
            cornerRadius = radiusDp * density
            setColor(card)
            setStroke(1, if (lightDialog) 0x22333344 else 0x33FFFFFF)
        }
    }

    fun get(theme: Int, accent: Int? = null, family: Int = StyleFamily.GLASS): Palette {
        val isLight = theme == LIGHT
        val effectiveAccent = accent ?: when (theme) {
            COLORFUL -> 0xFFFFC857.toInt()
            else -> 0xFF89E5FF.toInt()
        }
        val darkBase = when (theme) {
            COLORFUL -> 0xF2C44DFF.toInt()
            LIGHT -> 0xEBF5F5F8.toInt()
            else -> 0xEB101823.toInt()
        }
        return when (family) {
            StyleFamily.FLOW -> Palette(
                if (isLight) 0xFF202B3C.toInt() else 0xFFF2F6FF.toInt(),
                if (isLight) 0xFF69758A.toInt() else 0xFFB4C3D8.toInt(),
                if (isLight) 0xF2F2F5F9.toInt() else 0xF21A2636.toInt(),
                if (isLight) 0xE6FFFFFF.toInt() else 0xD9223042.toInt(),
                if (isLight) 0xFFF0F3F8.toInt() else 0xFF26374A.toInt(), null, isLight,
                effectiveAccent, if (isLight) 0xFFFFFFFF.toInt() else 0xFF1D2A3A.toInt(), 28
            )
            StyleFamily.DYNAMIC -> Palette(
                if (isLight) 0xFF1C2028.toInt() else 0xFFF8F6FF.toInt(),
                if (isLight) 0xFF69707B.toInt() else 0xFFC6C3D2.toInt(),
                if (isLight) 0xF2F5F6FA.toInt() else 0xEE171822.toInt(),
                if (isLight) 0xDDFBFBFF.toInt() else 0xCC272936.toInt(),
                if (isLight) 0xFFF0F1F8.toInt() else 0xFF292A38.toInt(), null, isLight,
                effectiveAccent, if (isLight) 0xFFFFFFFF.toInt() else 0xFF22232F.toInt(), 26
            )
            else -> Palette(
                if (isLight) 0xFF1B1B1F.toInt() else 0xFFFFFFFF.toInt(),
                if (isLight) 0xFF6A6A75.toInt() else 0xFFB5B5C0.toInt(),
                darkBase,
                if (isLight) 0xB3FFFFFF.toInt() else 0x802A3443.toInt(),
                if (isLight) 0xFFE4E4EB.toInt() else 0xCC242D3A.toInt(),
                if (theme == COLORFUL) intArrayOf(0xF2FF6B6B.toInt(), 0xF2C44DFF.toInt(), 0xF23D6BFF.toInt()) else null,
                isLight, effectiveAccent,
                if (isLight) 0xCCFFFFFF.toInt() else 0xAA1D2938.toInt(), 32
            )
        }
    }
}

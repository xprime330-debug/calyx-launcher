package com.calyx.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Small dots under the home pages showing which page you are on. */
class PageDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var count: Int = 1
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var selected: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var dotColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = context.dp(14) * count.coerceAtLeast(1)
        setMeasuredDimension(
            resolveSize(w, widthMeasureSpec),
            resolveSize(context.dp(20), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val step = context.dp(14).toFloat()
        val start = (width - step * count) / 2f + step / 2f
        for (i in 0 until count) {
            paint.color = dotColor
            paint.alpha = if (i == selected) 255 else 110
            val radius = if (i == selected) context.dp(4).toFloat() else context.dp(3).toFloat()
            canvas.drawCircle(start + step * i, height / 2f, radius, paint)
        }
    }
}

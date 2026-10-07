package com.calyx.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Root layout that watches every touch (without stealing it) so swipes and
 * double taps work anywhere on the home screen.
 */
class GestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var swipeUpListener: (() -> Unit)? = null
    var swipeDownListener: (() -> Unit)? = null
    var doubleTapListener: (() -> Unit)? = null
    var gestureStartListener: (() -> Unit)? = null

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                doubleTapListener?.invoke()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                val farEnough = abs(dy) > context.dp(80)
                val mostlyVertical = abs(dy) > abs(dx) * 1.5f
                if (farEnough && mostlyVertical && abs(velocityY) > 600f) {
                    if (dy < 0) swipeUpListener?.invoke() else swipeDownListener?.invoke()
                    return true
                }
                return false
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) gestureStartListener?.invoke()
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}

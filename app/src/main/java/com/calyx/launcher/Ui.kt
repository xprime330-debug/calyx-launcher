package com.calyx.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

object IconShape {
    const val SQUIRCLE = 0
    const val CIRCLE = 1
    const val ROUNDED = 2
    const val TEARDROP = 3
    val names = arrayOf("Squircle", "Circle", "Rounded square", "Teardrop")
}

object PageTransition {
    const val SLIDE = 0
    const val FADE = 1
    const val DEPTH = 2
    val names = arrayOf("Slide", "Fade", "Depth")
}

/** Draws app icons clipped to the selected, original Calyx icon shape. */
class ShapedIcon(private val src: Drawable, private val shape: Int) : Drawable() {
    private val path = Path()
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2F7.toInt() }

    override fun onBoundsChange(bounds: Rect) {
        path.reset()
        val b = RectF(bounds)
        when (shape) {
            IconShape.CIRCLE -> path.addOval(b, Path.Direction.CW)
            IconShape.TEARDROP -> {
                val p = Path()
                p.moveTo(b.centerX(), b.top)
                p.cubicTo(b.right * .98f, b.top, b.right, b.centerY(), b.right, b.centerY())
                p.cubicTo(b.right, b.bottom * .95f, b.left, b.bottom, b.left, b.centerY())
                p.cubicTo(b.left, b.top, b.centerX(), b.top, b.centerX(), b.top)
                p.close(); path.set(p)
            }
            IconShape.ROUNDED -> path.addRoundRect(b, b.width() * .14f, b.width() * .14f, Path.Direction.CW)
            else -> path.addRoundRect(b, b.width() * .24f, b.width() * .24f, Path.Direction.CW)
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val save = canvas.save()
        canvas.clipPath(path)
        if (src is AdaptiveIconDrawable) {
            val e = (b.width() * .25f).toInt()
            val r = Rect(b.left - e, b.top - e, b.right + e, b.bottom + e)
            src.background?.let { it.bounds = r; it.draw(canvas) }
            src.foreground?.let { it.bounds = r; it.draw(canvas) }
        } else {
            canvas.drawRect(RectF(b), backdrop)
            val inset = (b.width() * .14f).toInt()
            src.setBounds(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            src.draw(canvas)
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { src.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { src.colorFilter = colorFilter }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A compact, custom folder preview; style 0 grid, style 1 layered card, style 2 stack. */
class FolderPreviewDrawable(private val icons: List<Drawable>, private val style: Int) : Drawable() {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAE6F2FF.toInt() }
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55243142 }
    override fun draw(canvas: Canvas) {
        val b = bounds
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RectF(b), b.width() * .27f, b.width() * .27f, Path.Direction.CW) })
        canvas.drawRoundRect(RectF(b), b.width() * .27f, b.width() * .27f, bg)
        val count = minOf(icons.size, if (style == 2) 3 else 4)
        if (style == 2) {
            for (i in count - 1 downTo 0) {
                val inset = b.width() * (.12f + i * .08f)
                val rect = Rect(b.left + inset.toInt(), b.top + inset.toInt(), b.right - inset.toInt(), b.bottom - inset.toInt())
                canvas.drawRoundRect(RectF(rect), b.width() * .12f, b.width() * .12f, tile)
                drawIcon(canvas, icons[i], rect)
            }
        } else {
            val inset = b.width() * if (style == 1) .14f else .12f
            val gap = b.width() * .06f
            val cell = (b.width() - 2 * inset - gap) / 2f
            for (i in 0 until count) {
                val row = i / 2; val col = i % 2
                val left = (b.left + inset + col * (cell + gap)).toInt()
                val top = (b.top + inset + row * (cell + gap)).toInt()
                val rect = Rect(left, top, (left + cell).toInt(), (top + cell).toInt())
                if (style == 1) canvas.drawRoundRect(RectF(rect), cell * .24f, cell * .24f, tile)
                drawIcon(canvas, icons[i], rect)
            }
        }
        canvas.restoreToCount(save)
    }
    private fun drawIcon(canvas: Canvas, icon: Drawable, rect: Rect) {
        val old = Rect(icon.bounds); icon.bounds = rect; icon.draw(canvas); icon.bounds = old
    }
    override fun onBoundsChange(bounds: Rect) {}
    override fun setAlpha(alpha: Int) { bg.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { bg.colorFilter = colorFilter }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Backwards-compatible icon cell; callers can opt into a shape and size. */
fun makeAppCell(
    ctx: Context,
    app: AppInfo,
    textColor: Int,
    iconDp: Int,
    labels: Boolean,
    shadow: Boolean,
    shape: Int = IconShape.SQUIRCLE
): LinearLayout {
    val cell = LinearLayout(ctx)
    cell.orientation = LinearLayout.VERTICAL
    cell.gravity = Gravity.CENTER
    val icon = ImageView(ctx)
    icon.scaleType = ImageView.ScaleType.FIT_XY
    icon.contentDescription = app.label
    icon.setImageDrawable(if (app.isFolder) app.icon else ShapedIcon(app.icon, shape))
    cell.addView(icon, LinearLayout.LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp)))
    if (labels) {
        val tv = TextView(ctx)
        tv.text = app.label
        tv.setTextColor(textColor)
        tv.textSize = 11f
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.gravity = Gravity.CENTER
        if (shadow) tv.setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = ctx.dp(5)
        cell.addView(tv, lp)
    }
    return cell
}

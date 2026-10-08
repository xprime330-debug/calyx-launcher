package com.calyx.launcher

import android.content.ClipData
import android.graphics.Color
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import androidx.recyclerview.widget.RecyclerView

/** One page per list of apps; supports long-press drag reordering. */
class HomePagerAdapter(
    private val columns: Int,
    private val rows: Int,
    private val pages: List<List<AppInfo>>,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val onBackgroundLongClick: () -> Unit,
    private val iconSize: Int = 58,
    private val iconShape: Int = IconShape.SQUIRCLE,
    private val onDrop: (String, Int) -> Unit = { _, _ -> },
    private val badgeFor: (String) -> Int = { 0 }
) : RecyclerView.Adapter<HomePagerAdapter.VH>() {

    class VH(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)
    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val frame = FrameLayout(parent.context)
        frame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.setOnLongClickListener { onBackgroundLongClick(); true }
        return VH(frame)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.frame.context
        holder.frame.removeAllViews()
        val grid = GridLayout(ctx).apply {
            columnCount = columns
            rowCount = rows
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        }
        val apps = pages[position]
        val slots = columns * rows
        for (i in 0 until slots) {
            val lp = GridLayout.LayoutParams(GridLayout.spec(i / columns, 1f), GridLayout.spec(i % columns, 1f)).apply {
                width = 0; height = 0
            }
            if (i < apps.size) {
                val app = apps[i]
                val cell = makeAppCell(ctx, app, Color.WHITE, iconSize, labels = true, shadow = true, shape = iconShape, badgeCount = badgeFor(app.component.packageName))
                cell.setOnClickListener { onClick(app) }
                cell.setOnLongClickListener { view ->
                    val clip = ClipData.newPlainText("calyx-app", app.key)
                    @Suppress("DEPRECATION")
                    view.startDragAndDrop(clip, View.DragShadowBuilder(view), app.key, 0)
                    true
                }
                cell.setOnDragListener { _, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                        DragEvent.ACTION_DROP -> {
                            val key = event.localState as? String ?: event.clipData.getItemAt(0).text?.toString()
                            if (key != null) onDrop(key, position * slots + i)
                            true
                        }
                        else -> true
                    }
                }
                grid.addView(cell, lp)
            } else {
                val empty = View(ctx)
                empty.setOnDragListener { _, event ->
                    if (event.action == DragEvent.ACTION_DROP) {
                        val key = event.localState as? String ?: event.clipData.getItemAt(0).text?.toString()
                        if (key != null) onDrop(key, position * slots + i)
                    }
                    true
                }
                grid.addView(empty, lp)
            }
        }
        holder.frame.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                val key = event.localState as? String ?: event.clipData.getItemAt(0).text?.toString()
                if (key != null) onDrop(key, position * slots + apps.size)
            }
            true
        }
        holder.frame.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}

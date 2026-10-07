package com.calyx.launcher

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** App drawer with deterministic filtering and a real empty state for no matches. */
class DrawerAdapter(
    private val all: List<AppInfo>,
    private val textColor: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val iconSize: Int = 56,
    private val iconShape: Int = IconShape.SQUIRCLE
) : RecyclerView.Adapter<DrawerAdapter.VH>() {
    class VH(val box: FrameLayout) : RecyclerView.ViewHolder(box)
    private var shown: List<AppInfo> = all
    private var query: String = ""

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        this.query = query.trim()
        shown = if (this.query.isEmpty()) all else all.filter { it.label.contains(this.query, ignoreCase = true) }
        notifyDataSetChanged()
    }

    fun firstShown(): AppInfo? = shown.firstOrNull()
    override fun getItemCount(): Int = if (shown.isEmpty()) 1 else shown.size
    override fun getItemViewType(position: Int): Int = if (shown.isEmpty()) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val box = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, ctx.dp(10), 0, ctx.dp(10))
        }
        if (viewType == 1) {
            val message = TextView(ctx).apply {
                text = if (query.isEmpty()) "No apps available" else "No apps found for ‘$query’"
                setTextColor(textColor)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(ctx.dp(16), ctx.dp(28), ctx.dp(16), ctx.dp(28))
                contentDescription = text
            }
            box.addView(message, FrameLayout.LayoutParams(-1, -2))
        }
        return VH(box)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.box.removeAllViews()
        if (shown.isEmpty()) {
            val ctx = holder.box.context
            holder.box.addView(TextView(ctx).apply {
                text = if (query.isEmpty()) "No apps available" else "No apps found for ‘$query’"
                setTextColor(textColor); textSize = 15f; gravity = Gravity.CENTER
                setPadding(ctx.dp(16), ctx.dp(28), ctx.dp(16), ctx.dp(28)); contentDescription = text
            }, FrameLayout.LayoutParams(-1, -2))
            return
        }
        val app = shown[position]
        val ctx = holder.box.context
        val cell = makeAppCell(ctx, app, textColor, iconSize, labels = true, shadow = false, shape = iconShape)
        cell.setOnClickListener { onClick(app) }
        cell.setOnLongClickListener { onLongClick(app, cell); true }
        holder.box.addView(cell, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

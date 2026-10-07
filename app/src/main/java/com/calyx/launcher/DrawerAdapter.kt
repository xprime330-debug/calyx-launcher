package com.calyx.launcher

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** App Library rows with category filtering, search and optional frequent-first ranking. */
class DrawerAdapter(
    private val all: List<AppInfo>,
    private val textColor: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val iconSize: Int = 56,
    private val iconShape: Int = IconShape.SQUIRCLE,
    private val countFor: (String) -> Int = { 0 },
    private var frequentFirst: Boolean = false
) : RecyclerView.Adapter<DrawerAdapter.VH>() {
    class VH(val box: FrameLayout) : RecyclerView.ViewHolder(box)
    private var shown: List<AppInfo> = all
    private var query: String = ""
    private var category: String = "All"

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        shown = all.asSequence()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .filter { category == "All" || it.category == category }
            .let { seq -> if (frequentFirst) seq.sortedWith(compareByDescending<AppInfo> { countFor(it.key) }.thenBy { it.label.lowercase() }) else seq.sortedBy { it.label.lowercase() } }
            .toList()
        notifyDataSetChanged()
    }
    @SuppressLint("NotifyDataSetChanged") fun filter(value: String) { query = value.trim(); refresh() }
    @SuppressLint("NotifyDataSetChanged") fun setCategory(value: String) { category = value; refresh() }
    @SuppressLint("NotifyDataSetChanged") fun setFrequentFirst(value: Boolean) { frequentFirst = value; refresh() }
    fun firstShown(): AppInfo? = shown.firstOrNull()
    override fun getItemCount(): Int = if (shown.isEmpty()) 1 else shown.size
    override fun getItemViewType(position: Int): Int = if (shown.isEmpty()) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val box = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, ctx.dp(8), 0, ctx.dp(8))
        }
        return VH(box)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.box.removeAllViews()
        if (shown.isEmpty()) {
            val ctx = holder.box.context
            holder.box.addView(TextView(ctx).apply {
                text = if (query.isBlank()) "Nothing in this category yet" else "No apps found for ‘$query’"
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

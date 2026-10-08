package com.calyx.launcher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.DragEvent
import android.widget.ScrollView
import android.widget.TextView
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.TextClock
import java.util.TimeZone
import java.util.UUID
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var palette: Themes.Palette

    private lateinit var root: GestureLayout
    private lateinit var mainColumn: LinearLayout
    private lateinit var clockPanel: LinearLayout
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var clockEyebrow: TextView
    private lateinit var clockZone: TextView
    private lateinit var notificationButton: TextView
    private lateinit var controlsButton: TextView
    private lateinit var peekButton: TextView
    private lateinit var widgetTrayScroll: View
    private lateinit var widgetTray: LinearLayout
    private lateinit var drawerCategories: LinearLayout
    private lateinit var recentAppsRow: LinearLayout
    private lateinit var recentAppsScroll: View
    private lateinit var recentAppsTitle: TextView
    private lateinit var pager: ViewPager2
    private lateinit var dots: PageDots
    private lateinit var dock: LinearLayout
    private lateinit var drawer: View
    private lateinit var drawerContent: LinearLayout
    private lateinit var search: EditText
    private lateinit var drawerList: RecyclerView
    private lateinit var centersUi: CalyxCenters

    private var allApps: List<AppInfo> = emptyList()
    private var drawerAdapter: DrawerAdapter? = null
    private var drawerOpen = false
    private var listAtTopOnDown = true
    private val appWidgetHost by lazy { AppWidgetHost(this, 0xCA1A) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var weatherValue: WeatherNow? = null
    private var weatherError: String? = null
    private var weatherLastCity: String = ""
    private var pendingWidgetId: Int = -1

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { reloadApps() }
    }
    private val displayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClockZone()
            if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) applyTheme()
            if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) CenterSchedules.refreshAfterClockChange(context)
        }
    }
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refreshBadgeViews() }
    }

    private companion object {
        const val ROWS = 5
        const val REQUEST_WIDGET_PICK = 4301
        const val REQUEST_WIDGET_CONFIGURE = 4302
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        palette = Themes.get(prefs.theme, effectiveAccent(), prefs.styleFamily)

        root = findViewById(R.id.root)
        mainColumn = findViewById(R.id.mainColumn)
        clockPanel = findViewById(R.id.clockPanel)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        clockEyebrow = findViewById(R.id.clockEyebrow)
        clockZone = findViewById(R.id.clockZone)
        notificationButton = findViewById(R.id.notificationButton)
        controlsButton = findViewById(R.id.controlsButton)
        peekButton = findViewById(R.id.peekButton)
        widgetTrayScroll = findViewById(R.id.widgetTrayScroll)
        widgetTray = findViewById(R.id.widgetTray)
        drawerCategories = findViewById(R.id.drawerCategories)
        recentAppsRow = findViewById(R.id.recentAppsRow)
        recentAppsScroll = findViewById(R.id.recentAppsScroll)
        recentAppsTitle = findViewById(R.id.recentAppsTitle)
        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        drawerContent = findViewById(R.id.drawerContent)
        search = findViewById(R.id.search)
        drawerList = findViewById(R.id.drawerList)
        centersUi = CalyxCenters(this, prefs, { palette }, { allApps }, { key -> resolveTile(key) }, { app -> launch(app) }, { refreshBadgeViews() })
        notificationButton.setOnClickListener { centersUi.showNotificationCenter() }
        controlsButton.setOnClickListener { centersUi.showControlCenter() }
        peekButton.setOnClickListener { if (prefs.peekEnabled) centersUi.showPeekPanel() }

        drawerList.layoutManager = GridLayoutManager(this, prefs.drawerColumns)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dots.selected = position
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            mainColumn.setPadding(0, bars.top, 0, bars.bottom)
            drawerContent.setPadding(
                dp(16),
                bars.top + dp(12),
                dp(16),
                maxOf(bars.bottom, ime.bottom)
            )
            insets
        }

        root.gestureStartListener = {
            listAtTopOnDown = !drawerList.canScrollVertically(-1)
        }
        root.swipeUpListener = {
            if (!drawerOpen) openDrawer(false)
        }
        root.swipeDownListener = { handleSwipeDown() }
        root.edgeSwipeListener = { if (!drawerOpen && prefs.peekEnabled) centersUi.showPeekPanel() }
        root.doubleTapListener = {
            if (!drawerOpen) openDrawer(true)
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                drawerAdapter?.filter(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                drawerAdapter?.firstShown()?.let { launch(it) }
                true
            } else {
                false
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val displayFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_WALLPAPER_CHANGED)
        }
        ContextCompat.registerReceiver(this, displayReceiver, displayFilter, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(this, notificationReceiver, IntentFilter(CalyxNotificationService.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

        updateClockZone()
        dock.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                val key = event.localState as? String ?: event.clipData.getItemAt(0).text?.toString()
                if (key != null) moveToDock(key)
            }
            true
        }
        applyTheme()
        reloadApps()
    }

    override fun onResume() {
        super.onResume()
        try { appWidgetHost.startListening() } catch (_: Exception) { }
        updateClockZone()
        applyTheme()
        if (::centersUi.isInitialized) centersUi.onResume()
    }

    override fun onPause() {
        try { appWidgetHost.stopListening() } catch (_: Exception) { }
        super.onPause()
    }

    override fun onDestroy() {
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(displayReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home button pressed while already on home.
        if (drawerOpen) closeDrawer(true) else pager.setCurrentItem(0, true)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer(true) else pager.setCurrentItem(0, true)
    }

    // ---------------------------------------------------------------- theme

    private fun updateClockZone() {
        if (!::clockZone.isInitialized) return
        val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (offset < 0) "−" else "+"
        val absolute = kotlin.math.abs(offset)
        clockZone.text = "UTC $sign${(absolute / 60).toString().padStart(2, '0')}:${(absolute % 60).toString().padStart(2, '0')}"
    }

    private fun applyTheme() {
        palette = Themes.get(prefs.theme, effectiveAccent(), prefs.styleFamily)

        clockPanel.background = palette.surface(resources.displayMetrics.density)
        clock.typeface = android.graphics.Typeface.create(when (prefs.styleFamily) {
            StyleFamily.FLOW -> "sans-serif-medium"
            StyleFamily.DYNAMIC -> "sans-serif"
            else -> "sans-serif-light"
        }, android.graphics.Typeface.NORMAL)
        clock.setTextColor(palette.text)
        date.setTextColor(palette.subtext)
        clockEyebrow.setTextColor(palette.accent)
        clockZone.setTextColor(palette.text)
        if (::notificationButton.isInitialized) notificationButton.setTextColor(palette.text)
        if (::controlsButton.isInitialized) controlsButton.setTextColor(palette.text)
        if (::peekButton.isInitialized) {
            peekButton.setTextColor(palette.text)
            peekButton.visibility = if (prefs.peekEnabled) View.VISIBLE else View.GONE
        }
        drawer.background = palette.drawerBackground(resources.displayMetrics.density)
        drawerContent.background = when (prefs.drawerStyle) {
            1, 3 -> palette.surface(resources.displayMetrics.density)
            else -> palette.drawerBackground(resources.displayMetrics.density)
        }
        drawerContent.clipToOutline = true
        (drawerContent.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            val inset = if (prefs.drawerStyle == 3) dp(14) else 0
            lp.setMargins(inset, if (prefs.drawerStyle == 1) dp(70) else inset, inset, inset)
            if (prefs.drawerStyle == 1) lp.gravity = android.view.Gravity.BOTTOM
            drawerContent.layoutParams = lp
        }

        val searchBg = GradientDrawable()
        searchBg.cornerRadius = dp(16).toFloat()
        searchBg.setColor(palette.search)
        searchBg.cornerRadius = dp(24).toFloat()
        search.background = searchBg
        search.setTextColor(palette.text)
        search.setHintTextColor(palette.subtext)

        val dockBg = GradientDrawable()
        dockBg.cornerRadius = dp(palette.radiusDp).toFloat()
        dockBg.setColor(palette.dock)
        dock.background = dockBg

        dots.dotColor = Color.WHITE

        if (allApps.isNotEmpty()) setupDrawerAdapter()
        refreshWidgetTray()
    }

    /** One-tap family preset; users may fine-tune each setting afterward. */
    private fun applyStyleFamily(family: Int) {
        prefs.styleFamily = family.coerceIn(0, StyleFamily.names.lastIndex)
        when (prefs.styleFamily) {
            StyleFamily.GLASS -> {
                prefs.iconShape = IconShape.SQUIRCLE; prefs.folderStyle = 0; prefs.drawerStyle = 2
                prefs.transition = PageTransition.DEPTH; prefs.drawerBlur = true
            }
            StyleFamily.FLOW -> {
                prefs.iconShape = IconShape.ROUNDED; prefs.folderStyle = 1; prefs.drawerStyle = 1
                prefs.transition = PageTransition.SLIDE; prefs.drawerBlur = false
            }
            else -> {
                prefs.iconShape = IconShape.CIRCLE; prefs.folderStyle = 2; prefs.drawerStyle = 0
                prefs.transition = PageTransition.FADE; prefs.drawerBlur = false
            }
        }
    }

    private fun chooseTheme(theme: Int) {
        prefs.theme = theme
        applyTheme()
    }

    // ----------------------------------------------------------------- data

    private fun reloadApps() {
        allApps = AppLoader.load(this)
        val installedKeys = allApps.map { it.key }.toSet()
        prefs.hiddenApps = prefs.hiddenApps.filter { it in installedKeys }
        prefs.folders = prefs.folders.map { folder -> folder.copy(appKeys = folder.appKeys.filter { it in installedKeys }) }.filter { it.appKeys.isNotEmpty() }
        val folderKeys = prefs.folders.map { "folder:${it.id}" }.toSet()
        prefs.homeApps = prefs.homeApps.filterNot { it.startsWith("folder:") && it !in folderKeys }
        prefs.dockApps = prefs.dockApps.filterNot { it.startsWith("folder:") && it !in folderKeys }
        seedDefaults()
        renderHome()
        renderDock()
        setupDrawerAdapter()
    }

    /** First run only: put sensible apps on the home screen and dock. */
    private fun seedDefaults() {
        if (prefs.seeded || allApps.isEmpty()) return

        fun find(intent: Intent): String? {
            val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: return null
            val pkg = info.activityInfo.packageName
            return allApps.firstOrNull { it.component.packageName == pkg }?.key
        }

        var dockKeys = listOfNotNull(
            find(Intent(Intent.ACTION_DIAL)),
            find(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            find(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            find(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        ).distinct()

        if (dockKeys.isEmpty()) dockKeys = allApps.take(prefs.dockCapacity).map { it.key }

        var homeKeys = listOfNotNull(
            find(Intent(Settings.ACTION_SETTINGS)),
            find(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))),
            find(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            allApps.firstOrNull { it.component.packageName == "com.android.vending" }?.key
        ).distinct().filter { it !in dockKeys }

        if (homeKeys.size < 8) {
            val extra = allApps.map { it.key }.filter { it !in dockKeys && it !in homeKeys }
            homeKeys = homeKeys + extra.take(8 - homeKeys.size)
        }

        prefs.dockApps = dockKeys
        prefs.homeApps = homeKeys
        prefs.seeded = true
    }

    private fun effectiveAccent(): Int? {
        prefs.accentColor.takeIf { it != 0 }?.let { return it }
        if (prefs.styleFamily == StyleFamily.DYNAMIC && Build.VERSION.SDK_INT >= 27) {
            try {
                val manager = getSystemService(Context.WALLPAPER_SERVICE) as android.app.WallpaperManager
                return manager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            } catch (_: Exception) { }
        }
        return null
    }

    private fun resolveTile(key: String): AppInfo? {
        allApps.firstOrNull { it.key == key }?.let { return it }
        if (!key.startsWith("folder:")) return null
        val id = key.removePrefix("folder:")
        val folder = prefs.folders.firstOrNull { it.id == id } ?: return null
        val children = folder.appKeys.filterNot { it in prefs.hiddenApps }.mapNotNull { appKey -> allApps.firstOrNull { it.key == appKey } }
        if (children.isEmpty()) return null
        return AppInfo(folder.name, android.content.ComponentName(packageName, "$packageName.Folder_$id"),
            FolderPreviewDrawable(children.map { it.icon }, prefs.folderStyle), "Folders", id, children.map { it.key })
    }

    // --------------------------------------------------------------- render

    private fun renderHome() {
        val saved = prefs.homeApps
        val resolved = saved.mapNotNull { key -> resolveTile(key) }
        val valid = resolved.map { it.key }
        if (valid != saved) prefs.homeApps = valid

        val perPage = prefs.columns * ROWS
        val pages = resolved
            .chunked(perPage)
            .ifEmpty { listOf(emptyList<AppInfo>()) }

        val keep = pager.currentItem
        pager.adapter = HomePagerAdapter(
            prefs.columns,
            ROWS,
            pages,
            { app -> launch(app) },
            { app, view -> showAppMenu(app, view, fromDrawer = false, inDock = false) },
            { showSettings() },
            prefs.iconSize, prefs.iconShape,
            { key, target -> moveHomeApp(key, target) },
            { pkg -> badgeCount(pkg) }
        )
        pager.setPageTransformer { page, position ->
            when (prefs.transition) {
                PageTransition.FADE -> { page.alpha = (1f - kotlin.math.abs(position)).coerceIn(0f, 1f); page.translationX = 0f }
                PageTransition.DEPTH -> {
                    page.alpha = (1f - kotlin.math.abs(position)).coerceIn(0.35f, 1f)
                    page.translationX = -position * page.width * 0.25f
                    page.scaleX = 1f - kotlin.math.abs(position) * 0.08f
                    page.scaleY = 1f - kotlin.math.abs(position) * 0.08f
                }
                else -> { page.alpha = 1f; page.translationX = 0f; page.scaleX = 1f; page.scaleY = 1f }
            }
        }
        dots.count = pages.size
        val target = keep.coerceAtMost(pages.size - 1)
        pager.setCurrentItem(target, false)
        dots.selected = target
    }

    private fun renderDock() {
        val saved = prefs.dockApps
        val valid = saved.filter { resolveTile(it) != null }
        if (valid != saved) prefs.dockApps = valid

        dock.removeAllViews()
        for (key in valid) {
            val app = resolveTile(key) ?: continue
            val dockIconSize = minOf(prefs.iconSize, if (prefs.dockCapacity >= 7) 42 else if (prefs.dockCapacity >= 6) 46 else if (prefs.dockCapacity >= 5) 52 else prefs.iconSize)
            val cell = makeAppCell(this, app, palette.text, dockIconSize, labels = false, shadow = false, shape = prefs.iconShape, badgeCount = badgeCount(app.component.packageName))
            cell.setOnClickListener { launch(app) }
            cell.setOnLongClickListener {
                showAppMenu(app, cell, fromDrawer = false, inDock = true)
                true
            }
            cell.setOnDragListener { _, event ->
                if (event.action == DragEvent.ACTION_DROP) {
                    val key = event.localState as? String ?: event.clipData.getItemAt(0).text?.toString()
                    if (key != null) moveToDock(key)
                }; true
            }
            dock.addView(
                cell,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun setupDrawerAdapter() {
        val visibleApps = allApps.filterNot { it.key in prefs.hiddenApps }
        val adapter = DrawerAdapter(
            visibleApps,
            palette.text,
            { app -> launch(app) },
            { app, view -> showAppMenu(app, view, fromDrawer = true, inDock = false) },
            prefs.iconSize, prefs.iconShape, { key -> prefs.launchCount(key) }, prefs.frequentFirst,
            { pkg -> badgeCount(pkg) }
        )
        adapter.setCategory(prefs.drawerCategory)
        drawerAdapter = adapter
        drawerList.adapter = adapter
        drawerList.layoutManager = GridLayoutManager(this, prefs.drawerColumns)
        val q = search.text?.toString() ?: ""
        if (q.isNotEmpty()) adapter.filter(q)
        renderCategoryChips()
        renderRecentApps()
    }

    private fun renderRecentApps() {
        recentAppsRow.removeAllViews()
        val recent = prefs.recentApps.mapNotNull { key -> allApps.firstOrNull { it.key == key && key !in prefs.hiddenApps } }.take(8)
        recent.forEach { app ->
            val cell = makeAppCell(this, app, palette.text, 42, labels = true, shadow = false, shape = prefs.iconShape, badgeCount = badgeCount(app.component.packageName)).apply {
                contentDescription = "Recently used ${app.label}"
                setOnClickListener { launch(app) }
            }
            recentAppsRow.addView(cell, LinearLayout.LayoutParams(dp(72), dp(90)).apply { marginEnd = dp(4) })
        }
        val visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recentAppsScroll.visibility = visibility
        recentAppsTitle.visibility = visibility
    }

    private fun renderCategoryChips() {
        drawerCategories.removeAllViews()
        val categories = listOf("All", "Games", "Social", "Media", "Tools", "Sort")
        categories.forEach { category ->
            val selected = if (category == "Sort") prefs.frequentFirst else prefs.drawerCategory == category
            val chip = TextView(this).apply {
                text = if (category == "Sort") if (prefs.frequentFirst) "Frequent ↓" else "A–Z ↑" else category
                textSize = 13f
                setTextColor(if (selected) 0xFF101820.toInt() else palette.text)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(if (selected) palette.accent else palette.search)
                }
                isClickable = true
                contentDescription = if (category == "Sort") "Sort ${text}" else "Filter $category apps"
                setOnClickListener {
                    if (category == "Sort") {
                        prefs.frequentFirst = !prefs.frequentFirst
                        drawerAdapter?.setFrequentFirst(prefs.frequentFirst)
                        renderCategoryChips()
                    } else {
                        prefs.drawerCategory = category
                        drawerAdapter?.setCategory(category)
                        renderCategoryChips()
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
            drawerCategories.addView(chip, lp)
        }
    }

    // --------------------------------------------------------------- drawer

    private fun openDrawer(focusSearch: Boolean) {
        if (drawerOpen) return
        drawerOpen = true
        drawer.animate().cancel()
        drawer.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= 31) {
            mainColumn.setRenderEffect(if (prefs.drawerBlur) RenderEffect.createBlurEffect(dp(18).toFloat(), dp(18).toFloat(), Shader.TileMode.CLAMP) else null)
        }
        drawer.alpha = 0f
        drawer.translationY = root.height * 0.12f
        drawer.animate().alpha(1f).translationY(0f).setDuration((220 * prefs.animationSpeed / 100).toLong()).start()
        if (focusSearch) {
            search.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeDrawer(animate: Boolean) {
        if (!drawerOpen) return
        drawerOpen = false
        hideKeyboard()
        search.setText("")
        if (Build.VERSION.SDK_INT >= 31) mainColumn.setRenderEffect(null)
        drawer.animate().cancel()
        if (animate) {
            drawer.animate()
                .alpha(0f)
                .translationY(root.height * 0.12f)
                .setDuration((180 * prefs.animationSpeed / 100).toLong())
                .withEndAction {
                    if (!drawerOpen) drawer.visibility = View.GONE
                }
                .start()
        } else {
            drawer.visibility = View.GONE
        }
        drawerList.scrollToPosition(0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(search.windowToken, 0)
        search.clearFocus()
    }

    // -------------------------------------------------------------- actions

    private fun launch(app: AppInfo) {
        if (app.isFolder) { showFolderDialog(app); return }
        prefs.incrementLaunch(app.key)
        if (prefs.frequentFirst) drawerAdapter?.setFrequentFirst(true)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
        closeDrawer(false)
    }

    private fun showAppMenu(app: AppInfo, anchor: View, fromDrawer: Boolean, inDock: Boolean) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        if (inDock) menu.add(0, 1, 0, if (app.isFolder) "Remove folder from dock" else "Remove from dock")
        else if (!fromDrawer) {
            menu.add(0, 1, 0, if (app.isFolder) "Dissolve folder" else "Remove from home")
            menu.add(0, 6, 0, "Move icon (drag)")
            if (app.isFolder) menu.add(0, 9, 0, "Rename folder") else menu.add(0, 8, 0, "Create folder")
        } else {
            menu.add(0, 2, 0, "Add to home")
            menu.add(0, 3, 0, "Add to dock")
            menu.add(0, 7, 0, "Hide from Calyx")
        }
        if (!app.isFolder) {
            menu.add(0, 4, 0, "App info")
            addShortcutMenuItems(menu, app)
            menu.add(0, 5, 0, "Uninstall")
        } else if (!fromDrawer) menu.add(0, 10, 0, "Change folder style")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (app.isFolder) {
                    if (inDock) { prefs.dockApps = prefs.dockApps.filterNot { it == app.key }; dissolveFolder(app, true) }
                    else dissolveFolder(app, true)
                } else if (inDock) { prefs.dockApps = prefs.dockApps.filterNot { it == app.key }; renderDock() }
                else { prefs.homeApps = prefs.homeApps.filterNot { it == app.key }; renderHome() }
                2 -> { if (app.key !in prefs.homeApps) prefs.homeApps = prefs.homeApps + app.key; renderHome() }
                3 -> addToDock(app.key)
                4 -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.packageName, null)))
                5 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.packageName)))
                6 -> {
                    @Suppress("DEPRECATION")
                    anchor.startDragAndDrop(ClipData.newPlainText("calyx-app", app.key), View.DragShadowBuilder(anchor), app.key, 0)
                }
                7 -> hideApp(app)
                8 -> createFolder(app)
                9 -> renameFolder(app)
                10 -> chooseFrom("Folder style", arrayOf("Grid mosaic", "Card tiles", "Layered stack")) { prefs.folderStyle = it; refreshUi() }
                in 1000..1999 -> startAppShortcut(app, item.itemId - 1000)
            }
            true
        }
        popup.show()
    }

    private fun showFolderDialog(folder: AppInfo) {
        val children = folder.folderAppKeys.mapNotNull { key -> allApps.firstOrNull { it.key == key } }
        if (children.isEmpty()) { Toast.makeText(this, "This folder is empty", Toast.LENGTH_SHORT).show(); return }
        val columns = when (prefs.folderStyle) { 1 -> 2; 2 -> 1; else -> 3 }
        val grid = android.widget.GridLayout(this).apply { columnCount = columns; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        lateinit var dialog: AlertDialog
        children.forEach { app ->
            val iconSize = if (prefs.folderStyle == 2) 38 else 46
            val cell = makeAppCell(this, app, palette.text, iconSize, labels = true, shadow = false, shape = prefs.iconShape)
            cell.setPadding(dp(4), dp(8), dp(4), dp(8))
            if (prefs.folderStyle != 0) cell.background = palette.surface(resources.displayMetrics.density)
            cell.setOnClickListener { dialog.dismiss(); launch(app) }
            val lp = android.widget.GridLayout.LayoutParams().apply { width = 0; height = dp(if (prefs.folderStyle == 2) 62 else 84); columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3)) }
            grid.addView(cell, lp)
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        dialog = AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(folder.label).setView(scroll)
            .setNeutralButton("Rename") { _, _ -> renameFolder(folder) }
            .setPositiveButton("Done", null).create()
        dialog.show()
    }

    private fun createFolder(first: AppInfo) {
        if (first.isFolder) return
        val candidates = prefs.homeApps.mapNotNull(::resolveTile).filter { !it.isFolder && it.key != first.key }
        if (candidates.isEmpty()) { Toast.makeText(this, "Add another app to the home screen first", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Add ${first.label} to a folder")
            .setItems(candidates.map { it.label }.toTypedArray()) { _, which ->
                val second = candidates[which]
                val nameField = EditText(this).apply { setText("${first.label} + ${second.label}"); selectAll() }
                AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Name your folder").setView(nameField)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Create") { _, _ ->
                        val old = prefs.homeApps.toMutableList()
                        val insertAt = minOf(old.indexOf(first.key).coerceAtLeast(0), old.indexOf(second.key).coerceAtLeast(0))
                        val id = UUID.randomUUID().toString()
                        val name = nameField.text.toString().trim().ifBlank { "Folder" }
                        prefs.folders = prefs.folders + CalyxFolder(id, name, listOf(first.key, second.key))
                        old.removeAll(listOf(first.key, second.key).toSet()); old.add(insertAt.coerceIn(0, old.size), "folder:$id")
                        prefs.homeApps = old; renderHome()
                    }.show()
            }.show()
    }

    private fun renameFolder(folder: AppInfo) {
        val field = EditText(this).apply { setText(folder.label); selectAll() }
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Rename folder").setView(field).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val id = folder.folderId ?: return@setPositiveButton
                prefs.folders = prefs.folders.map { if (it.id == id) it.copy(name = field.text.toString().trim().ifBlank { "Folder" }) else it }
                renderHome(); renderDock()
            }.show()
    }

    private fun dissolveFolder(folder: AppInfo, returnApps: Boolean) {
        val id = folder.folderId ?: return
        val record = prefs.folders.firstOrNull { it.id == id }
        val children = record?.appKeys.orEmpty().filter { key -> allApps.any { it.key == key } }
        val home = prefs.homeApps.toMutableList()
        val oldIndex = home.indexOf(folder.key)
        home.removeAll { it == folder.key }
        if (returnApps) home.addAll((if (oldIndex >= 0) oldIndex else home.size).coerceIn(0, home.size), children.filterNot { it in home })
        prefs.folders = prefs.folders.filterNot { it.id == id }
        prefs.homeApps = home
        prefs.dockApps = prefs.dockApps.filterNot { it == folder.key }
        renderHome(); renderDock()
    }

    private fun hideApp(app: AppInfo) {
        if (app.isFolder) return
        prefs.hiddenApps = (prefs.hiddenApps + app.key).distinct()
        prefs.folders = prefs.folders.map { it.copy(appKeys = it.appKeys.filterNot { key -> key == app.key }) }.filter { it.appKeys.isNotEmpty() }
        val validFolders = prefs.folders.map { "folder:${it.id}" }.toSet()
        prefs.homeApps = prefs.homeApps.filterNot { it == app.key || (it.startsWith("folder:") && it !in validFolders) }
        prefs.dockApps = prefs.dockApps.filterNot { it == app.key }
        renderHome(); renderDock(); setupDrawerAdapter()
        Toast.makeText(this, "Hidden from Calyx. Restore it in Settings → Hidden apps.", Toast.LENGTH_LONG).show()
    }

    private fun manageHiddenApps() {
        if (allApps.isEmpty()) return
        val checked = allApps.map { it.key in prefs.hiddenApps }.toBooleanArray()
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Hidden apps")
            .setMultiChoiceItems(allApps.map { it.label }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val hidden = allApps.filterIndexed { index, _ -> checked[index] }.map { it.key }
                val newlyHidden = hidden - prefs.hiddenApps.toSet()
                prefs.hiddenApps = hidden
                prefs.folders = prefs.folders.map { folder -> folder.copy(appKeys = folder.appKeys.filterNot { it in newlyHidden }) }.filter { it.appKeys.isNotEmpty() }
                val validFolders = prefs.folders.map { "folder:${it.id}" }.toSet()
                prefs.homeApps = prefs.homeApps.filterNot { it in newlyHidden || (it.startsWith("folder:") && it !in validFolders) }
                prefs.dockApps = prefs.dockApps.filterNot { it in newlyHidden }
                refreshUi()
            }.show()
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(14), dp(20), dp(14)) }
        fun section(title: String) { content.addView(TextView(this).apply { text = title.uppercase(); textSize = 11f; letterSpacing = .12f; setTextColor(palette.accent); setPadding(0, dp(14), 0, dp(4)) }) }
        fun row(title: String, summary: String, action: () -> Unit) {
            content.addView(TextView(this).apply {
                text = "$title\n$summary"; textSize = 14f; setTextColor(palette.text); setPadding(dp(12), dp(11), dp(12), dp(11)); isClickable = true; isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        section("Look and feel")
        row("Style family", StyleFamily.names[prefs.styleFamily], { chooseFrom("Style family", StyleFamily.names) { applyStyleFamily(it) } })
        row("Color theme", Themes.names[prefs.theme.coerceIn(0, 2)], { chooseFrom("Color theme", Themes.names) { prefs.theme = it; refreshUi() } })
        row("Accent color", if (prefs.accentColor == 0) "Family default" else "Custom accent", { chooseAccent() })
        row("Icon shape and size", "${IconShape.names[prefs.iconShape.coerceIn(0, IconShape.names.lastIndex)]} · ${prefs.iconSize} dp", { chooseFrom("Icon shape", IconShape.names) { prefs.iconShape = it; refreshUi() } })
        row("Icon size", "${prefs.iconSize} dp", { chooseFrom("Icon size", arrayOf("Small", "Default", "Large", "Extra large")) { prefs.iconSize = listOf(48, 58, 68, 78)[it]; refreshUi() } })
        row("Folder tile style", arrayOf("Grid mosaic", "Card tiles", "Layered stack")[prefs.folderStyle], { chooseFrom("Folder style", arrayOf("Grid mosaic", "Card tiles", "Layered stack")) { prefs.folderStyle = it; refreshUi() } })
        section("Home screen and dock")
        row("Home grid", "${prefs.columns} columns × 5 rows", { chooseFrom("Home grid", (3..8).map { "$it columns" }.toTypedArray()) { prefs.columns = it + 3; renderHome() } })
        row("Dock capacity", "${prefs.dockCapacity} apps", { chooseFrom("Dock capacity", (4..7).map { "$it apps" }.toTypedArray()) { prefs.dockCapacity = it + 4; prefs.dockApps = prefs.dockApps.take(prefs.dockCapacity); renderDock() } })
        row("Page transition", PageTransition.names[prefs.transition.coerceIn(0, 2)], { chooseFrom("Page transition", PageTransition.names) { prefs.transition = it; renderHome() } })
        row("Motion speed", "${prefs.animationSpeed}%", { chooseFrom("Motion speed", arrayOf("Slow", "Relaxed", "Default", "Fast", "Very fast")) { prefs.animationSpeed = listOf(50, 75, 100, 125, 150)[it] } })
        section("App Library")
        row("Drawer presentation", arrayOf("Full screen", "Reachable sheet", "Frosted", "Inset card")[prefs.drawerStyle], { chooseFrom("Drawer presentation", arrayOf("Full screen", "Reachable sheet", "Frosted", "Inset card")) { prefs.drawerStyle = it; applyTheme() } })
        row("Backdrop blur", if (prefs.drawerBlur) "Enabled where Android supports it" else "Disabled", { prefs.drawerBlur = !prefs.drawerBlur; applyTheme() })
        row("App grid", "${prefs.drawerColumns} columns", { chooseFrom("App Library grid", (3..6).map { "$it columns" }.toTypedArray()) { prefs.drawerColumns = it + 3; setupDrawerAdapter() } })
        row("Sort order", if (prefs.frequentFirst) "Frequently used first" else "Alphabetical", { prefs.frequentFirst = !prefs.frequentFirst; setupDrawerAdapter() })
        row("Hidden apps", "${prefs.hiddenApps.size} hidden · tap to manage", { manageHiddenApps() })
        section("Widgets")
        row("Built-in cards", prefs.builtinWidgets.joinToString().ifBlank { "None selected" }, { manageBuiltinWidgets() })
        row("Add Android widget", "Choose a widget published by another app", { addPlatformWidget() })
        row("Weather location", prefs.weatherCity.ifBlank { "Choose a city manually · no location permission" }, { showWeatherCityDialog() })
        section("Backup and privacy")
        row("Export layout and settings", "Create a local .calyxbackup file", { exportBackup() })
        row("Restore layout and settings", "Import a .calyxbackup file", { importBackup() })
        row("Weather data source", "Open-Meteo · city name only; no device location", { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/en/terms"))) })
        section("Centers and privacy")
        row("Permission Center", "Review optional Android access", { centersUi.showPermissionCenter() })
        row("Notification badges", if (prefs.notificationBadgesEnabled) "Show active notification counts on app icons" else "Hidden") {
            prefs.notificationBadgesEnabled = !prefs.notificationBadgesEnabled
            refreshBadgeViews()
        }
        row("Privacy Shield", if (prefs.privacyShield) "Notification message text is hidden in Calyx" else "Notification previews are visible in Calyx") { prefs.privacyShield = !prefs.privacyShield }
        row("Notification history", if (prefs.notificationHistoryEnabled) "On · stored locally, up to 50 entries" else "Off · notification content is not retained") {
            prefs.notificationHistoryEnabled = !prefs.notificationHistoryEnabled
            if (!prefs.notificationHistoryEnabled) NotificationHistory.clear(this)
        }
        row("Muted in Calyx", "${prefs.mutedNotificationPackages.size} apps · Android notifications are unchanged", { centersUi.manageMutedApps() })
        row("Daily digest", if (prefs.digestEnabled) "On · ${String.format(java.util.Locale.getDefault(), "%02d:%02d", prefs.digestMinute / 60, prefs.digestMinute % 60)}" else "Off · count-only reminder, optional", {
            if (prefs.digestEnabled) centersUi.setDigestEnabled(false) else centersUi.configureDigestTimeAndEnable()
        })
        row("Do Not Disturb schedule", if (prefs.dndScheduleEnabled) "On · ${String.format(java.util.Locale.getDefault(), "%02d:%02d–%02d:%02d", prefs.dndStartMinute / 60, prefs.dndStartMinute % 60, prefs.dndEndMinute / 60, prefs.dndEndMinute % 60)}" else "Off · Android policy access required", { centersUi.toggleDndSchedule() })
        row("Peek favorites", "${prefs.peekFavorites.size} selected · local to this device", { centersUi.managePeekFavorites() })
        row("Control Center tiles", "Custom order · ${listOf("Comfortable", "Compact", "Dense")[prefs.controlTileSize]}", { centersUi.manageControlTiles() })
        row("Control tile size", listOf("Comfortable", "Compact", "Dense")[prefs.controlTileSize], { chooseFrom("Control tile size", arrayOf("Comfortable", "Compact", "Dense")) { prefs.controlTileSize = it } })
        row("Control haptics", if (prefs.controlHaptics) "On" else "Off", { prefs.controlHaptics = !prefs.controlHaptics })
        row("Peek Panel", if (prefs.peekEnabled) "Edge swipe and clock-card shortcut enabled" else "Disabled", { prefs.peekEnabled = !prefs.peekEnabled; peekButton.visibility = if (prefs.peekEnabled) View.VISIBLE else View.GONE })
        section("System")
        row("Change wallpaper", "Open Android wallpaper picker", { safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Select wallpaper")) })
        row("Default launcher", "Choose Calyx as your home app", { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) })
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Calyx · Settings").setView(scroll).setNegativeButton("Close", null).show()
    }

    private fun manageBuiltinWidgets() {
        val keys = listOf("clock", "battery", "weather")
        val labels = arrayOf("Clock & date", "Battery", "Weather (Open-Meteo)")
        val checked = keys.map { it in prefs.builtinWidgets }.toBooleanArray()
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Choose built-in cards")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.builtinWidgets = keys.filterIndexed { index, _ -> checked[index] }
                if ("weather" in prefs.builtinWidgets && prefs.weatherCity.isBlank()) showWeatherCityDialog() else refreshWidgetTray()
            }.show()
    }

    private fun showWeatherCityDialog() {
        val input = EditText(this).apply { hint = "City, region or country"; setText(prefs.weatherCity) }
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Weather location").setMessage("Calyx sends this city name to Open-Meteo to look up a forecast. Device location is not used.")
            .setView(input).setNegativeButton("Cancel", null)
            .setPositiveButton("Use city") { _, _ ->
                val city = input.text.toString().trim()
                if (city.length >= 2) { prefs.weatherCity = city; weatherLastCity = ""; weatherValue = null; weatherError = null; refreshWidgetTray() }
                else Toast.makeText(this, "Enter at least two characters", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun addPlatformWidget() {
        val id = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        try { startActivityForResult(intent, REQUEST_WIDGET_PICK) }
        catch (_: Exception) { pendingWidgetId = -1; appWidgetHost.deleteAppWidgetId(id); Toast.makeText(this, "No widget picker is available", Toast.LENGTH_SHORT).show() }
    }

    private fun finishWidgetSelection(id: Int) {
        val info = try { appWidgetManager.getAppWidgetInfo(id) } catch (_: Exception) { null }
        if (id < 0 || info == null) { if (id >= 0) appWidgetHost.deleteAppWidgetId(id); pendingWidgetId = -1; return }
        val configure = info.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            try { startActivityForResult(intent, REQUEST_WIDGET_CONFIGURE); return }
            catch (_: Exception) { Toast.makeText(this, "Widget configuration unavailable; adding it with defaults", Toast.LENGTH_SHORT).show() }
        }
        saveHostedWidget(id)
    }

    private fun saveHostedWidget(id: Int) {
        prefs.appWidgetIds = (prefs.appWidgetIds + id).distinct()
        pendingWidgetId = -1
        refreshWidgetTray()
    }

    private fun refreshWidgetTray() {
        if (!::widgetTray.isInitialized || !::widgetTrayScroll.isInitialized) return
        widgetTray.removeAllViews()
        prefs.builtinWidgets.forEach { kind ->
            when (kind) {
                "clock" -> {
                    val card = widgetCard("LOCAL TIME", "Clock & date")
                    card.addView(TextClock(this).apply { format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 31f; setTextColor(palette.text); typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) })
                    card.addView(TextClock(this).apply { format12Hour = "EEE, d MMM"; format24Hour = "EEE, d MMM"; textSize = 12f; setTextColor(palette.subtext) })
                    widgetTray.addView(card, widgetLayoutParams())
                }
                "battery" -> {
                    val card = widgetCard("POWER", "Battery")
                    val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = status?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = status?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                    card.addView(TextView(this).apply { text = if (percent >= 0) "$percent%" else "—"; textSize = 30f; setTextColor(palette.text); typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) })
                    card.addView(TextView(this).apply { text = "Device battery"; textSize = 11f; setTextColor(palette.subtext) })
                    widgetTray.addView(card, widgetLayoutParams())
                }
                "weather" -> addWeatherWidget()
            }
        }
        prefs.appWidgetIds.forEach { id ->
            try {
                val info = appWidgetManager.getAppWidgetInfo(id) ?: return@forEach
                val hosted = appWidgetHost.createView(this, id, info)
                hosted.setAppWidget(id, info)
                hosted.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle("Remove widget?").setMessage(info.loadLabel(packageManager))
                        .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                            appWidgetHost.deleteAppWidgetId(id); prefs.appWidgetIds = prefs.appWidgetIds.filterNot { it == id }; refreshWidgetTray()
                        }.show(); true
                }
                widgetTray.addView(hosted, widgetLayoutParams())
            } catch (_: Exception) { }
        }
        widgetTrayScroll.visibility = if (widgetTray.childCount == 0) View.GONE else View.VISIBLE
    }

    private fun widgetLayoutParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(248), dp(122)).apply { marginEnd = dp(10) }

    private fun widgetCard(eyebrow: String, description: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_VERTICAL
            background = palette.surface(resources.displayMetrics.density); setPadding(dp(16), dp(10), dp(16), dp(10)); isClickable = true; isFocusable = true
            contentDescription = "$description widget"
        }
        card.addView(TextView(this).apply { text = eyebrow; textSize = 9f; letterSpacing = .14f; setTextColor(palette.accent); setPadding(0, 0, 0, dp(4)) })
        return card
    }

    private fun addWeatherWidget() {
        val card = widgetCard("LIVE WEATHER · OPEN-METEO", "Weather")
        val place = TextView(this).apply { text = prefs.weatherCity.ifBlank { "Choose a city in Settings" }; textSize = 11f; setTextColor(palette.subtext) }
        val conditions = TextView(this).apply { textSize = 20f; setTextColor(palette.text); text = weatherValue?.let { "${it.temperatureC.toInt()}°  ${it.description}" } ?: weatherError ?: "Tap to load weather" }
        card.addView(place); card.addView(conditions)
        card.setOnClickListener {
            if (prefs.weatherCity.isBlank()) showWeatherCityDialog() else fetchWeather(conditions, place)
        }
        card.setOnLongClickListener { showWeatherCityDialog(); true }
        widgetTray.addView(card, widgetLayoutParams())
        if (prefs.weatherCity.isNotBlank() && (weatherLastCity != prefs.weatherCity || (weatherValue == null && weatherError == null))) fetchWeather(conditions, place)
    }

    private fun fetchWeather(conditions: TextView, place: TextView) {
        val city = prefs.weatherCity
        if (city.isBlank()) { showWeatherCityDialog(); return }
        conditions.text = "Updating…"; place.text = city
        weatherLastCity = city; weatherError = null
        Thread {
            try {
                val result = WeatherClient.fetch(city)
                runOnUiThread { if (prefs.weatherCity == city) { weatherValue = result; weatherError = null; place.text = result.location; conditions.text = "${result.temperatureC.toInt()}°C  ·  ${result.description}" } }
            } catch (e: Exception) {
                runOnUiThread { if (prefs.weatherCity == city) { weatherError = "Weather unavailable · tap to retry"; conditions.text = weatherError; place.text = city } }
            }
        }.start()
    }

    private fun chooseFrom(title: String, options: Array<String>, apply: (Int) -> Unit) {
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(title).setItems(options) { _, which -> apply(which); refreshUi() }.show()
    }

    private fun chooseAccent() {
        val names = arrayOf("Use theme default", "Lavender", "Ocean", "Mint", "Coral", "Sunshine")
        val colors = intArrayOf(0, 0xFF9B8CFF.toInt(), 0xFF4D9DE0.toInt(), 0xFF4CB782.toInt(), 0xFFFF7A6B.toInt(), 0xFFFFC857.toInt())
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Accent color").setItems(names) { _, which -> prefs.accentColor = colors[which]; applyTheme() }.show()
    }

    private fun refreshUi() { renderHome(); renderDock(); setupDrawerAdapter(); applyTheme() }

    private fun moveHomeApp(key: String, target: Int) {
        val list = prefs.homeApps.toMutableList()
        val from = list.indexOf(key)
        if (from < 0) return
        list.removeAt(from)
        list.add(target.coerceIn(0, list.size), key)
        prefs.homeApps = list
        renderHome()
    }

    private fun addToDock(key: String) {
        val app = resolveTile(key) ?: return
        val current = prefs.dockApps
        if (key in current) return
        if (current.size >= prefs.dockCapacity) Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
        else { prefs.dockApps = current + key; renderDock(); Toast.makeText(this, "Added ${app.label} to dock", Toast.LENGTH_SHORT).show() }
    }

    private fun moveToDock(key: String) {
        if (key !in prefs.homeApps) { addToDock(key); return }
        if (prefs.dockApps.size >= prefs.dockCapacity && key !in prefs.dockApps) {
            Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.homeApps = prefs.homeApps.filterNot { it == key }
        if (key !in prefs.dockApps) prefs.dockApps = prefs.dockApps + key
        renderHome()
        renderDock()
    }

    private fun addShortcutMenuItems(menu: android.view.Menu, app: AppInfo) {
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().setPackage(app.component.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            launcherApps.getShortcuts(query, android.os.Process.myUserHandle()).orEmpty().take(5).forEachIndexed { index, shortcut ->
                menu.add(0, 1000 + index, 0, "Shortcut: ${shortcut.shortLabel}")
            }
        } catch (_: Exception) { }
    }

    private fun startAppShortcut(app: AppInfo, index: Int) {
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().setPackage(app.component.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val shortcut = launcherApps.getShortcuts(query, android.os.Process.myUserHandle()).orEmpty().getOrNull(index) ?: return
            launcherApps.startShortcut(shortcut.`package`, shortcut.id, null, null, android.os.Process.myUserHandle())
        } catch (_: Exception) { Toast.makeText(this, "Shortcut unavailable", Toast.LENGTH_SHORT).show() }
    }

    private fun exportBackup() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.calyx.backup")
            .putExtra(Intent.EXTRA_TITLE, "calyx-layout.calyxbackup")
        startActivityForResult(i, 4101)
    }

    private fun importBackup() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, 4102)
    }

    @Deprecated("Handled for Android 8 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WIDGET_PICK) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == RESULT_OK && id >= 0) finishWidgetSelection(id)
            else { if (id >= 0) appWidgetHost.deleteAppWidgetId(id); pendingWidgetId = -1 }
            return
        }
        if (requestCode == REQUEST_WIDGET_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == RESULT_OK && id >= 0) saveHostedWidget(id)
            else { if (id >= 0) appWidgetHost.deleteAppWidgetId(id); pendingWidgetId = -1 }
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 4101) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("CALYX_BACKUP_V1\n" + prefs.export()) }
            if (requestCode == 4102) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (!raw.startsWith("CALYX_BACKUP_V1\n") || !prefs.import(raw.substringAfter('\n'))) throw IllegalArgumentException()
                CenterSchedules.refresh(this)
                refreshUi(); Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { Toast.makeText(this, "Could not read or write backup", Toast.LENGTH_LONG).show() }
    }

    private fun handleSwipeDown() {
        if (drawerOpen) {
            if (listAtTopOnDown) closeDrawer(true)
        } else {
            centersUi.showNotificationCenter()
        }
    }

    private fun badgeCount(packageName: String): Int =
        if (prefs.notificationBadgesEnabled && packageName !in prefs.mutedNotificationPackages) CalyxNotificationService.countFor(this, packageName) else 0

    private fun refreshBadgeViews() {
        if (!::prefs.isInitialized || !::pager.isInitialized) return
        pager.adapter?.notifyDataSetChanged()
        renderDock(); drawerAdapter?.refreshBadges(); renderRecentApps()
        val count = if (prefs.notificationBadgesEnabled && CalyxNotificationService.hasAccess(this)) CalyxNotificationService.snapshot().count { it.packageName !in prefs.mutedNotificationPackages } else 0
        notificationButton.text = if (count > 0) "ALERTS $count" else "ALERTS"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::centersUi.isInitialized) centersUi.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}

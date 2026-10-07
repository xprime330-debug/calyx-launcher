package com.calyx.launcher

import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var pager: ViewPager2
    private lateinit var dots: PageDots
    private lateinit var dock: LinearLayout
    private lateinit var drawer: View
    private lateinit var drawerContent: LinearLayout
    private lateinit var search: EditText
    private lateinit var drawerList: RecyclerView

    private var allApps: List<AppInfo> = emptyList()
    private var drawerAdapter: DrawerAdapter? = null
    private var drawerOpen = false
    private var listAtTopOnDown = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadApps()
        }
    }

    private companion object {
        const val ROWS = 5
        const val DOCK_MAX = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        palette = Themes.get(prefs.theme, prefs.accentColor.takeIf { it != 0 })

        root = findViewById(R.id.root)
        mainColumn = findViewById(R.id.mainColumn)
        pager = findViewById(R.id.pager)
        dots = findViewById(R.id.dots)
        dock = findViewById(R.id.dock)
        drawer = findViewById(R.id.drawer)
        drawerContent = findViewById(R.id.drawerContent)
        search = findViewById(R.id.search)
        drawerList = findViewById(R.id.drawerList)

        drawerList.layoutManager = GridLayoutManager(this, 4)

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

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
        }
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

    private fun applyTheme() {
        palette = Themes.get(prefs.theme, prefs.accentColor.takeIf { it != 0 })

        drawer.background = palette.drawerBackground()

        val searchBg = GradientDrawable()
        searchBg.cornerRadius = dp(16).toFloat()
        searchBg.setColor(palette.search)
        search.background = searchBg
        search.setTextColor(palette.text)
        search.setHintTextColor(palette.subtext)

        val dockBg = GradientDrawable()
        dockBg.cornerRadius = dp(30).toFloat()
        dockBg.setColor(palette.dock)
        dock.background = dockBg

        dots.dotColor = Color.WHITE

        if (allApps.isNotEmpty()) setupDrawerAdapter()
    }

    private fun chooseTheme(theme: Int) {
        prefs.theme = theme
        applyTheme()
    }

    // ----------------------------------------------------------------- data

    private fun reloadApps() {
        allApps = AppLoader.load(this)
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

        if (dockKeys.isEmpty()) dockKeys = allApps.take(DOCK_MAX).map { it.key }

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

    // --------------------------------------------------------------- render

    private fun renderHome() {
        val byKey = allApps.associateBy { it.key }
        val saved = prefs.homeApps
        val valid = saved.filter { byKey.containsKey(it) }
        if (valid.size != saved.size) prefs.homeApps = valid

        val perPage = prefs.columns * ROWS
        val pages = valid.mapNotNull { byKey[it] }
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
            { key, target -> moveHomeApp(key, target) }
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
        val byKey = allApps.associateBy { it.key }
        val saved = prefs.dockApps
        val valid = saved.filter { byKey.containsKey(it) }
        if (valid.size != saved.size) prefs.dockApps = valid

        dock.removeAllViews()
        for (key in valid) {
            val app = byKey[key] ?: continue
            val cell = makeAppCell(this, app, palette.text, prefs.iconSize, labels = false, shadow = false, shape = prefs.iconShape)
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
        val adapter = DrawerAdapter(
            allApps,
            palette.text,
            { app -> launch(app) },
            { app, view -> showAppMenu(app, view, fromDrawer = true, inDock = false) },
            prefs.iconSize, prefs.iconShape
        )
        drawerAdapter = adapter
        drawerList.adapter = adapter
        val q = search.text?.toString() ?: ""
        if (q.isNotEmpty()) adapter.filter(q)
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
        if (inDock) {
            menu.add(0, 1, 0, "Remove from dock")
        } else if (!fromDrawer) {
            menu.add(0, 1, 0, "Remove from home")
        } else {
            menu.add(0, 2, 0, "Add to home")
            menu.add(0, 3, 0, "Add to dock")
        }
        if (!fromDrawer) menu.add(0, 6, 0, "Move icon (drag)")
        menu.add(0, 4, 0, "App info")
        addShortcutMenuItems(menu, app)
        menu.add(0, 5, 0, "Uninstall")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (inDock) {
                        prefs.dockApps = prefs.dockApps.filter { it != app.key }
                        renderDock()
                    } else {
                        prefs.homeApps = prefs.homeApps.filter { it != app.key }
                        renderHome()
                    }
                }
                2 -> {
                    if (!prefs.homeApps.contains(app.key)) {
                        prefs.homeApps = prefs.homeApps + app.key
                        renderHome()
                    }
                    Toast.makeText(this, "Added to home", Toast.LENGTH_SHORT).show()
                }
                3 -> {
                    val current = prefs.dockApps
                    if (current.contains(app.key)) {
                        Toast.makeText(this, "Already in the dock", Toast.LENGTH_SHORT).show()
                    } else if (current.size >= DOCK_MAX) {
                        Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.dockApps = current + app.key
                        renderDock()
                        Toast.makeText(this, "Added to dock", Toast.LENGTH_SHORT).show()
                    }
                }
                6 -> {
                    @Suppress("DEPRECATION")
                    anchor.startDragAndDrop(ClipData.newPlainText("calyx-app", app.key), View.DragShadowBuilder(anchor), app.key, 0)
                }
                in 1000..1999 -> startAppShortcut(app, item.itemId - 1000)
                4 -> {
                    val i = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.component.packageName, null)
                    )
                    safeStart(i)
                }
                5 -> {
                    val i = Intent(
                        Intent.ACTION_DELETE,
                        Uri.parse("package:" + app.component.packageName)
                    )
                    safeStart(i)
                }
            }
            true
        }
        popup.show()
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(14), dp(20), dp(14)) }
        fun section(title: String) {
            content.addView(TextView(this).apply { text = title.uppercase(); textSize = 12f; setTextColor(palette.accent); setPadding(0, dp(14), 0, dp(4)) })
        }
        fun row(title: String, summary: String, action: () -> Unit) {
            content.addView(TextView(this).apply {
                text = "$title\n$summary"; textSize = 15f; setTextColor(palette.text); setPadding(dp(12), dp(12), dp(12), dp(12)); isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        section("Appearance")
        row("Theme", Themes.names[prefs.theme.coerceIn(0, 2)], { chooseFrom("Theme", Themes.names) { prefs.theme = it; applyTheme() } })
        row("Accent color", if (prefs.accentColor == 0) "Theme default" else "Custom accent", { chooseAccent() })
        row("Icon shape", IconShape.names[prefs.iconShape.coerceIn(0, IconShape.names.lastIndex)], { chooseFrom("Icon shape", IconShape.names) { prefs.iconShape = it; refreshUi() } })
        row("Icon size", "${prefs.iconSize} dp", { chooseFrom("Icon size", arrayOf("Small", "Default", "Large", "Extra large")) { prefs.iconSize = listOf(48, 58, 68, 78)[it]; refreshUi() } })
        row("Drawer background", if (prefs.drawerBlur) "Blur where supported" else "Solid", { prefs.drawerBlur = !prefs.drawerBlur; applyTheme() })
        section("Home screen")
        row("Grid columns", "${prefs.columns} columns × 5 rows", { chooseFrom("Grid columns", (3..8).map { "$it columns" }.toTypedArray()) { prefs.columns = it + 3; renderHome() } })
        row("Page transition", PageTransition.names[prefs.transition.coerceIn(0, 2)], { chooseFrom("Page transition", PageTransition.names) { prefs.transition = it; renderHome() } })
        row("Animation speed", "${prefs.animationSpeed}%", { chooseFrom("Animation speed", arrayOf("Slow", "Relaxed", "Default", "Fast", "Very fast")) { prefs.animationSpeed = listOf(50, 75, 100, 125, 150)[it] } })
        section("Backup and system")
        row("Export layout & settings", "Save a local .calyxbackup file", { exportBackup() })
        row("Restore layout & settings", "Import a .calyxbackup file", { importBackup() })
        row("Change wallpaper", "Open Android wallpaper picker", { safeStart(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Select wallpaper")) })
        row("Default launcher", "Choose Calyx as your home app", { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) })
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this, if (palette.lightDialog) AlertDialog.THEME_DEVICE_DEFAULT_LIGHT else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Calyx Settings")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
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
        val app = allApps.firstOrNull { it.key == key } ?: return
        val current = prefs.dockApps
        if (key in current) return
        if (current.size >= DOCK_MAX) Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
        else { prefs.dockApps = current + key; renderDock(); Toast.makeText(this, "Added ${app.label} to dock", Toast.LENGTH_SHORT).show() }
    }

    private fun moveToDock(key: String) {
        if (key !in prefs.homeApps) { addToDock(key); return }
        if (prefs.dockApps.size >= DOCK_MAX && key !in prefs.dockApps) {
            Toast.makeText(this, "Dock is full", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.homeApps = prefs.homeApps.filterNot { it == key }
        if (key !in prefs.dockApps) prefs.dockApps = prefs.dockApps + key
        renderHome()
        renderDock()
    }

    private fun addShortcutMenuItems(menu: android.view.Menu, app: AppInfo) {
        if (Build.VERSION.SDK_INT < 25) return
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
        if (Build.VERSION.SDK_INT < 25) return
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
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 4101) contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write("CALYX_BACKUP_V1\n" + prefs.export()) }
            if (requestCode == 4102) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                if (!raw.startsWith("CALYX_BACKUP_V1\n") || !prefs.import(raw.substringAfter('\n'))) throw IllegalArgumentException()
                refreshUi(); Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) { Toast.makeText(this, "Could not read or write backup", Toast.LENGTH_LONG).show() }
    }

    private fun handleSwipeDown() {
        if (drawerOpen) {
            if (listAtTopOnDown) closeDrawer(true)
        } else {
            expandNotifications()
        }
    }

    /** Swipe down on the home screen pulls down the notification panel. */
    private fun expandNotifications() {
        try {
            val service = getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(service)
        } catch (e: Exception) {
            // Not allowed on this phone; ignore.
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}

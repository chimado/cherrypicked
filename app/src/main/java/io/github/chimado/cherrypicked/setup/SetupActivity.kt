package io.github.chimado.cherrypicked.setup

import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.a11y.KeyLearn
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.health.HomeHealth
import io.github.chimado.cherrypicked.home.AppEntry
import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.keys.KeyNames
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.ConfigStore
import java.io.IOException
import kotlin.math.max

private const val PKG = "io.github.chimado.cherrypicked"
private const val DIM = 0x99E9EAEE.toInt()
private const val FOCUS_BG = 0x33E0233F

/** [id]: a stable focus id for an actionable row, `#key` for a section header (the target of a "section" extra). */
private class Row(val text: String, val id: String? = null, val onClick: (() -> Unit)? = null) {
    val section get() = id?.startsWith("#") == true
}

/** A sub-screen; leaving it focuses [opener], or the first row of [section] when that row is gone. */
private sealed class Sub(val opener: String, val section: String) {
    class FavActions(val pkg: String) : Sub("fav:$pkg", "favorites")
    object PickFavorite : Sub("add-fav", "favorites")
    object PickMain : Sub("main", "autoopen")
}

/** Plain list setup screen in its own `:setup` process; every change is saved at once. */
class SetupActivity : Activity() {
    private lateinit var store: ConfigStore
    private lateinit var config: Config
    private lateinit var apps: List<AppEntry>
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var regular: Typeface
    private var vw = 0f
    private var sub: Sub? = null
    private var note: String? = null
    private var usageNote = false
    private var started = false
    private var learnText: String? = null

    private val learnRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val k = i.getIntExtra(KeyLearn.EXTRA_KEY, -1)
            learnText = if (k < 0) "No button seen" else "You pressed: ${KeyNames.label(k)} ($k)"
            render(focusedId(), null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(filesDir)
        config = store.load()
        apps = AppCatalog(this).apps().sortedBy { it.label.lowercase() }
        val b = windowManager.currentWindowMetrics.bounds
        vw = max(b.width(), b.height()) / 100f
        regular = Typeface.createFromAsset(assets, "Inter-Regular.ttf")
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((8 * vw).toInt(), (5 * vw).toInt(), (8 * vw).toInt(), (5 * vw).toInt()) }
        scroll = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM))
            addView(list)
        }
        setContentView(scroll)
        registerReceiver(learnRx, IntentFilter(KeyLearn.ACTION_RESULT), RECEIVER_NOT_EXPORTED)
        render(null, intent.getStringExtra("section"))
    }

    override fun onResume() {
        super.onResume()
        // Returning from a system settings screen: refresh the statuses.
        if (started) render(focusedId(), null) else started = true // first resume keeps the "section" focus
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = sub ?: return finish()
        sub = null
        render(s.opener, s.section)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(learnRx)
        Process.killProcess(Process.myPid()) // alone in :setup; also covers leaving with Home, not only finish()
    }

    private fun edit(edit: (Config) -> Config) {
        config = edit(config)
        note = try { store.save(config); null } catch (_: IOException) { "Couldn't save" }
    }

    private fun label(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun rows(): List<Row> = when (val s = sub) {
        is Sub.FavActions -> listOf(
            Row(label(s.pkg)),
            Row("Move up", "up") { edit { it.moveFavorite(s.pkg, -1) } },
            Row("Move down", "down") { edit { it.moveFavorite(s.pkg, 1) } },
            Row("Remove", "remove") { edit { it.removeFavorite(s.pkg) }; sub = null },
        )
        Sub.PickFavorite -> listOf(Row("Add favorite")) +
            apps.filter { it.pkg !in config.favorites && it.pkg !in config.hidden }.map { a ->
                Row(a.label, "pick:${a.pkg}") { edit { it.addFavorite(a.pkg) }; sub = null }
            }
        Sub.PickMain -> listOf(Row("Main app")) +
            apps.map { a -> Row(a.label, "pick:${a.pkg}") { edit { it.setMainApp(a.pkg) }; sub = null } }
        null -> mainRows()
    }

    private fun mainRows(): List<Row> = buildList {
        note?.let { add(Row(it)) }
        add(Row("Favorites", "#favorites"))
        config.favorites.forEach { p -> add(Row(label(p), "fav:$p") { sub = Sub.FavActions(p) }) }
        add(if (config.favorites.size >= HomeModel.MAX_FAVORITES) Row("Add favorite... (5 of 5)")
            else Row("Add favorite...", "add-fav") { sub = Sub.PickFavorite })

        add(Row("Auto-open", "#autoopen"))
        add(if (config.mainApp == null) Row("Open main app on start: set a main app first")
            else Row("Open main app on start: " + if (config.autoOpen) "On" else "Off", "autoopen") { edit { it.toggleAutoOpen() } })
        add(Row("Main app: " + (config.mainApp?.let(::label) ?: "none"), "main") { sub = Sub.PickMain })

        add(Row("Hidden apps", "#hidden"))
        apps.forEach { a -> add(Row((if (a.pkg in config.hidden) "✓  " else "     ") + a.label, "hide:${a.pkg}") { edit { it.toggleHidden(a.pkg) } }) }

        add(Row("Home button", "#home"))
        if (HomeHealth.isEnabled(this@SetupActivity)) add(Row("Connected")) else {
            add(Row("Not connected"))
            add(Row("1. Settings > Accessibility > Cherrypicked > On"))
            add(Row("2. If that is greyed out, run from a computer: adb shell settings put secure enabled_accessibility_services <current list>:$PKG/.a11y.CpAccessibilityService"))
            add(Row("3. On TCL TVs also allow auto-start: adb shell appops set $PKG APP_AUTO_START allow"))
        }
        add(if (!HomeHealth.isEnabled(this@SetupActivity)) Row("Button test: Needs the Home button connected")
            else Row(learnText ?: "Button test", "learn") {
                learnText = "Press any button on your remote…"
                sendBroadcast(Intent(KeyLearn.ACTION_START).setPackage(PKG))
            })
        add(Row("Open accessibility settings", "a11y") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) })

        add(Row("Usage access", "#usage"))
        add(Row(if (usageAllowed()) "Allowed" else "Not allowed"))
        add(Row("Lets Cherrypicked rank apps by how much you watch them, not only by launches from Cherrypicked."))
        add(Row("Open usage access settings", "usage") { usageNote = !open(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
        if (usageNote) add(Row("No settings screen for this here. From a computer run: adb shell appops set $PKG GET_USAGE_STATS allow"))
    }

    @Suppress("DEPRECATION")
    private fun usageAllowed() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED

    private fun open(action: String): Boolean = try {
        startActivity(Intent(action)); true
    } catch (_: ActivityNotFoundException) { false }

    private fun focusedId() = currentFocus?.tag as? String

    /** Runs a row's action, then re-renders with focus on the same row, the new sub-screen's first row, or the opener. */
    private fun click(r: Row) {
        val before = sub
        val id = focusedId()
        r.onClick?.invoke()
        val now = sub
        when {
            now === before -> render(id, null)
            now == null -> render(before!!.opener, before.section)
            else -> render(if (now is Sub.PickMain) config.mainApp?.let { "pick:$it" } else null, null)
        }
    }

    private fun render(focusId: String?, section: String?) {
        list.removeAllViews()
        val rows = rows()
        rows.forEach { list.addView(row(it)) }
        val i = focusTarget(rows.map { if (it.onClick != null || it.section) it.id else null }, focusId, section)
        val target = list.getChildAt(i) ?: return
        target.post { target.requestFocus(); target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height)) }
    }

    private fun row(r: Row) = TextView(this).apply {
        text = r.text
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, vw * if (r.section) 1.4f else 1.7f)
        setTextColor(if (r.section) Theme.ACCENT else if (r.onClick == null) DIM else Theme.TEXT)
        setPadding((1.5 * vw).toInt(), (if (r.section) 2.2 * vw else 0.9 * vw).toInt(), (1.5 * vw).toInt(), (0.9 * vw).toInt())
        if (r.onClick != null) {
            tag = r.id
            isFocusable = true
            setOnClickListener { click(r) }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) GradientDrawable().apply { setColor(FOCUS_BG); setStroke(0, 0) } else ColorDrawable(0)
                (v as TextView).setTextColor(if (f) Theme.ACCENT else Theme.TEXT)
            }
        }
    }
}

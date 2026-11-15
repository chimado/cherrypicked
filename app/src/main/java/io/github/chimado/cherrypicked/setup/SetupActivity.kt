package io.github.chimado.cherrypicked.setup

import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.home.AppEntry
import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.ConfigStore
import java.io.IOException
import kotlin.math.max

private const val PKG = "io.github.chimado.cherrypicked"
private const val DIM = 0x99E9EAEE.toInt()
private const val FOCUS_BG = 0x33E0233F

private class Row(val text: String, val onClick: (() -> Unit)? = null, val header: Boolean = false, val key: String? = null)

private sealed interface Sub {
    class FavActions(val pkg: String) : Sub
    object PickFavorite : Sub
    object PickMain : Sub
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
    private var started = false

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
        render(intent.getStringExtra("section"))
    }

    override fun onResume() {
        super.onResume()
        // Returning from a system settings screen: refresh the statuses.
        if (started) render(null, keepFocus = true) else started = true // first resume keeps the "section" focus
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sub != null) { sub = null; render(null) } else finish()
    }

    override fun finish() {
        super.finish()
        Process.killProcess(Process.myPid()) // alone in :setup
    }

    private fun edit(edit: (Config) -> Config) {
        config = edit(config)
        note = try { store.save(config); null } catch (_: IOException) { "Couldn't save" }
    }

    private fun label(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun rows(): List<Row> = when (val s = sub) {
        is Sub.FavActions -> listOf(
            Row(label(s.pkg), header = true),
            Row("Move up", { edit { it.moveFavorite(s.pkg, -1) } }),
            Row("Move down", { edit { it.moveFavorite(s.pkg, 1) } }),
            Row("Remove", { edit { it.removeFavorite(s.pkg) }; sub = null }),
        )
        Sub.PickFavorite -> listOf(Row("Add favorite", header = true)) +
            apps.filter { it.pkg !in config.favorites }.map { a ->
                Row(a.label, { edit { it.addFavorite(a.pkg) }; sub = null })
            }
        Sub.PickMain -> listOf(Row("Main app", header = true)) +
            apps.map { a -> Row(a.label, { edit { it.setMainApp(a.pkg) }; sub = null }) }
        null -> mainRows()
    }

    private fun mainRows(): List<Row> = buildList {
        note?.let { add(Row(it, header = true)) }
        add(Row("Favorites", header = true, key = "favorites"))
        config.favorites.forEach { p -> add(Row(label(p), { sub = Sub.FavActions(p) })) }
        add(if (config.favorites.size >= HomeModel.MAX_FAVORITES) Row("Add favorite... (5 of 5)", header = true)
            else Row("Add favorite...", { sub = Sub.PickFavorite }))

        add(Row("Auto-open", header = true, key = "autoopen"))
        add(if (config.mainApp == null) Row("Open main app on start: set a main app first", header = true)
            else Row("Open main app on start: " + if (config.autoOpen) "On" else "Off", { edit { it.toggleAutoOpen() } }))
        add(Row("Main app: " + (config.mainApp?.let(::label) ?: "none"), { sub = Sub.PickMain }))

        add(Row("Hidden apps", header = true, key = "hidden"))
        apps.forEach { a -> add(Row((if (a.pkg in config.hidden) "✓  " else "     ") + a.label, { edit { it.toggleHidden(a.pkg) } })) }

        add(Row("Home button", header = true, key = "home"))
        if (isHomeConnected()) add(Row("Connected", header = true)) else {
            add(Row("Not connected", header = true))
            add(Row("1. Settings > Accessibility > Cherrypicked > On", header = true))
            add(Row("2. If that is greyed out, run from a computer: adb shell settings put secure enabled_accessibility_services <current list>:$PKG/.a11y.CpAccessibilityService", header = true))
        }
        add(Row("Open accessibility settings", { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }))

        add(Row("Usage access", header = true, key = "usage"))
        add(Row(if (usageAllowed()) "Allowed" else "Not allowed", header = true))
        add(Row("Lets Cherrypicked rank apps by how much you watch them, not only by launches from Cherrypicked.", header = true))
        add(Row("Open usage access settings", {
            if (!open(Settings.ACTION_USAGE_ACCESS_SETTINGS)) note = "adb shell appops set $PKG GET_USAGE_STATS allow"
        }))
    }

    // TODO(Task 10): replace with HomeHealth.isEnabled once it exists.
    private fun isHomeConnected() = true

    @Suppress("DEPRECATION")
    private fun usageAllowed() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED

    private fun open(action: String): Boolean = try {
        startActivity(Intent(action)); true
    } catch (_: ActivityNotFoundException) { false }

    private fun render(section: String?, keepFocus: Boolean = false) {
        val oldIdx = if (keepFocus) list.indexOfChild(currentFocus) else -1
        list.removeAllViews()
        var target: View? = null
        var pendingSection = false
        for (r in rows()) {
            val v = row(r) { r.onClick?.invoke(); render(null, keepFocus = true) }
            list.addView(v)
            if (r.key != null) pendingSection = r.key == section
            else if (pendingSection && r.onClick != null && target == null) target = v
        }
        if (target == null && oldIdx >= 0) target = list.getChildAt(oldIdx.coerceAtMost(list.childCount - 1))
        if (target == null || !target.isFocusable) target = (0 until list.childCount).map(list::getChildAt).firstOrNull { it.isFocusable }
        target?.post { target.requestFocus(); target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height)) }
    }

    private fun row(r: Row, click: () -> Unit) = TextView(this).apply {
        text = r.text
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, vw * if (r.header && r.key != null) 1.4f else 1.7f)
        setTextColor(if (r.key != null) Theme.ACCENT else if (r.header) DIM else Theme.TEXT)
        setPadding((1.5 * vw).toInt(), (if (r.key != null) 2.2 * vw else 0.9 * vw).toInt(), (1.5 * vw).toInt(), (0.9 * vw).toInt())
        if (r.onClick != null) {
            isFocusable = true
            setOnClickListener { click() }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) GradientDrawable().apply { setColor(FOCUS_BG); setStroke(0, 0) } else ColorDrawable(0)
                (v as TextView).setTextColor(if (f) Theme.ACCENT else Theme.TEXT)
            }
        }
    }
}

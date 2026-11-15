package io.github.chimado.cherrypicked.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.a11y.tvInputPackages
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.apps.InputIcon
import io.github.chimado.cherrypicked.health.HomeHealth
import io.github.chimado.cherrypicked.inputs.InputCatalog
import io.github.chimado.cherrypicked.rank.UsageLedger
import io.github.chimado.cherrypicked.rank.foregroundLast14d
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.ConfigStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

private const val TAG = "Cherrypicked"
// Started by name: these activities land in Tasks 7 and 8; until then opening them is a no-op.
private const val ALL_APPS = "io.github.chimado.cherrypicked.home.AllAppsActivity"
private const val SETUP = "io.github.chimado.cherrypicked.setup.SetupActivity"

class HomeActivity : Activity() {
    private class Item(val view: View, val act: () -> Unit)

    private lateinit var catalog: AppCatalog
    private lateinit var store: ConfigStore
    private lateinit var ledger: UsageLedger
    private lateinit var body: FrameLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var hint: TextView
    private lateinit var regular: Typeface
    private var w = 0f
    private var h = 0f
    private var vw = 0f

    private var config = Config()
    private var shown: Pair<HomeRows, Suggestion?>? = null
    private var items: List<List<Item>> = emptyList()
    private var tier2Box: View? = null
    private var apps: List<AppEntry>? = null // cached; dropped by pkgRx
    private var excluded: Set<String>? = null // other launchers + TV-input apps, never suggested; dropped by pkgRx
    private var fg: Map<String, Long> = emptyMap()
    private var fgAt = Long.MIN_VALUE / 2
    private var resumed = false
    private var stale = false // a package changed while paused: rebuild on resume even if the rows look the same
    private var focus = FocusModel(listOf(1), listOf(listOf(0f)))
    private var hintPending = false

    private val handler = Handler(Looper.getMainLooper())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    private val dateFmt = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val tz = TimeZone.getDefault() // SimpleDateFormat caches the zone it was built with
            timeFmt.timeZone = tz
            dateFmt.timeZone = tz
            clock.text = timeFmt.format(Date(now))
            date.text = dateFmt.format(Date(now))
            handler.removeCallbacks(this)
            handler.postDelayed(this, 60_000 - now % 60_000)
        }
    }
    private val hideHint = Runnable {
        hint.visibility = View.GONE
        hintPending = false
        save { it.copy(hintShown = true) }
    }
    private val timeRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = tick.run()
    }
    private val pkgRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            i.data?.schemeSpecificPart?.let { catalog.evict(it) }
            apps = null
            excluded = null
            if (resumed) refresh(force = true) else stale = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = AppCatalog(this)
        store = ConfigStore(filesDir)
        Theme.load(store.load())
        ledger = UsageLedger(File(filesDir, "usage.json"))
        val b = windowManager.currentWindowMetrics.bounds
        w = max(b.width(), b.height()).toFloat()
        h = min(b.width(), b.height()).toFloat()
        vw = w / 100f

        val root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM))
        body = FrameLayout(this).apply { clipChildren = false }
        root.addView(body, FrameLayout.LayoutParams(-1, -1))
        val light = Typeface.createFromAsset(assets, "Inter-ExtraLight.ttf")
        regular = Typeface.createFromAsset(assets, "Inter-Regular.ttf")
        fun corner(font: Typeface, sizeVw: Float, color: Int, topFrac: Float) = text(font, sizeVw, color).also {
            root.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = (topFrac * h).toInt()
                rightMargin = (5 * vw).toInt()
            })
        }
        clock = corner(light, 3.4f, Theme.ACCENT, 0.06f)
        date = corner(regular, 1.4f, Theme.FAINT, 0.13f)
        hint = text(regular, 1.4f, Theme.FAINT).apply {
            text = "Settings live under Setup"
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
        }
        root.addView(hint, FrameLayout.LayoutParams(-1, -2).apply { topMargin = (0.93f * h + 0.8f * vw).toInt() })

        setContentView(root)
        // Registered for the activity's lifetime so the cached app list never goes stale while paused.
        registerReceiver(pkgRx, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, RECEIVER_NOT_EXPORTED)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun text(font: Typeface, sizeVw: Float, color: Int) = TextView(this).apply {
        typeface = font
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw)
        setTextColor(color)
        includeFontPadding = false
        maxLines = 1
    }

    override fun onResume() {
        super.onResume()
        tick.run()
        // Protected system broadcasts only; the system still delivers them to non-exported receivers.
        registerReceiver(timeRx, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }, RECEIVER_NOT_EXPORTED)
        resumed = true
        refresh(force = stale)
        stale = false
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        // The hint only counts as seen after 8 s on screen; paused early, it shows again next time.
        handler.removeCallbacks(hideHint)
        hint.visibility = View.GONE
        hintPending = false
        unregisterReceiver(timeRx)
        resumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pkgRx)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (items.isEmpty()) return
        show(focus.row, focus.col, false, animate = false)
        focus.reset()
        show(0, 0, true, animate = false)
    }

    /** Reloads config, apps and scores; rebuilds the views only when what they show changed (or [force]). */
    private fun refresh(force: Boolean, reload: Boolean = true) {
        if (reload) config = store.load()
        if (Theme.load(config)) { recreate(); return } // appearance changed in Setup
        val inputs = InputCatalog(this).inputs() // one cheap binder call; never cached, so renames/hot-plug show up
        val apps = apps ?: catalog.apps().also { apps = it }
        val now = System.currentTimeMillis()
        // Usage stats can take tens of ms; ranking drifts slowly, so 10 min staleness is fine.
        if (now - fgAt > 10 * 60_000L) { fg = foregroundLast14d(this); fgAt = now }
        val scores = ledger.scores(now, fg)
        val ex = excluded ?: (packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() + tvInputPackages(this)).also { excluded = it }
        val next = HomeModel.build(apps, config, scores, homeBroken = !HomeHealth.isEnabled(this), inputs = inputs) to Suggester.next(apps, config, scores, ex)
        if (force || next != shown) {
            shown = next
            build(next.first, next.second)
        }
        if (!config.hintShown && !hintPending) {
            hintPending = true
            hint.visibility = View.VISIBLE
            handler.postDelayed(hideHint, 8_000)
        }
    }

    /** Read-modify-write on a fresh load, so Home never writes back stale fields Setup changed (remaps, hidden...). */
    private fun save(change: (Config) -> Config) {
        val c = change(store.load())
        config = c
        try {
            store.save(c)
        } catch (e: IOException) {
            Log.w(TAG, "config save failed", e) // keep the in-memory change; never crash Home
        }
    }

    private fun build(rows: HomeRows, sug: Suggestion?) {
        body.removeAllViews()
        val its = mutableListOf<List<Item>>()
        val cx = mutableListOf<List<Float>>()
        val used = HashSet<String>()

        /** A centred row; tiles shrink (with radius, gap, ring and glow) so the row fits in [maxVw] (spec §4.2). */
        fun tileRow(tiles: List<Tile>, twVw: Float, thVw: Float, rVw: Float, gapVw: Float, top: Float, maxVw: Float): View {
            val n = tiles.size
            val s = HomeModel.fitScale(n, twVw, gapVw, maxVw)
            val tw = twVw * vw * s; val th = thVw * vw * s; val gap = gapVw * vw * s
            val glow = 3.5f * vw * s
            val m = TileView.margin(glow)
            val total = n * tw + (n - 1) * gap
            val start = (w - total) / 2
            // Tier 2 sits at 35% alpha; without this the row would be drawn through an offscreen layer every frame.
            val box = object : FrameLayout(this) { override fun hasOverlappingRendering() = false }.apply { clipChildren = false }
            val r = rVw * vw * s
            val glowMask = TileView.glow(tw, th, r, glow)
            its += tiles.mapIndexed { i, t ->
                val v = TileView(this, art(t, tw.toInt(), th.toInt(), r, used), tw, th, r, 0.35f * vw * s, glow, glowMask)
                box.addView(v, FrameLayout.LayoutParams((tw + 2 * m).toInt(), (th + 2 * m).toInt()).apply {
                    leftMargin = (i * (tw + gap)).toInt()
                })
                Item(v) { act(t) }
            }
            body.addView(box, FrameLayout.LayoutParams((total + 2 * m).toInt(), (th + 2 * m).toInt()).apply {
                leftMargin = (start - m).toInt()
                topMargin = (top - m).toInt()
            })
            cx += tiles.indices.map { start + it * (tw + gap) + tw / 2 }
            return box
        }

        val tier2Top = 0.93f * h - 5.6f * vw
        tileRow(rows.favorites, 22f, 12.4f, 2f, 3f, 0.34f * h, 90f)
        if (sug != null) strip(sug, tier2Top, its, cx)
        // 6 apps + Apps + Setup = 91.2vw fits as is; a 9th tile (Fix Home) shrinks the row slightly.
        tier2Box = tileRow(rows.tier2, 10f, 5.6f, 1.2f, 1.6f, tier2Top, 96f)
        catalog.retain(used)

        items = its
        focus = FocusModel(its.map { it.size }, cx) // rows are never empty: favorites has PickFavorites, tier 2 has AllApps/Setup
        for (r in items.indices) for (c in items[r].indices) show(r, c, r == 0 && c == 0, animate = false)
    }

    /** Suggestion question plus Yes / Pick another / Not now, centred above the tier-2 row. */
    private fun strip(s: Suggestion, tier2Top: Float, its: MutableList<List<Item>>, cx: MutableList<List<Float>>) {
        val question = when (s) {
            is Suggestion.MainApp -> "You open ${s.app.label} the most. Open it when the TV turns on?"
            is Suggestion.Favorite -> "You use ${s.app.label} a lot. Add it to favorites?"
        }
        val top = tier2Top - 7.5f * vw
        body.addView(text(regular, 1.4f, Theme.TEXT).apply { text = question; gravity = Gravity.CENTER_HORIZONTAL },
            FrameLayout.LayoutParams(-1, -2).apply { topMargin = top.toInt() })

        val buttons = listOfNotNull(
            "Yes" to { save { it.accept(s) }; refresh(force = true, reload = false) },
            ("Pick another" to { open(SETUP, "autoopen") }).takeIf { s is Suggestion.MainApp },
            "Not now" to { save { it.decline(s) }; refresh(force = true, reload = false) },
        )
        val pad = 1f * vw; val gap = 1.5f * vw
        val vpad = 0.4f * vw // room for the focus pill
        val views = buttons.map { (label, _) -> text(regular, 1.4f, Theme.DIM).apply { text = label; setPadding(pad.toInt(), vpad.toInt(), pad.toInt(), vpad.toInt()) } }
        val widths = views.map { it.paint.measureText(it.text.toString()) + 2 * pad }
        var x = (w - widths.sum() - gap * (views.size - 1)) / 2
        val centres = mutableListOf<Float>()
        views.forEachIndexed { i, v ->
            body.addView(v, FrameLayout.LayoutParams(widths[i].toInt() + 1, -2).apply {
                leftMargin = x.toInt()
                topMargin = (top + 3f * vw - vpad).toInt()
            })
            centres += x + widths[i] / 2
            x += widths[i] + gap
        }
        its += views.mapIndexed { i, v -> Item(v, buttons[i].second) }
        cx += centres
    }

    private fun art(t: Tile, tw: Int, th: Int, r: Float, used: MutableSet<String>): Drawable = when (t) {
        is Tile.App -> {
            used += catalog.key(t.app.pkg, tw, th, r)
            BitmapDrawable(resources, catalog.tileBitmap(t.app.pkg, tw, th, r))
        }
        is Tile.Input -> {
            val key = "input:${t.input.id}:${t.input.label}@${tw}x$th/$r"
            used += key
            BitmapDrawable(resources, catalog.cachedBitmap(key, tw, th) { catalog.card(t.input.label, InputIcon, r = r) })
        }
        Tile.PickFavorites -> catalog.card("Pick favorites", r = r)
        Tile.AllApps -> catalog.card("Apps", r = r)
        Tile.Setup -> catalog.card("Setup", r = r)
        Tile.FixHome -> catalog.card("Fix Home", r = r)
    }

    private fun act(t: Tile) {
        when (t) {
            is Tile.App -> if (catalog.launch(t.app.pkg)) ledger.recordLaunch(t.app.pkg, System.currentTimeMillis())
            is Tile.Input -> try {
                startActivity(InputCatalog(this).launchIntent(t.input.id))
            } catch (e: RuntimeException) { Log.w(TAG, "input ${t.input.id} not opened", e) } // ActivityNotFound / Security
            Tile.AllApps -> open(ALL_APPS)
            Tile.Setup -> open(SETUP)
            Tile.PickFavorites -> open(SETUP, "favorites")
            Tile.FixHome -> open(SETUP, "home")
        }
    }

    private fun open(cls: String, section: String? = null) {
        val i = Intent().setClassName(packageName, cls)
        section?.let { i.putExtra("section", it) }
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Log.w(TAG, "$cls not available yet")
        }
    }

    private fun show(r: Int, c: Int, on: Boolean, animate: Boolean = true) {
        when (val v = items[r][c].view) {
            is TileView -> if (animate) v.focus(on) else v.set(if (on) 1f else 0f)
            is TextView -> {
                v.setTextColor(if (on) Theme.ACCENT else Theme.DIM)
                v.background = if (on) GradientDrawable().apply { setColor(Theme.FOCUS_BG); cornerRadius = 10 * vw } else null // clamped to a pill
            }
        }
        if (on) tier2Box?.alpha = if (r == items.lastIndex) 1f else 0.35f
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        val dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!dpad && !ok && k != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)
        if (e.action != KeyEvent.ACTION_DOWN || items.isEmpty()) return true // Back is consumed and does nothing
        if (dpad) {
            val r = focus.row; val c = focus.col
            if (focus.move(k)) {
                show(r, c, false)
                show(focus.row, focus.col, true)
            }
        } else if (ok && e.repeatCount == 0) {
            items[focus.row][focus.col].act()
        }
        return true
    }
}

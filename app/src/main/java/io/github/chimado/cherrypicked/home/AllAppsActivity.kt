package io.github.chimado.cherrypicked.home

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.rank.UsageLedger
import io.github.chimado.cherrypicked.rank.foregroundLast14d
import io.github.chimado.cherrypicked.store.ConfigStore
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val COLS = 5
private const val DIM = 0x99E9EAEE.toInt()

/** Every non-hidden app, 5 per row; a header toggle flips Most used / A-Z (not saved). */
class AllAppsActivity : Activity() {
    private lateinit var catalog: AppCatalog
    private lateinit var ledger: UsageLedger
    private lateinit var config: io.github.chimado.cherrypicked.store.Config
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var toggle: TextView
    private lateinit var regular: Typeface
    private var w = 0f
    private var vw = 0f
    private var byUse = true
    private var apps: List<AppEntry> = emptyList()
    private var fg: Map<String, Long> = emptyMap()
    private var rows: List<List<AppEntry>> = emptyList()
    private var rowViews: List<View> = emptyList()
    private var tiles: List<List<Pair<TileView, TextView>>> = emptyList()
    private var focus = FocusModel(listOf(1), listOf(listOf(0f)))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog = AppCatalog(this)
        ledger = UsageLedger(File(filesDir, "usage.json"))
        config = ConfigStore(filesDir).load()
        val b = windowManager.currentWindowMetrics.bounds
        w = max(b.width(), b.height()).toFloat()
        vw = w / 100f
        regular = Typeface.createFromAsset(assets, "Inter-Regular.ttf")

        val root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM))
        toggle = TextView(this).apply {
            typeface = regular
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.8f * vw)
            includeFontPadding = false
        }
        root.addView(toggle, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = (5 * vw).toInt(); topMargin = (3 * vw).toInt()
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false // room for the focus glow above the first / below the last row
            setPadding(0, (2.5f * vw).toInt(), 0, (2.5f * vw).toInt())
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { topMargin = (8 * vw).toInt() })
        setContentView(root)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        apps = catalog.apps()
        fg = foregroundLast14d(this)
        build()
    }

    private fun build() {
        toggle.text = if (byUse) "Most used" else "A–Z"
        val sorted = if (byUse) HomeModel.rankAll(apps, config, ledger.scores(System.currentTimeMillis(), fg))
        else apps.filter { it.pkg !in config.hidden }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        rows = sorted.chunked(COLS)

        // 5 x 16vw + 4 x 2.5vw = 90vw, centred.
        val tw = 16 * vw; val th = 9 * vw; val gap = 2.5f * vw
        val glow = 1.5f * vw
        val m = TileView.margin(glow)
        val left = (w - (COLS * tw + (COLS - 1) * gap)) / 2
        val labelH = 2.4f * vw
        val rowH = (m + th + labelH + 1.6f * vw).toInt()
        val used = HashSet<String>()
        list.removeAllViews()
        val rv = mutableListOf<View>()
        val tv = mutableListOf<List<Pair<TileView, TextView>>>()
        val cx = mutableListOf(listOf(left + toggle.paint.measureText(toggle.text.toString()) / 2))
        val sizes = mutableListOf(1)
        for (row in rows) {
            val box = FrameLayout(this).apply { clipChildren = false }
            tv += row.mapIndexed { i, a ->
                val x = left + i * (tw + gap)
                used += catalog.key(a.pkg, tw.toInt(), th.toInt())
                val v = TileView(this, BitmapDrawable(resources, catalog.tileBitmap(a.pkg, tw.toInt(), th.toInt())),
                    tw, th, 1.2f * vw, 0.3f * vw, glow)
                box.addView(v, FrameLayout.LayoutParams((tw + 2 * m).toInt(), (th + 2 * m).toInt()).apply { leftMargin = (x - m).toInt() })
                val label = TextView(this).apply {
                    text = a.label; typeface = regular; maxLines = 1; includeFontPadding = false
                    ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_HORIZONTAL
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 1.3f * vw); setTextColor(DIM)
                }
                box.addView(label, FrameLayout.LayoutParams(tw.toInt(), -2).apply {
                    leftMargin = x.toInt(); topMargin = (m + th + 0.5f * vw).toInt()
                })
                v to label
            }
            list.addView(box, LinearLayout.LayoutParams(-1, rowH))
            rv += box
            cx += row.indices.map { left + it * (tw + gap) + tw / 2 }
            sizes += row.size
        }
        catalog.retain(used)
        rowViews = rv; tiles = tv
        focus = FocusModel(sizes, cx)
        show(0, 0, true)
        scroll.scrollTo(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        catalog.retain(emptySet()) // release tile bitmaps with the activity
    }

    private fun show(r: Int, c: Int, on: Boolean) {
        if (r == 0) { toggle.setTextColor(if (on) Theme.ACCENT else DIM); return }
        val (v, label) = tiles[r - 1][c]
        v.set(if (on) 1f else 0f)
        label.setTextColor(if (on) Theme.TEXT else DIM)
        if (on) {
            val box = rowViews[r - 1]
            val pad = scroll.paddingTop
            if (box.top - pad < scroll.scrollY) scroll.smoothScrollTo(0, box.top - pad)
            else if (box.bottom + pad > scroll.scrollY + scroll.height) scroll.smoothScrollTo(0, box.bottom + pad - scroll.height)
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        val dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
        val ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!dpad && !ok && k != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)
        if (e.action != KeyEvent.ACTION_DOWN) return true
        if (k == KeyEvent.KEYCODE_BACK) { finish(); return true }
        if (dpad) {
            val r = focus.row; val c = focus.col
            if (focus.move(k)) { show(r, c, false); show(focus.row, focus.col, true) }
        } else if (e.repeatCount == 0) {
            if (focus.row == 0) { byUse = !byUse; build() }
            else {
                val a = rows[focus.row - 1][focus.col]
                if (catalog.launch(a.pkg)) ledger.recordLaunch(a.pkg, System.currentTimeMillis())
            }
        }
        return true
    }
}

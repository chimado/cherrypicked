package io.github.chimado.cherrypicked.setup

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.apps.InputIcon
import io.github.chimado.cherrypicked.home.AppEntry
import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.home.PatternDrawable
import io.github.chimado.cherrypicked.home.TileView
import io.github.chimado.cherrypicked.home.TvInput
import io.github.chimado.cherrypicked.home.named
import io.github.chimado.cherrypicked.store.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The favorites editor, drawn like the home screen: the favorites row on top, a shelf of everything that can be added
 * below. [apps] is A to Z; [inputs] carry the TV's own labels. Every change goes through [edit] and is saved at once.
 */
class FavEditor(
    private val act: Activity,
    private val w: Float,
    private val h: Float,
    private val regular: Typeface,
    private val apps: List<AppEntry>,
    private val inputs: List<TvInput>,
    private val config: () -> Config,
    private val edit: ((Config) -> Config) -> Unit,
    private val askName: (TvInput, () -> Unit) -> Unit,
    private val exit: () -> Unit,
) : FrameLayout(act) {
    private val vw = w / 100f
    private val catalog = AppCatalog(act)
    private val nav: EditorNav
    private var favTiles: List<TileView> = emptyList()
    private var shelfTiles: List<TileView> = emptyList()
    private var chips: List<TextView> = emptyList()
    private var shelfBox: View? = null
    private var shelfStep = 0f
    private var shelfWidth = 0f
    private val hint = text(1.4f, Theme.DIM).apply { gravity = Gravity.CENTER_HORIZONTAL }
    private var note: String? = null

    init {
        background = PatternDrawable(Theme.palette)
        clipChildren = false
        nav = EditorNav(favs(), shelf())
        build()
    }

    /** The favorites the home screen shows. */
    private fun favs(): List<String> = config().let { c ->
        c.favorites.filter { it !in c.hidden && (apps.any { a -> a.pkg == it } || inputs.any { i -> "input:${i.id}" == it }) }.take(HomeModel.MAX_FAVORITES)
    }

    private fun shelf(): List<String> = config().let { c ->
        (apps.filter { it.pkg !in c.hidden }.map { it.pkg } + inputs.map { "input:${it.id}" }) - favs().toSet()
    }

    private fun text(sizeVw: Float, color: Int) = TextView(act).apply {
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw)
        setTextColor(color)
        includeFontPadding = false
        maxLines = 1
    }

    private fun input(entry: String) = inputs.firstOrNull { "input:${it.id}" == entry }

    private fun art(entry: String, tw: Int, th: Int, r: Float, used: MutableSet<String>): Drawable {
        val i = input(entry) ?: run {
            used += catalog.key(entry, tw, th, r)
            return BitmapDrawable(resources, catalog.tileBitmap(entry, tw, th, r))
        }
        val label = listOf(i).named(config())[0].label
        val key = "input:${i.id}:$label@${tw}x$th/$r"
        used += key
        return BitmapDrawable(resources, catalog.cachedBitmap(key, tw, th) { catalog.card(label, InputIcon, r = r) })
    }

    /** Lays everything out again; called after each change to the favorites, not on focus moves. */
    private fun build() {
        removeAllViews()
        val c = config()
        val used = HashSet<String>()
        addView(text(1.7f, Theme.DIM).apply { text = "Edit favorites" }, LayoutParams(-2, -2).apply { leftMargin = (5 * vw).toInt(); topMargin = (0.07f * h).toInt() })
        addView(text(3.4f, Theme.ACCENT).apply {
            typeface = Typeface.createFromAsset(act.assets, "Inter-ExtraLight.ttf")
            text = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date())
        }, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = (0.06f * h).toInt(); rightMargin = (5 * vw).toInt() })

        fun row(entries: List<String>, extra: Int, twVw: Float, thVw: Float, rVw: Float, gapVw: Float, top: Float, maxVw: Float?, decorate: (FrameLayout, Int, Float, Float, Float, Float) -> Unit): Pair<FrameLayout, List<TileView>> {
            val n = entries.size + extra
            val s = if (maxVw == null) 1f else HomeModel.fitScale(n, twVw, gapVw, maxVw)
            val tw = twVw * vw * s; val th = thVw * vw * s; val gap = gapVw * vw * s; val r = rVw * vw * s
            val glow = 3.5f * vw * s
            val m = TileView.margin(glow)
            val total = n * tw + (n - 1) * gap
            val box = FrameLayout(act).apply { clipChildren = false }
            val mask = TileView.glow(tw, th, r, glow)
            val tiles = entries.mapIndexed { i, e ->
                TileView(act, art(e, tw.toInt(), th.toInt(), r, used), tw, th, r, 0.35f * vw * s, glow, mask).also {
                    box.addView(it, LayoutParams((tw + 2 * m).toInt(), (th + 2 * m).toInt()).apply { leftMargin = (i * (tw + gap)).toInt() })
                }
            }
            for (i in 0 until n) decorate(box, i, m + i * (tw + gap), m, tw, th)
            addView(box, LayoutParams((total + 2 * m).toInt(), (th + 2 * m).toInt()).apply {
                leftMargin = (maxOf((w - total) / 2, 5 * vw) - m).toInt()
                topMargin = (top - m).toInt()
            })
            if (maxVw == null) { shelfStep = tw + gap; shelfWidth = total }
            return box to tiles
        }

        val favs = nav.favs
        val favTop = 0.34f * h
        val slot = if (favs.size < HomeModel.MAX_FAVORITES) 1 else 0
        favTiles = row(favs, slot, 22f, 12.4f, 2f, 3f, favTop, 90f) { box, i, left, top, tw, th ->
            if (i >= favs.size) { // the empty slot
                box.addView(View(act).apply {
                    background = GradientDrawable().apply { setStroke((0.15f * vw).toInt(), Theme.FAINT, 0.8f * vw, 0.6f * vw); cornerRadius = tw / 11f }
                }, LayoutParams(tw.toInt(), th.toInt()).apply { leftMargin = left.toInt(); topMargin = top.toInt() })
                return@row
            }
            val e = favs[i]
            fun over(v: TextView, fromRight: Boolean) {
                v.z = 2f // above a focused tile
                v.measure(0, 0)
                box.addView(v, LayoutParams(-2, -2).apply {
                    topMargin = (top + 0.08f * th).toInt()
                    leftMargin = (if (fromRight) left + 0.95f * tw - v.measuredWidth else left + 0.05f * tw).toInt()
                })
            }
            // The TV's own name for a renamed input, so the owner can tell which port it is.
            input(e)?.takeIf { it.id in c.inputNames }?.let { over(text(1.1f, Theme.DIM).apply { text = it.label }, false) }
            if (e == c.mainApp) over(text(1.1f, 0xFFFFFFFF.toInt()).apply {
                text = "Main"
                setPadding((0.7f * vw).toInt(), (0.25f * vw).toInt(), (0.7f * vw).toInt(), (0.25f * vw).toInt())
                background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = 2 * vw }
            }, true)
        }.second

        val chipTop = favTop + 12.4f * vw + 2.6f * vw
        val opts = nav.options
        chips = opts.map { o -> text(1.4f, Theme.DIM).apply { text = o.label; setPadding(vw.toInt(), (0.4f * vw).toInt(), vw.toInt(), (0.4f * vw).toInt()) } }
        val widths = chips.map { it.paint.measureText(it.text.toString()) + 2 * vw }
        var x = (w - widths.sum() - 1.5f * vw * (chips.size - 1)) / 2
        chips.forEachIndexed { i, v ->
            addView(v, LayoutParams(widths[i].toInt() + 1, -2).apply { leftMargin = x.toInt(); topMargin = chipTop.toInt() })
            x += widths[i] + 1.5f * vw
        }
        addView(hint, LayoutParams(-1, -2).apply { topMargin = (chipTop + 4.2f * vw).toInt() })

        val shelfTop = 0.93f * h - 5.6f * vw
        addView(text(1.4f, Theme.FAINT).apply { text = "Add to favorites" }, LayoutParams(-2, -2).apply { leftMargin = (5 * vw).toInt(); topMargin = (shelfTop - 3.2f * vw).toInt() })
        row(nav.shelf, 0, 10f, 5.6f, 1.2f, 1.6f, shelfTop, null) { _, _, _, _, _, _ -> }.let { shelfBox = it.first; shelfTiles = it.second }
        catalog.retain(used)
        paint(animate = false)
    }

    /** Shows where the focus is: rings, chips, the hint, and the shelf scrolled so the focused item is on screen. */
    private fun paint(animate: Boolean = true) {
        fun mark(t: TileView, on: Boolean) = if (animate) t.focus(on) else t.set(if (on) 1f else 0f)
        favTiles.forEachIndexed { i, t -> mark(t, i == nav.fav && nav.zone != Zone.SHELF); t.alpha = if (nav.held && i != nav.fav) 0.5f else 1f }
        shelfTiles.forEachIndexed { i, t -> mark(t, i == nav.shelfCol && nav.zone == Zone.SHELF) }
        chips.forEachIndexed { i, v ->
            val on = nav.zone == Zone.OPTIONS && i == nav.opt
            v.visibility = if (nav.held) View.VISIBLE else View.INVISIBLE
            v.setTextColor(if (on) Theme.ACCENT else Theme.DIM)
            v.background = GradientDrawable().apply { setColor(if (on) Theme.FOCUS_BG else 0x22FFFFFF); cornerRadius = 10 * vw }
        }
        shelfBox?.let { b ->
            b.alpha = if (nav.zone == Zone.SHELF) 1f else 0.35f
            // Keep the focused shelf item near the middle once the row is wider than the screen.
            val over = shelfWidth - (w - 10 * vw)
            b.translationX = if (over <= 0) 0f else -(nav.shelfCol * shelfStep - (w - 10 * vw) / 2 + shelfStep / 2).coerceIn(0f, over)
        }
        hint.text = note ?: when {
            nav.favs.isEmpty() && nav.shelf.isEmpty() -> "Nothing to add"
            nav.zone == Zone.SHELF -> "OK to add"
            nav.held -> "Left and right to move · Down for options · OK to drop"
            else -> "OK to pick up"
        }
        note = null
    }

    /** One key press from the activity. */
    fun key(k: Nav) {
        val held = nav.held
        var rebuild = false
        when (val e = nav.key(k)) {
            null -> {}
            Edit.Exit -> return exit()
            Edit.Full -> note = "${HomeModel.MAX_FAVORITES} of ${HomeModel.MAX_FAVORITES}: remove one first"
            is Edit.Move -> { change { it.moveFavorite(e.entry, e.d) }; rebuild = true }
            is Edit.Add -> { change { it.addFavorite(e.entry) }; rebuild = true }
            is Edit.Remove -> { change { it.removeFavorite(e.entry) }; rebuild = true }
            is Edit.Main -> { edit { it.setMainApp(e.entry) }; note = "Opens when the TV turns on"; rebuild = true }
            is Edit.Rename -> return rename(e.entry)
        }
        // The option chips belong to the held tile, so picking up or dropping needs a new layout too.
        if (rebuild || held != nav.held) build() else paint()
    }

    private fun change(c: (Config) -> Config) {
        val shown = favs()
        // Favorites not on screen (a hidden app, an input the TV doesn't list right now) keep their place at the end.
        edit { old -> c(old.copy(favorites = shown)).let { it.copy(favorites = it.favorites + (old.favorites - shown.toSet() - it.favorites.toSet())) } }
        nav.sync(favs(), shelf())
    }

    private fun rename(entry: String) { input(entry)?.let { askName(it) { build() } } }
}

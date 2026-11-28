package io.github.chimado.cherrypicked.panel

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.store.ConfigStore
import io.github.chimado.cherrypicked.a11y.Fonts
import io.github.chimado.cherrypicked.home.named
import io.github.chimado.cherrypicked.inputs.InputCatalog
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.setup.SetupActivity
import io.github.chimado.cherrypicked.timer.SleepTimer

private const val IDLE_MS = 15_000L // closes itself after this long without a panel key

internal fun sleepLabel(endMs: Long?, nowMs: Long): String {
    val left = SleepTimer.remainingMs(endMs, nowMs)?.takeIf { it > 0 } ?: return "Sleep timer: Off" // expired-but-unfired reads Off
    return "Sleep timer: ${(left + 59_999) / 60_000} min left"
}

enum class PanelRoute { ENGINE, CLOSE, PANEL }

/** The panel's own keys: D-pad, OK/Enter, Back, Home. Everything else (volume, mute, media, channels) stays with the engine. */
val PANEL_KEYS = setOf(19, 20, 21, 22, 23, 66, 160, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME)

/** Where a key goes. The press that opened the panel (down before [openedAt]) stays with the engine so it sees its UP. */
fun panelRoute(open: Boolean, downTime: Long, openedAt: Long, code: Int, down: Boolean): PanelRoute = when {
    !open || downTime < openedAt || code !in PANEL_KEYS -> PanelRoute.ENGINE
    !down && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) -> PanelRoute.CLOSE
    else -> PanelRoute.PANEL
}

/**
 * Right-side sheet drawn by whichever process hosts it (:a11y or :ui) with window [type]. Not focusable: while open the host
 * hands it every panel key ([onKey]), and it draws its own selection. Views exist only while open. [onClosed] runs on every close.
 */
class QuickPanel(
    private val svc: Context,
    private val handler: Handler,
    private val type: Int,
    private val run: (Action) -> Unit,
    private val sleepEnd: () -> Long?,
    private val onClosed: () -> Unit = {},
) {
    private class Row(val id: String, val text: () -> String, val ok: () -> Unit)

    private val store by lazy { ConfigStore(svc.filesDir) } // skips parsing while config.json is unchanged
    private var list: LinearLayout? = null
    private var rows: List<Row> = emptyList()
    private var sel = 0
    private var w = 0
    private val idle = Runnable { Log.i("CP", "panel timeout"); close() } // posted only while open

    /** Uptime when opened; a press that began earlier (the long-press Home that opened it) stays with the remap engine. */
    var openedAt = 0L
        private set

    fun isOpen() = list != null

    fun toggle() { if (isOpen()) close() else open() }

    /** False if the window could not be drawn (logged). */
    fun open(): Boolean {
        if (isOpen()) return true
        Theme.load(store.load()) // this process may have missed an appearance change
        w = svc.resources.displayMetrics.widthPixels
        rows = buildList {
            add(Row("sleep", { sleepLabel(sleepEnd(), System.currentTimeMillis()) }) { run(Action.SleepTimer); refresh(sel) })
            for (i in InputCatalog(svc).inputs().named(store.load())) add(Row("input:${i.id}", { i.label }) { close(); run(Action.OpenInput(i.id)) })
            add(Row("screen_off", { "Screen off" }) { close(); run(Action.ScreenOff) })
            add(Row("setup", { "Setup" }) {
                svc.startActivity(Intent(svc, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                close()
            })
        }
        sel = 0
        val lp = WindowManager.LayoutParams(
            Theme.vw(28f, w).toInt(), WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.TOP }
        val v = try { // a font or window failure logs instead of killing the host process
            val regular = Fonts.regular(svc)
            LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                val p = Theme.vw(2f, w).toInt()
                setPadding(0, p, 0, p)
                setBackgroundColor(Theme.BG_TOP) // opaque: the app's clock must not show through
                for (r in rows) addView(TextView(svc).apply {
                    typeface = regular
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.vw(1.6f, w))
                    val px = Theme.vw(1.5f, w).toInt(); val py = Theme.vw(0.9f, w).toInt()
                    setPadding(px, py, px, py)
                })
            }.also { svc.getSystemService(WindowManager::class.java).addView(it, lp) }
        } catch (e: RuntimeException) { Log.w("CP", "panel not shown", e); rows = emptyList(); return false }
        list = v
        openedAt = SystemClock.uptimeMillis()
        Log.i("CP", "panel open")
        refresh(-1)
        handler.postDelayed(idle, IDLE_MS)
        return true
    }

    fun close() {
        handler.removeCallbacks(idle)
        val v = list ?: return
        list = null
        rows = emptyList()
        try { svc.getSystemService(WindowManager::class.java).removeView(v) } catch (_: RuntimeException) {}
        Log.i("CP", "panel close")
        onClosed()
    }

    /** Redraws every row, e.g. after the sleep timer changed in another process. */
    fun redraw() = refresh(-1)

    /** Called for each of the panel's keys while open (see [panelRoute]); always consumes. Up/Down move on press (and repeat); OK acts on release. */
    fun onKey(code: Int, down: Boolean, canceled: Boolean): Boolean {
        if (!isOpen()) return true // a key in flight when it closed: no idle timer for a closed panel
        handler.removeCallbacks(idle)
        handler.postDelayed(idle, IDLE_MS)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) move(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (!down && !canceled) rows.getOrNull(sel)?.let { Log.i("CP", "panel ok ${it.id}"); it.ok() }
        }
        return true
    }

    private fun move(d: Int) {
        val n = (sel + d).coerceIn(0, rows.lastIndex)
        if (n == sel) return
        val old = sel
        sel = n
        Log.i("CP", "panel select ${rows[sel].id}")
        refresh(old)
    }

    /** Redraws row [old] (or every row when -1) and the selected row. */
    private fun refresh(old: Int) {
        val l = list ?: return
        for (i in rows.indices) if (old < 0 || i == old || i == sel) (l.getChildAt(i) as TextView).apply {
            text = rows[i].text()
            val f = i == sel
            setTextColor(if (f) Theme.ACCENT else Theme.TEXT)
            background = if (f) LayerDrawable(arrayOf(ColorDrawable(Theme.FOCUS_BG), ColorDrawable(Theme.ACCENT))).apply {
                setLayerWidth(1, Theme.vw(0.3f, w).toInt())
                setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
            } else null
        }
    }
}

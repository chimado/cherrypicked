package io.github.chimado.cherrypicked.panel

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.setup.SetupActivity
import io.github.chimado.cherrypicked.timer.SleepTimer

private const val FOCUS_BG = 0x33E0233F // Setup's focus tint

internal fun sleepLabel(endMs: Long?, nowMs: Long): String {
    val left = SleepTimer.remainingMs(endMs, nowMs) ?: return "Sleep timer: Off"
    return "Sleep timer: ${(left + 59_999) / 60_000} min left"
}

/**
 * Right-side sheet drawn by the service. Not focusable: while open the service hands it every key ([onKey]),
 * and it draws its own selection. Views exist only while open.
 */
class QuickPanel(
    private val svc: AccessibilityService,
    private val run: (Action) -> Unit,
    private val sleepEnd: () -> Long?,
) {
    private class Row(val id: String, val text: () -> String, val ok: () -> Unit)

    private var list: LinearLayout? = null
    private var rows: List<Row> = emptyList()
    private var sel = 0
    private var w = 0

    /** Uptime when opened; a press that began earlier (the long-press Home that opened it) stays with the remap engine. */
    var openedAt = 0L
        private set

    fun isOpen() = list != null

    fun toggle() = if (isOpen()) close() else open()

    private fun open() {
        w = svc.resources.displayMetrics.widthPixels
        rows = buildList {
            add(Row("sleep", { sleepLabel(sleepEnd(), System.currentTimeMillis()) }) { run(Action.SleepTimer); refresh(sel) })
            // Task 7: one Row per InputCatalog input goes here.
            add(Row("screen_off", { "Screen off" }) { close(); run(Action.ScreenOff) })
            add(Row("setup", { "Setup" }) {
                svc.startActivity(Intent(svc, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                close()
            })
        }
        sel = 0
        val regular = Typeface.createFromAsset(svc.assets, "Inter-Regular.ttf")
        val v = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            val p = Theme.vw(2f, w).toInt()
            setPadding(0, p, 0, p)
            setBackgroundColor((0xF5 shl 24) or (Theme.BG_TOP and 0xFFFFFF)) // 96% alpha
            for (r in rows) addView(TextView(svc).apply {
                typeface = regular
                setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.vw(1.6f, w))
                val px = Theme.vw(1.5f, w).toInt(); val py = Theme.vw(0.9f, w).toInt()
                setPadding(px, py, px, py)
            })
        }
        val lp = WindowManager.LayoutParams(
            Theme.vw(28f, w).toInt(), WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.TOP }
        try {
            svc.getSystemService(WindowManager::class.java).addView(v, lp)
        } catch (e: RuntimeException) { Log.w("CP", "panel not shown", e); rows = emptyList(); return }
        list = v
        openedAt = SystemClock.uptimeMillis()
        Log.i("CP", "panel open")
        refresh(-1)
    }

    fun close() {
        val v = list ?: return
        list = null
        rows = emptyList()
        try { svc.getSystemService(WindowManager::class.java).removeView(v) } catch (_: RuntimeException) {}
        Log.i("CP", "panel close")
    }

    /** Called for every key while open; always consumes. Up/Down move on press (and repeat); OK acts on release. */
    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) move(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) move(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (!down && !event.isCanceled) rows.getOrNull(sel)?.let { Log.i("CP", "panel ok ${it.id}"); it.ok() }
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
            background = if (f) LayerDrawable(arrayOf(ColorDrawable(FOCUS_BG), ColorDrawable(Theme.ACCENT))).apply {
                setLayerWidth(1, Theme.vw(0.3f, w).toInt())
                setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
            } else null
        }
    }
}

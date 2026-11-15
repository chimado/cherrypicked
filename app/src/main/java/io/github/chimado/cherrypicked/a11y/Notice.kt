package io.github.chimado.cherrypicked.a11y

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme

/**
 * A small top-centre pill (TCL's toast quota blocks Toasts), drawn by whichever process hosts it with window [type].
 * The view exists only while shown. [onHidden] runs when a notice times out (false) or fails to show (true), not when replaced.
 */
class Notice(private val svc: Context, private val handler: Handler, private val type: Int, private val onHidden: (failed: Boolean) -> Unit = {}) {
    private var view: View? = null
    private val hide = Runnable { dismiss(); onHidden(false) }

    val showing get() = view != null

    fun show(text: String) {
        dismiss()
        Log.i("CP", "notice \"$text\"")
        val w = svc.resources.displayMetrics.widthPixels
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = Theme.vw(2f, w).toInt() }
        try { // a font or window failure logs instead of killing the process
            val v = TextView(svc).apply {
                this.text = text
                typeface = Fonts.regular(svc)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, Theme.vw(1.4f, w))
                setTextColor(Theme.TEXT)
                val px = Theme.vw(1f, w).toInt(); val py = Theme.vw(0.6f, w).toInt()
                setPadding(px, py, px, py)
                background = GradientDrawable().apply {
                    setColor((0xEB shl 24) or (Theme.BG_TOP and 0xFFFFFF)) // 92% alpha
                    cornerRadius = Theme.vw(1f, w)
                }
            }
            svc.getSystemService(WindowManager::class.java).addView(v, lp)
            view = v
            handler.postDelayed(hide, 2500)
        } catch (e: RuntimeException) { Log.w("CP", "notice not shown", e); onHidden(true) }
    }

    fun dismiss() {
        handler.removeCallbacks(hide)
        val v = view ?: return
        view = null
        try { svc.getSystemService(WindowManager::class.java).removeView(v) } catch (_: RuntimeException) {}
    }
}

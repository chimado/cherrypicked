package io.github.chimado.cherrypicked.setup

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.github.chimado.cherrypicked.Theme

/** A four-digit entry: a title, the digits typed so far as dots, and a numbers-only pad. [onPin] gets each full entry. */
class PinPad(ctx: Context, private val vw: Float, private val font: Typeface, title: String, private val onPin: (String) -> Unit) : LinearLayout(ctx) {
    private var entry = ""
    private val titleView = label(title, 2f, Theme.TEXT)
    private val dots = label("", 3f, Theme.ACCENT)
    private val noteView = label("", 1.4f, Theme.DIM)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM))
        addView(titleView)
        addView(dots)
        addView(noteView)
        for (keys in listOf("123", "456", "789", "⌫0 ")) addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            for (k in keys) addView(key(k))
        })
        show()
        post { findViewWithTag<TextView>('1').requestFocus() }
    }

    /** Asks again under a new [title], with [note] saying why. */
    fun ask(title: String, note: String = "") { titleView.text = title; noteView.text = note; entry = ""; show() }

    fun type(d: Char) {
        entry += d
        noteView.text = ""
        show()
        if (entry.length == 4) { val pin = entry; entry = ""; show(); onPin(pin) }
    }

    fun delete() { entry = entry.dropLast(1); show() }

    private fun show() { dots.text = (0 until 4).joinToString(" ") { if (it < entry.length) "●" else "○" } }

    private fun label(s: String, sizeVw: Float, color: Int) = TextView(context).apply {
        text = s
        typeface = font
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeVw * vw)
        setTextColor(color)
        setPadding(0, (0.6f * vw).toInt(), 0, (0.6f * vw).toInt())
    }

    private fun key(k: Char) = label(k.toString(), 2.2f, Theme.TEXT).apply {
        val size = (5.5f * vw).toInt()
        layoutParams = LayoutParams(size, size).apply { setMargins((0.4f * vw).toInt(), (0.4f * vw).toInt(), (0.4f * vw).toInt(), (0.4f * vw).toInt()) }
        if (k == ' ') { text = ""; return@apply } // keeps 0 under 8
        tag = k
        contentDescription = if (k == '⌫') "Delete" else k.toString()
        isFocusable = true
        setOnClickListener { if (k == '⌫') delete() else type(k) }
        setOnFocusChangeListener { v, f ->
            v.background = if (f) GradientDrawable().apply { setColor(Theme.FOCUS_BG); cornerRadius = 1.2f * vw } else null
            (v as TextView).setTextColor(if (f) Theme.ACCENT else Theme.TEXT)
        }
    }
}

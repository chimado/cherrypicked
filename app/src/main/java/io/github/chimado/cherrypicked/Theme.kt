package io.github.chimado.cherrypicked

import io.github.chimado.cherrypicked.store.Config

/** The active appearance; each process calls [load] wherever it loads the config. */
object Theme {
    var palette = palette(Config())
        private set

    val BG_TOP get() = palette.bgTop
    val BG_BOTTOM get() = palette.bgBottom
    val ACCENT get() = palette.accent
    val TEXT get() = palette.text
    val DIM get() = (0x99 shl 24) or (TEXT and 0xFFFFFF)
    val FAINT get() = (0x80 shl 24) or (TEXT and 0xFFFFFF)
    val FOCUS_BG get() = (0x33 shl 24) or (ACCENT and 0xFFFFFF)

    /** Returns true when the palette changed. */
    fun load(c: Config): Boolean {
        val p = palette(c)
        if (p == palette) return false
        palette = p
        return true
    }

    /** [v] percent of the screen width, in pixels. */
    fun vw(v: Float, screenW: Int): Float = v * screenW / 100f
}

package io.github.chimado.cherrypicked

object Theme {
    const val BG_TOP = 0xFF17181B.toInt()
    const val BG_BOTTOM = 0xFF0D0E10.toInt()
    const val ACCENT = 0xFFE0233F.toInt()
    const val TEXT = 0xFFE9EAEE.toInt()

    /** [v] percent of the screen width, in pixels. */
    fun vw(v: Float, screenW: Int): Float = v * screenW / 100f
}

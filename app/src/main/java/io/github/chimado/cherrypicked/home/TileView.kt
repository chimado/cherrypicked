package io.github.chimado.cherrypicked.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.chimado.cherrypicked.Theme

/**
 * One tile: [art] (already rounded, see AppCatalog.tileBitmap), plus focus ring and [glow] (from [glow], shared per row).
 * The view is [m] larger on each side for the glow.
 */
class TileView(ctx: Context, private val art: Drawable, tw: Float, th: Float, private val radius: Float, ringW: Float, glowBlur: Float, private val glow: Bitmap) : View(ctx) {
    val m = margin(glowBlur)
    private val rect = RectF(m, m, m + tw, m + th)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringW; color = Theme.ACCENT }
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Theme.ACCENT } // ALPHA_8 bitmaps draw in the paint colour
    private var f = 0f
    private var anim: ValueAnimator? = null

    init {
        outlineProvider = null // z is used only for draw order, never a shadow
        art.setBounds(m.toInt(), m.toInt(), (m + tw).toInt(), (m + th).toInt())
    }

    fun focus(on: Boolean) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(f, if (on) 1f else 0f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
            addUpdateListener { set(it.animatedValue as Float) }
            start()
        }
    }

    /** Jumps straight to focus amount [v] (0..1) without animating. */
    fun set(v: Float) {
        f = v
        z = if (v > 0f) 1f else 0f
        scaleX = 1f + 0.07f * v
        scaleY = scaleX
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (f > 0f) {
            glowPaint.alpha = (0.4f * 255 * f).toInt()
            c.drawBitmap(glow, 0f, 0f, glowPaint)
        }
        art.draw(c)
        if (f > 0f) {
            ring.alpha = (255 * f).toInt()
            c.drawRoundRect(rect, radius, radius, ring)
        }
    }

    companion object {
        /** Extra room on each side of the tile for the glow. */
        fun margin(glowBlur: Float) = glowBlur * 1.5f

        /** The blurred tile shape as an ALPHA_8 mask the size of a TileView, rendered once and shared by a row's tiles. */
        fun glow(tw: Float, th: Float, radius: Float, glowBlur: Float): Bitmap {
            val m = margin(glowBlur)
            // CSS blur B ~ sigma B/2; Skia sigma = 0.57735 * radius + 0.5.
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(glowBlur * 0.866f, BlurMaskFilter.Blur.NORMAL) }
            return Bitmap.createBitmap((tw + 2 * m).toInt(), (th + 2 * m).toInt(), Bitmap.Config.ALPHA_8).also {
                Canvas(it).drawRoundRect(RectF(m, m, m + tw, m + th), radius, radius, p)
            }
        }
    }
}

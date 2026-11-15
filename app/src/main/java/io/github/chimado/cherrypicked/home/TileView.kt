package io.github.chimado.cherrypicked.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.chimado.cherrypicked.Theme

/** One tile: [art] clipped to a rounded rect, plus focus ring and glow. The view is [m] larger on each side for the glow. */
class TileView(ctx: Context, private val art: Drawable, tw: Float, th: Float, private val radius: Float, ringW: Float, glowBlur: Float) : View(ctx) {
    val m = margin(glowBlur)
    private val rect = RectF(m, m, m + tw, m + th)
    private val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringW; color = Theme.ACCENT }
    // CSS blur B ~ sigma B/2; Skia sigma = 0.57735 * radius + 0.5.
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.ACCENT; maskFilter = BlurMaskFilter(glowBlur * 0.866f, BlurMaskFilter.Blur.NORMAL) }
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
            glow.alpha = (0.4f * 255 * f).toInt()
            c.drawRoundRect(rect, radius, radius, glow)
        }
        c.save()
        c.clipPath(clip)
        art.draw(c)
        c.restore()
        if (f > 0f) {
            ring.alpha = (255 * f).toInt()
            c.drawRoundRect(rect, radius, radius, ring)
        }
    }

    companion object {
        /** Extra room on each side of the tile for the glow. */
        fun margin(glowBlur: Float) = glowBlur * 1.5f
    }
}

package io.github.chimado.cherrypicked.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import io.github.chimado.cherrypicked.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val W = 960f
private const val H = 540f

/**
 * The base gradient plus [Palette.pattern], ported from docs/design/appearance-mockup.html. Shapes are built once, in
 * the mockup's 960x540 design space, and scaled to the bounds; the view system records the drawing, so it isn't redone
 * on focus moves. Strengths and placements are the numbers to tune on the TV.
 */
class PatternDrawable(private val p: Palette) : Drawable() {
    private val bg = Paint()
    private val paths = ArrayList<Pair<Pair<Int, Float>, Path>>() // (colour, stroke width; 0 = fill) to shapes
    private val paints: List<Pair<Path, Paint>>
    private val glow: Paint?

    private val f = if (p.light) 1.6f else 1f

    private fun alpha(a: Float) = (a * 255).roundToInt().coerceIn(0, 255) shl 24
    /** Faint marks: white on dark bases, black at 1.6x on light ones. */
    private fun ink(a: Float) = alpha(a * f) or if (p.light) 0 else 0xFFFFFF
    private fun acc(a: Float) = alpha(a) or (p.accent and 0xFFFFFF)
    /** A mark of the ink/fill colour itself (the mockup's `#fff`/`#000` with an opacity that already includes f). */
    private fun mono(a: Float) = alpha(a) or if (p.light) 0 else 0xFFFFFF
    // One draw merges overlaps; the mockup draws each shape on its own, so where shapes overlap (shared hex edges,
    // stacked blocks) each gets its own Path to blend the same way. Elsewhere same-paint shapes share one Path.
    private val separate = p.pattern == "Hexagons" || p.pattern == "Blocks"
    private fun path(color: Int, width: Float = 0f): Path {
        val k = color to width
        if (!separate) paths.firstOrNull { it.first == k }?.let { return it.second }
        return Path().also { paths.add(k to it) }
    }
    private fun line(c: Int, w: Float, x1: Float, y1: Float, x2: Float, y2: Float) = path(c, w).run { moveTo(x1, y1); lineTo(x2, y2) }
    private fun ring(c: Int, w: Float, cx: Float, cy: Float, r: Float) = path(c, w).addCircle(cx, cy, r, Path.Direction.CW)

    init {
        glow = if (p.pattern == "Glow") Paint().apply {
            shader = RadialGradient(0.1f, 1.05f, 0.9f, acc(0.24f), acc(0f), Shader.TileMode.CLAMP)
        } else null
        build()
        paints = paths.map { (k, path) ->
            path to Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = k.first
                if (k.second > 0f) { style = Paint.Style.STROKE; strokeWidth = k.second } else style = Paint.Style.FILL
            }
        }
    }

    /** The mockup's seeded generator, so seeded patterns look the same everywhere. */
    private class Rnd(private var s: Long) { fun next(): Float { s = s * 16807 % 2147483647; return (s / 2147483647.0).toFloat() } }

    private fun build() = when (p.pattern) {
        "Rings" -> { var r = 90; while (r < 1300) { if (r == 510) ring(acc(0.25f), 2f, W * .86f, H * 1.12f, r.toFloat()) else ring(ink(.035f), 1.5f, W * .86f, H * 1.12f, r.toFloat()); r += 70 } }
        "Lines" -> { var x = -H; while (x < W) { line(if (x > -260 && x < -180) acc(.2f) else ink(.028f), 1.5f, x, H, x + H, 0f); x += 26 } }
        "Waves" -> {
            fun wave(c: Int, y: Float, amp: Float, ph: Float) = path(c).run {
                moveTo(0f, H); lineTo(0f, y)
                var x = 0; while (x <= W) { lineTo(x.toFloat(), y + sin(x / 150f + ph) * amp); x += 20 }
                lineTo(W, H); close()
            }
            wave(ink(.025f), H * .70f, 22f, 0f); wave(ink(.03f), H * .80f, 18f, 2f); wave(acc(.13f), H * .90f, 14f, 4f)
        }
        "Dots" -> {
            var y = 14f
            while (y < H) {
                var x = 14f
                while (x < W) {
                    val q = max(0f, 1 - hypot(x - W * .9f, y - H * .08f) / 420f)
                    // Accent alpha rounded to 2 places, as the mockup does, so nearby dots share a path.
                    if (q > 0.05f) path(acc(((0.06f + q * 0.3f) * 100).roundToInt() / 100f)).addCircle(x, y, 1.4f, Path.Direction.CW)
                    else path(ink(.05f)).addCircle(x, y, 1.4f, Path.Direction.CW)
                    x += 26
                }
                y += 26
            }
        }
        "Contours" -> for (i in 1 until 16) {
            val r0 = i * 48f
            val path = path(if (i == 4) acc(.25f) else ink(.035f), 1.5f)
            for (t in 0..64) {
                val th = t / 64.0 * 2 * PI
                val r = r0 + sin(th * 3 + i * .7) * 10 + cos(th * 5 - i) * 6
                val x = (W * .12 + cos(th) * r * 1.3).toFloat(); val y = (H * .15 + sin(th) * r).toFloat()
                if (t == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        "Grid" -> {
            var x = 0f; while (x < W) { line(ink(.025f), 1f, x, 0f, x, H); x += 48 }
            var y = 0f; while (y < H) { line(ink(.025f), 1f, 0f, y, W, y); y += 48 }
            for ((gx, gy) in listOf(48 * 17 to 48, 48 * 18 to 48, 48 * 18 to 96)) path(acc(.12f)).addRect(gx.toFloat(), gy.toFloat(), gx + 48f, gy + 48f, Path.Direction.CW)
        }
        "Hexagons" -> {
            val r = 30f; val hw = r * sqrt(3f)
            var row = -1
            while (row < H / (r * 1.5f) + 1) {
                var c = -1
                while (c < W / hw + 1) {
                    val cx = c * hw + if (row % 2 != 0) hw / 2 else 0f; val cy = row * r * 1.5f
                    val hex = Path()
                    for (j in 0 until 6) {
                        val t = PI / 3 * j + PI / 6
                        val x = (cx + cos(t) * r).toFloat(); val y = (cy + sin(t) * r).toFloat()
                        if (j == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
                    }
                    hex.close()
                    if ((row == 9 && c == 2) || (row == 10 && c == 1)) path(acc(.14f)).addPath(hex)
                    path(ink(.035f), 1.2f).addPath(hex)
                    c++
                }
                row++
            }
        }
        "Facets" -> {
            val rnd = Rnd(7); val cw = W / 9; val rh = H / 5
            for (i in 0 until 9) for (j in 0 until 5) {
                val x = i * cw; val y = j * rh
                val ac = i == 8 && j == 0
                path(if (ac) acc(.14f) else mono(rnd.next() * 0.03f * f)).run { moveTo(x, y); lineTo(x + cw, y); lineTo(x, y + rh); close() }
                path(mono(rnd.next() * 0.03f * f)).run { moveTo(x + cw, y); lineTo(x + cw, y + rh); lineTo(x, y + rh); close() }
            }
        }
        "Arcs" -> {
            // Two quarter-arcs of the cell's centred circle, picked at random per cell (the mockup's SVG arcs resolve to these).
            val rnd = Rnd(3); val c = 60f
            var x = 0f
            while (x < W) {
                var y = 0f
                while (y < H) {
                    val fl = rnd.next() > .5f
                    val o = RectF(x, y, x + c, y + c)
                    val path = path(if (x < c * 2 && y >= H - c * 2) acc(.3f) else ink(.035f), 1.5f)
                    if (fl) { path.arcTo(o, 270f, 90f, true); path.arcTo(o, 90f, 90f, true) }
                    else { path.arcTo(o, 180f, 90f, true); path.arcTo(o, 0f, 90f, true) }
                    y += c
                }
                x += c
            }
        }
        "Horizon" -> {
            path(acc(.13f)).addCircle(W * .5f, H * .98f, 150f, Path.Direction.CW)
            var y = H * .62f; var g = 26f
            while (y < H) { line(ink(.04f), 1.5f, 0f, y, W, y); g *= .82f; y += max(g, 5f) }
        }
        "Ripples" -> for ((cx, cy, n) in listOf(Triple(W * .08f, H * .1f, 7), Triple(W * .92f, H * .92f, 9))) for (i in 1..n) {
            if (i == 3 && cx < W / 2) ring(acc(.3f), 1.5f, cx, cy, i * 34f) else ring(ink(.035f), 1.5f, cx, cy, i * 34f)
        }
        "Chevrons" -> { var y = -40f; while (y < H + 40) {
            val path = path(if (y > H - 110 && y < H - 60) acc(.2f) else ink(.03f), 1.5f)
            var x = 0; while (x <= W) { val py = y + if ((x / 60) % 2 == 1) 18 else 0; if (x == 0) path.moveTo(0f, py) else path.lineTo(x.toFloat(), py); x += 60 }
            y += 30
        } }
        "Blocks" -> {
            val rnd = Rnd(11)
            repeat(22) {
                val w = 60 + rnd.next() * 220; val h = 40 + rnd.next() * 160; val x = rnd.next() * W - 40; val y = rnd.next() * H - 40
                path(mono(0.012f * f)).addRoundRect(x.roundToInt().toFloat(), y.roundToInt().toFloat(), (x.roundToInt() + w.roundToInt()).toFloat(), (y.roundToInt() + h.roundToInt()).toFloat(), 10f, 10f, Path.Direction.CW)
            }
            path(acc(.13f)).addRoundRect(W * .78f, H * .82f, W * .78f + 170, H * .82f + 120, 12f, 12f, Path.Direction.CW)
        }
        "Pinstripe" -> { var x = 12f; while (x < W) { line(ink(.022f), 1f, x, 0f, x, H); x += 12 }; path(acc(.5f)).addRect(0f, H - 6, W, H, Path.Direction.CW) }
        else -> Unit // Plain, and Glow (its shader is drawn separately)
    }

    override fun onBoundsChange(b: Rect) {
        bg.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), p.bgTop, p.bgBottom, Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, bg)
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        c.scale(b.width() / W, b.height() / H)
        for ((path, paint) in paints) c.drawPath(path, paint)
        glow?.let { c.scale(W, H); c.drawRect(0f, 0f, 1f, 1f, it) } // the mockup's gradient is relative to the box
        c.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

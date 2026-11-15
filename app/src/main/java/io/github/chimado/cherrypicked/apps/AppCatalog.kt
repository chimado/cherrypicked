package io.github.chimado.cherrypicked.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.home.AppEntry

class AppCatalog(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val font by lazy { Typeface.createFromAsset(ctx.assets, "Inter-Regular.ttf") }

    /** Leanback activities first so they win the per-package dedupe; our own package is left out. */
    fun apps(): List<AppEntry> {
        val out = LinkedHashMap<String, AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg != ctx.packageName) out.putIfAbsent(pkg, AppEntry(pkg, ri.loadLabel(pm).toString()))
            }
        }
        return out.values.toList()
    }

    private fun intentFor(pkg: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    /** Activity banner, then application banner, then the icon on a card with its label. */
    fun tileArt(pkg: String): Drawable {
        intentFor(pkg)?.component?.let { c ->
            try { pm.getActivityBanner(c)?.let { return it } } catch (_: PackageManager.NameNotFoundException) {}
        }
        try { pm.getApplicationBanner(pkg)?.let { return it } } catch (_: PackageManager.NameNotFoundException) {}
        val (icon, label) = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            ai.loadIcon(pm) to ai.loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null to pkg
        }
        return card(label, icon)
    }

    private val bitmaps = HashMap<String, Bitmap>()

    /**
     * [tileArt] rasterised once at tile pixel size and cached, so full-density banners are never kept
     * and a rebuild decodes nothing. Call [evict] when the package changes and [retain] after a rebuild.
     */
    fun tileBitmap(pkg: String, w: Int, h: Int): Bitmap = bitmaps.getOrPut(key(pkg, w, h)) {
        val d = tileArt(pkg)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { d.setBounds(0, 0, w, h); d.draw(Canvas(it)) }
    }

    fun evict(pkg: String) { bitmaps.keys.removeAll { it.startsWith("$pkg@") } }

    /** Drops every cached bitmap not in [keys] (as built by [key]). */
    fun retain(keys: Set<String>) { bitmaps.keys.retainAll(keys) }

    fun key(pkg: String, w: Int, h: Int) = "$pkg@${w}x$h"

    /** Fallback/synthetic tile art: [icon] centred on a #2a2b30 card with [label] underneath (or just the label). */
    fun card(label: String, icon: Drawable? = null): Drawable = CardDrawable(label, icon, font)

    fun launch(pkg: String): Boolean {
        val i = intentFor(pkg) ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private class CardDrawable(private val label: String, private val icon: Drawable?, font: Typeface) : Drawable() {
    private val bg = Paint().apply { color = 0xFF2A2B30.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font; color = Theme.TEXT; textAlign = Paint.Align.CENTER
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(b, bg)
        val h = b.height().toFloat()
        text.textSize = h * 0.13f
        val shown = if (text.measureText(label) > b.width() * 0.9f)
            label.take(text.breakText(label, true, b.width() * 0.9f, null).coerceAtLeast(1) - 1) + "…" else label
        if (icon == null) {
            c.drawText(shown, b.exactCenterX(), b.exactCenterY() - (text.ascent() + text.descent()) / 2, text)
            return
        }
        val s = (h * 0.42f).toInt()
        val left = b.centerX() - s / 2
        val top = b.top + (h * 0.16f).toInt()
        icon.setBounds(left, top, left + s, top + s)
        icon.draw(c)
        c.drawText(shown, b.exactCenterX(), b.top + h * 0.84f, text)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

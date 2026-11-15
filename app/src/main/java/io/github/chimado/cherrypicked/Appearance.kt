package io.github.chimado.cherrypicked

import io.github.chimado.cherrypicked.store.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Presets as approved in docs/design/appearance-mockup.html; the first of each list is the default.
val PATTERNS = listOf(
    "Plain", "Rings", "Lines", "Waves", "Dots", "Glow", "Contours", "Grid",
    "Hexagons", "Facets", "Arcs", "Horizon", "Ripples", "Chevrons", "Blocks", "Pinstripe",
)

val ACCENTS: Map<String, Int> = linkedMapOf(
    "Cherry" to 0xFFE0233F, "Coral" to 0xFFF2664A, "Tangerine" to 0xFFF58A1F, "Amber" to 0xFFF2A33A,
    "Gold" to 0xFFD9B433, "Lime" to 0xFF9BC53D, "Emerald" to 0xFF2FAE6B, "Teal" to 0xFF26B3A1,
    "Cyan" to 0xFF2AA9D6, "Blue" to 0xFF4A8DF0, "Indigo" to 0xFF6A6CF0, "Violet" to 0xFF9B6CF2,
    "Magenta" to 0xFFD64FB0, "Terracotta" to 0xFFC8643F, "Rust" to 0xFFB5502B, "Ochre" to 0xFFC99A2E,
    "Olive" to 0xFF8A9A3B, "Sage" to 0xFF8FAE8B, "Clay" to 0xFFB97A63, "Sand" to 0xFFCDB48A,
).mapValues { it.value.toInt() }

/** Base gradients, top to bottom. */
val BASES: Map<String, Pair<Int, Int>> = linkedMapOf(
    "Graphite" to (0xFF17181B to 0xFF0D0E10), "Midnight" to (0xFF131927 to 0xFF0A0D15),
    "Slate" to (0xFF1B2026 to 0xFF0F1216), "Plum" to (0xFF1C1620 to 0xFF0E0B11),
    "Forest" to (0xFF141B18 to 0xFF0A0F0D), "Moss" to (0xFF1B1D14 to 0xFF0E100A),
    "Espresso" to (0xFF1E1814 to 0xFF0F0C0A), "Umber" to (0xFF241A14 to 0xFF120D0A),
    "Paper" to (0xFFF4F1EA to 0xFFE6E1D6), "Fog" to (0xFFECEEF1 to 0xFFDADDE2),
    "Linen" to (0xFFEFE6D8 to 0xFFE0D3BF), "Sandstone" to (0xFFE6D6BF to 0xFFD4BF9F),
    "Sagebrush" to (0xFFE3E7DC to 0xFFCFD6C6), "Terracotta" to (0xFFECD6C9 to 0xFFDCBCA9),
).mapValues { (_, v) -> v.first.toInt() to v.second.toInt() }

private const val DARK_TEXT = 0xFF1D1E22.toInt()
private const val LIGHT_TEXT = 0xFFE9EAEE.toInt()

/** WCAG relative luminance. */
fun luminance(argb: Int): Double {
    fun ch(v: Int) = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * ch(argb shr 16 and 255) + 0.7152 * ch(argb shr 8 and 255) + 0.0722 * ch(argb and 255)
}

fun contrast(a: Int, b: Int): Double {
    val la = luminance(a); val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** Darkens [accent] in 2% steps until it reaches 3:1 against [baseTop] (WCAG minimum for UI components). */
fun readableAccent(accent: Int, baseTop: Int): Int {
    var i = 0
    var c = accent
    while (contrast(c, baseTop) < 3.0 && i < 50) {
        i++
        val k = 1 - 0.02 * i
        c = (0xFF shl 24) or ((((accent shr 16) and 255) * k).toInt() shl 16) or
            ((((accent shr 8) and 255) * k).toInt() shl 8) or (((accent and 255) * k).toInt())
    }
    return c
}

data class Palette(
    val pattern: String, val accentName: String, val baseName: String,
    val accent: Int, val bgTop: Int, val bgBottom: Int, val text: Int, val light: Boolean,
)

fun palette(c: Config): Palette {
    val pattern = c.pattern.takeIf { it in PATTERNS } ?: PATTERNS.first()
    val accentName = c.accent.takeIf { it in ACCENTS } ?: ACCENTS.keys.first()
    val baseName = c.base.takeIf { it in BASES } ?: BASES.keys.first()
    val (top, bottom) = BASES.getValue(baseName)
    val light = luminance(top) > 0.5
    return Palette(pattern, accentName, baseName, readableAccent(ACCENTS.getValue(accentName), top), top, bottom,
        if (light) DARK_TEXT else LIGHT_TEXT, light)
}

/** The next entry in [dir], wrapping; an unknown [current] counts as the default (index 0). */
fun <T> cycle(list: List<T>, current: T, dir: Int): T = list[(list.indexOf(current).coerceAtLeast(0) + dir).mod(list.size)]

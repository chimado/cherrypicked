package io.github.chimado.cherrypicked

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    @Test fun presetCounts() {
        assertEquals(16, PATTERNS.size); assertEquals(20, ACCENTS.size); assertEquals(14, BASES.size)
        assertEquals("Plain", PATTERNS.first()); assertEquals("Cherry", ACCENTS.keys.first()); assertEquals("Graphite", BASES.keys.first())
    }

    @Test fun everyAccentReadableOnEveryBase() {
        for (a in ACCENTS.values) for ((top, _) in BASES.values) assertTrue(contrast(readableAccent(a, top), top) >= 3.0)
    }

    @Test fun passingAccentsUnchanged() {
        for (a in ACCENTS.values) for ((top, _) in BASES.values.take(8)) assertEquals(a, readableAccent(a, top))
    }

    @Test fun darkensToSpecValues() {
        assertEquals(0xFFB77B2C.toInt(), readableAccent(0xFFF2A33A.toInt(), 0xFFF4F1EA.toInt())) // Amber on Paper
        assertEquals(0xFF87765B.toInt(), readableAccent(0xFFCDB48A.toInt(), 0xFFE6D6BF.toInt())) // Sand on Sandstone
    }

    @Test fun paletteDefaults() = assertEquals(
        Palette("Plain", "Cherry", "Graphite", 0xFFE0233F.toInt(), 0xFF17181B.toInt(), 0xFF0D0E10.toInt(), 0xFFE9EAEE.toInt(), false),
        palette(Config()),
    )

    @Test fun unknownNamesFallBack() = assertEquals(palette(Config()), palette(Config(pattern = "Nope", accent = "X", base = "Y")))

    @Test fun lightBaseFlipsText() {
        val p = palette(Config(base = "Paper"))
        assertEquals(0xFF1D1E22.toInt(), p.text); assertTrue(p.light)
    }

    @Test fun cycleWraps() {
        assertEquals("Pinstripe", cycle(PATTERNS, "Plain", -1)); assertEquals("Plain", cycle(PATTERNS, "Pinstripe", 1))
    }

    @Test fun cycleFromUnknownStartsAtDefault() = assertEquals("Rings", cycle(PATTERNS, "Nope", 1))
}

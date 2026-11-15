package io.github.chimado.cherrypicked

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun vwConvertsFractionOfWidth() {
        assertEquals(19.2f, Theme.vw(1f, 1920), 0.001f)
        assertEquals(422.4f, Theme.vw(22f, 1920), 0.001f)
    }

    @Test
    fun loadReportsChange() {
        Theme.load(Config())
        assertFalse(Theme.load(Config()))
        assertTrue(Theme.load(Config(accent = "Teal")))
        assertEquals(ACCENTS["Teal"], Theme.ACCENT)
        assertEquals((0x99 shl 24) or (Theme.TEXT and 0xFFFFFF), Theme.DIM)
        Theme.load(Config())
        assertEquals(0xFFE0233F.toInt(), Theme.ACCENT)
    }
}

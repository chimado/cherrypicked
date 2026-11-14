package io.github.chimado.cherrypicked

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun vwConvertsFractionOfWidth() {
        assertEquals(19.2f, Theme.vw(1f, 1920), 0.001f)
        assertEquals(422.4f, Theme.vw(22f, 1920), 0.001f)
    }
}

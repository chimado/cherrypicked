package io.github.chimado.cherrypicked.inputs

import io.github.chimado.cherrypicked.home.TvInput
import org.junit.Assert.assertEquals
import org.junit.Test

class InputOrderTest {
    private fun sorted(vararg l: String) = l.map { TvInput(it, it) }.sortedWith(naturalByLabel).map { it.label }

    @Test fun numbersSortNumerically() =
        assertEquals(listOf("AV", "HDMI 1", "HDMI 2", "HDMI 10"), sorted("HDMI 10", "AV", "HDMI 2", "HDMI 1"))

    @Test fun nonAsciiDigitDoesNotThrow() = // Java's \d is ASCII-only, so "١" lands in a text chunk
        assertEquals(listOf("1 av", "١ hdmi"), sorted("١ hdmi", "1 av"))
}

package io.github.chimado.cherrypicked.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemapTest {
    private val all = listOf(
        Action.OpenApp("a.b"), Action.OpenInput("in:1"), Action.Home, Action.Back,
        Action.QuickPanel, Action.SleepTimer, Action.ScreenOff, Action.Nothing,
    )

    @Test fun encodeDecodeRoundTrip() = all.forEach { assertEquals(it, decodeAction(it.encode())) }

    @Test fun decodeUnknownIsNull() {
        assertNull(decodeAction("bogus"))
        assertNull(decodeAction(""))
    }
}

package io.github.chimado.cherrypicked.a11y

import io.github.chimado.cherrypicked.keys.Press
import org.junit.Assert.assertEquals
import org.junit.Test

class TestHookTest {
    private fun s(at: Long, down: Boolean, repeat: Int = 0) = TestHook.Step(at, down, repeat)

    @Test fun single() = assertEquals(listOf(s(0, true), s(80, false)), TestHook.script(Press.SINGLE))

    @Test fun double() = assertEquals(
        listOf(s(0, true), s(80, false), s(200, true), s(280, false)), TestHook.script(Press.DOUBLE))

    @Test fun long() {
        val l = TestHook.script(Press.LONG)
        assertEquals(s(0, true), l.first())
        assertEquals(s(800, false), l.last())
        assertEquals((1..15).map { s(it * 50L, true, it) }, l.subList(1, l.size - 1))
    }
}

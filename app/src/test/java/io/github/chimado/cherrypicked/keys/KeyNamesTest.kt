package io.github.chimado.cherrypicked.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyNamesTest {
    @Test fun knownKeys() {
        assertEquals("Home", KeyNames.label(3))
        assertEquals("OK", KeyNames.label(23))
        assertEquals("Back", KeyNames.label(4))
    }

    @Test fun unknownFallsBack() {
        val s = KeyNames.label(999)
        assertTrue(s.isNotEmpty() && s.contains("999"))
    }
}

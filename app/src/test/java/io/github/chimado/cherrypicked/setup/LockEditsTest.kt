package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockEditsTest {
    @Test fun nicknameIsTrimmedAndCut() {
        assertEquals("Console", Config().nameInput("h", "  Console ").inputNames["h"])
        assertEquals(20, Config().nameInput("h", "y".repeat(40)).inputNames["h"]!!.length)
    }

    @Test fun blankNicknameRemovesIt() =
        assertEquals(emptyMap<String, String>(), Config(inputNames = mapOf("h" to "Console")).nameInput("h", "  ").inputNames)

    @Test fun lockStoresPinAndRow() {
        val c = Config().lock("0007", listOf("a", "b"))
        assertEquals("0007", c.pin)
        assertEquals(listOf("a", "b"), c.frozenRow)
    }

    @Test fun lockRefusesAPinThatIsNotFourDigits() {
        assertNull(Config().lock("12", emptyList()).pin)
        assertNull(Config().lock("12a4", emptyList()).pin)
        assertNull(Config().lock("12345", emptyList()).pin)
    }

    @Test fun unlockClearsPinAndRow() = assertEquals(Config(), Config(pin = "1234", frozenRow = listOf("a")).unlock())
}

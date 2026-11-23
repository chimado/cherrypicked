package io.github.chimado.cherrypicked.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LockFieldsTest {
    @Test fun roundTrip() {
        val c = Config(inputNames = mapOf("hdmi1" to "Console"), pin = "0420", frozenRow = listOf("b", "a"))
        assertEquals(c, Config.fromJson(c.toJson()))
    }

    @Test fun pinThatCannotBeTypedIsRefused() {
        assertThrows(ConfigFormatException::class.java) { Config.fromJson("{\"pin\":\"12\"}") }
        assertThrows(ConfigFormatException::class.java) { Config.fromJson("{\"pin\":\"abcd\"}") }
        assertThrows(ConfigFormatException::class.java) { Config.fromJson("{\"pin\":1234}") }
    }

    @Test fun longNicknameIsCutOnRead() =
        assertEquals("x".repeat(20), Config.fromJson("{\"inputNames\":{\"h\":\"${"x".repeat(30)}\"}}").inputNames["h"])

    @Test fun blankNicknameIsDroppedOnRead() =
        assertEquals(mapOf("b" to "TV"), Config.fromJson("{\"inputNames\":{\"a\":\"  \",\"b\":\" TV \"}}").inputNames)

    @Test fun importSummarySaysLocked() {
        val ok = checkImport(Config(pin = "1234").toJson(), emptySet()) { it } as ImportCheck.Ok
        assertTrue("Simple mode: On" in ok.summary)
    }

    @Test fun importRefusesHugeFrozenRow() =
        assertTrue(checkImport(Config(frozenRow = List(MAX_IMPORT_ENTRIES + 1) { "p$it" }).toJson(), emptySet()) { it } is ImportCheck.Refused)
}

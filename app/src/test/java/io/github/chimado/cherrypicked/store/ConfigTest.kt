package io.github.chimado.cherrypicked.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigTest {
    @Test fun roundTrip() {
        val c = Config(1, listOf("a", "b"), setOf("h"), "m", true, setOf("fav:x", "main:y"), true)
        assertEquals(c, Config.fromJson(c.toJson()))
    }

    @Test fun missingKeysUseDefaults() = assertEquals(Config(), Config.fromJson("{\"version\":1}"))

    @Test fun unknownKeysAreIgnored() =
        assertEquals(listOf("a"), Config.fromJson("{\"version\":1,\"future\":true,\"favorites\":[\"a\"]}").favorites)

    @Test fun wrongTypeThrows() {
        assertThrows(ConfigFormatException::class.java) { Config.fromJson("{\"favorites\":5}") }
    }
}

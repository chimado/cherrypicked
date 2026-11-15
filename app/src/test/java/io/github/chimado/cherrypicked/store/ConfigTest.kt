package io.github.chimado.cherrypicked.store

import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.keys.forKey
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

    @Test fun remapsRoundTrip() {
        val c = Config(remaps = listOf(Remap(3, Press.LONG, Action.QuickPanel), Remap(85, Press.DOUBLE, Action.OpenApp("x.y"))))
        assertEquals(c, Config.fromJson(c.toJson()))
    }

    @Test fun missingRemapsUseDefaults() = assertEquals(DEFAULT_REMAPS, Config.fromJson("{\"version\":1}").remaps)

    @Test fun unknownRemapEntrySkipped() {
        val j = "{\"remaps\":[{\"key\":1,\"press\":\"LONG\",\"action\":\"zzz\"}," +
            "{\"key\":2,\"press\":\"TRIPLE\",\"action\":\"home\"},{\"key\":4,\"press\":\"SINGLE\",\"action\":\"back\"}]}"
        assertEquals(listOf(Remap(4, Press.SINGLE, Action.Back)), Config.fromJson(j).remaps)
        assertThrows(ConfigFormatException::class.java) { Config.fromJson("{\"remaps\":5}") }
    }

    @Test fun forKeyGroupsByPress() {
        val l = listOf(Remap(3, Press.LONG, Action.QuickPanel), Remap(3, Press.DOUBLE, Action.Back), Remap(4, Press.SINGLE, Action.Home))
        assertEquals(mapOf(Press.LONG to Action.QuickPanel, Press.DOUBLE to Action.Back), l.forKey(3))
    }

    private fun remaps(body: String) = Config.fromJson("{\"remaps\":$body}").remaps

    @Test fun emptyRemapsStayEmpty() = assertEquals(emptyList<Remap>(), remaps("[]"))

    @Test fun malformedEntriesSkipped() = assertEquals(emptyList<Remap>(), remaps(
        "[5,\"x\",null,{\"press\":\"LONG\",\"action\":\"home\"}," +
            "{\"key\":\"abc\",\"press\":\"LONG\",\"action\":\"home\"},{\"key\":null,\"press\":\"LONG\",\"action\":\"home\"}]"))

    @Test fun duplicateKeyPressLastWins() = assertEquals(listOf(Remap(3, Press.LONG, Action.Back)), remaps(
        "[{\"key\":3,\"press\":\"LONG\",\"action\":\"home\"},{\"key\":3,\"press\":\"LONG\",\"action\":\"back\"}]"))
}

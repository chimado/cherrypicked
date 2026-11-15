package io.github.chimado.cherrypicked.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun missingFileGivesDefaults() = assertEquals(Config(), ConfigStore(tmp.root).load())

    @Test fun saveThenLoad() {
        val s = ConfigStore(tmp.root)
        val c = Config(favorites = listOf("a"), mainApp = "m")
        s.save(c)
        assertEquals(c, s.load())
    }

    @Test fun corruptFileFallsBackAndIsKept() {
        File(tmp.root, "config.json").writeText("{nope")
        assertEquals(Config(), ConfigStore(tmp.root).load())
        assertEquals("{nope", File(tmp.root, "config.json.bad").readText())
    }

    @Test fun saveIsAtomic() {
        ConfigStore(tmp.root).save(Config())
        assertFalse(File(tmp.root, "config.json.tmp").exists())
        assertTrue(File(tmp.root, "config.json").exists())
    }

    @Test fun failedRenameRemovesTmp() {
        File(tmp.root, "config.json/x").mkdirs() // a non-empty directory cannot be renamed over
        assertThrows(java.io.IOException::class.java) { ConfigStore(tmp.root).save(Config()) }
        assertFalse(File(tmp.root, "config.json.tmp").exists())
    }

    @Test fun newerVersionFallsBackAndIsKept() {
        File(tmp.root, "config.json").writeText("{\"version\":99}")
        assertEquals(Config(), ConfigStore(tmp.root).load())
        assertTrue(File(tmp.root, "config.json.bad").exists())
    }

    @Test fun emptyFileFallsBack() {
        File(tmp.root, "config.json").writeText("")
        assertEquals(Config(), ConfigStore(tmp.root).load())
    }

    @Test fun unreadablePathFallsBack() {
        File(tmp.root, "config.json").mkdir()
        assertEquals(Config(), ConfigStore(tmp.root).load())
    }
}

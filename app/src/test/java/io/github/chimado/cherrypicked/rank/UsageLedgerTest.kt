package io.github.chimado.cherrypicked.rank

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UsageLedgerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val t = 1_000_000_000_000L
    private fun file() = File(tmp.root, "usage.json")

    @Test fun freshLaunchScoresOne() {
        val l = UsageLedger(file()); l.recordLaunch("a", t)
        assertEquals(1.0, l.scores(t)["a"]!!, 1e-9)
    }

    @Test fun halfLifeHalves() {
        val l = UsageLedger(file()); l.recordLaunch("a", t)
        assertEquals(0.5, l.scores(t + HALF_LIFE_MS)["a"]!!, 1e-9)
    }

    @Test fun persistsAcrossInstances() {
        UsageLedger(file()).recordLaunch("a", t)
        assertEquals(1.0, UsageLedger(file()).scores(t)["a"]!!, 1e-9)
    }

    @Test fun keepsOnlyNewest50() {
        val l = UsageLedger(file())
        for (i in 0 until 60) l.recordLaunch("a", t + i)
        val arr = JSONObject(file().readText()).getJSONArray("a")
        assertEquals(MAX_EVENTS_PER_APP, arr.length())
        assertEquals(t + 10, arr.getLong(0))
    }

    @Test fun foregroundHoursAdd() {
        val s = UsageLedger(file()).scores(t, mapOf("b" to 3 * 3600_000L))
        assertEquals(3.0, s["b"]!!, 1e-9)
    }

    @Test fun partialForegroundHoursCount() {
        val s = UsageLedger(file()).scores(t, mapOf("b" to 90 * 60_000L))
        assertEquals(1.5, s["b"]!!, 1e-9)
    }

    @Test fun corruptFileReadsEmpty() {
        file().writeText("{not json")
        val l = UsageLedger(file())
        assertTrue(l.scores(t).isEmpty())
        l.recordLaunch("a", t) // recovers
        assertEquals(1.0, l.scores(t)["a"]!!, 1e-9)
    }
}

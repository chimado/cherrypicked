package io.github.chimado.cherrypicked.store

import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigFileTest {
    private val labels = mapOf("a" to "A", "b" to "B", "input:hw1" to "Input 1")
    private fun label(e: String) = labels[e] ?: e

    @Test fun roundTrip() {
        val c = Config(
            favorites = listOf("a", "b"), hidden = setOf("x"), mainApp = "a", autoOpen = true,
            remaps = listOf(Remap(3, Press.LONG, Action.QuickPanel), Remap(183, Press.SINGLE, Action.OpenApp("b"))),
        )
        val r = checkImport(c.toJson(), setOf("a", "b"), ::label) as ImportCheck.Ok
        assertEquals(c, r.config)
        assertEquals(listOf("Favorites: A, B", "Main app: A", "Open main app on start: On", "Hidden apps: 1", "Buttons: 2"), r.summary)
    }

    @Test fun marksMissingApps() {
        val c = Config(favorites = listOf("a", "b", "input:hw1"), mainApp = "b")
        val r = checkImport(c.toJson(), setOf("a"), ::label) as ImportCheck.Ok
        assertEquals("Favorites: A, B (not installed), Input 1", r.summary[0])
        assertEquals("Main app: B (not installed)", r.summary[1])
    }

    @Test fun refusesGarbage() {
        assertEquals(ImportCheck.Refused("This file isn't a Cherrypicked setup"), checkImport("not json", emptySet(), ::label))
        assertEquals(ImportCheck.Refused("This file isn't a Cherrypicked setup"), checkImport("""{"favorites":5}""", emptySet(), ::label))
    }

    @Test fun refusesNewer() {
        assertEquals(ImportCheck.Refused("This file is from a newer Cherrypicked"), checkImport("""{"version":99}""", emptySet(), ::label))
    }

    @Test fun refusesNoWayHome() {
        val c = Config(remaps = listOf(Remap(3, Press.SINGLE, Action.Nothing)))
        assertEquals(ImportCheck.Refused("This setup leaves no way back to Cherrypicked"), checkImport(c.toJson(), emptySet(), ::label))
    }
}

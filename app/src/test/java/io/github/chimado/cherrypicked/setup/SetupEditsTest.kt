package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupEditsTest {
    private val c = Config(favorites = listOf("a", "b", "c"))

    @Test fun moveEdgesAreNoOps() {
        assertEquals(c, c.moveFavorite("a", -1))
        assertEquals(c, c.moveFavorite("c", 1))
        assertEquals(c, c.moveFavorite("zzz", 1))
    }

    @Test fun moveSwaps() {
        assertEquals(listOf("b", "a", "c"), c.moveFavorite("a", 1).favorites)
        assertEquals(listOf("a", "c", "b"), c.moveFavorite("c", -1).favorites)
    }

    @Test fun addCapsAtFiveAndRefusesDuplicates() {
        val full = c.addFavorite("d").addFavorite("e")
        assertEquals(5, full.favorites.size)
        assertEquals(full, full.addFavorite("f"))
        assertEquals(c, c.addFavorite("a"))
    }

    @Test fun removeAndHide() {
        assertEquals(listOf("a", "c"), c.removeFavorite("b").favorites)
        assertEquals(setOf("x"), c.toggleHidden("x").hidden)
        assertEquals(emptySet<String>(), c.toggleHidden("x").toggleHidden("x").hidden)
    }

    @Test fun mainAppTurnsAutoOpenOn() {
        assertEquals(Config(favorites = c.favorites, mainApp = "m", autoOpen = true), c.setMainApp("m"))
    }

    @Test fun autoOpenNeedsMainApp() {
        assertEquals(c, c.toggleAutoOpen())
        assertEquals(false, c.setMainApp("m").toggleAutoOpen().autoOpen)
    }
}

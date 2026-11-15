package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.DEFAULT_REMAPS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val menu = 82 // KEYCODE_MENU
    private val r = Config(remaps = listOf(Remap(menu, Press.SINGLE, Action.Home), Remap(menu, Press.LONG, Action.Back)))

    @Test fun putRemapReplacesSameKeyPress() {
        val out = r.putRemap(Remap(menu, Press.SINGLE, Action.QuickPanel)).remaps
        assertEquals(2, out.size)
        assertTrue(Remap(menu, Press.SINGLE, Action.QuickPanel) in out)
        assertTrue(Remap(menu, Press.LONG, Action.Back) in out)
        assertEquals(3, r.putRemap(Remap(menu, Press.DOUBLE, Action.Nothing)).remaps.size)
    }

    @Test fun putRemapRefusesEmptyIds() { // "app:" / "input:" would not survive a save
        assertEquals(r, r.putRemap(Remap(menu, Press.SINGLE, Action.OpenApp(""))))
        assertEquals(r, r.putRemap(Remap(menu, Press.SINGLE, Action.OpenInput(""))))
    }

    @Test fun removeRemap() {
        assertEquals(listOf(Remap(menu, Press.SINGLE, Action.Home)), r.removeRemap(menu, Press.LONG).remaps)
        assertEquals(emptyList<Remap>(), r.removeRemap(menu, Press.SINGLE).remaps) // the long can't stand alone on Menu
        val home = Config(remaps = listOf(Remap(3, Press.SINGLE, Action.Back), Remap(3, Press.LONG, Action.QuickPanel)))
        assertEquals(listOf(Remap(3, Press.LONG, Action.QuickPanel)), home.removeRemap(3, Press.SINGLE).remaps)
        assertEquals(r, r.removeRemap(menu, Press.DOUBLE))
    }

    @Test fun canRemapLongOnHomeWithoutSingle() {
        assertTrue(canRemap(3, Press.LONG, emptyList()))
        assertTrue(canRemap(4, Press.DOUBLE, emptyList()))
    }

    @Test fun cannotRemapLongOnOtherKeyWithoutSingle() {
        assertFalse(canRemap(menu, Press.LONG, emptyList()))
        assertFalse(canRemap(menu, Press.DOUBLE, listOf(Remap(19, Press.SINGLE, Action.Home))))
        assertTrue(canRemap(menu, Press.SINGLE, emptyList()))
    }

    @Test fun canRemapLongAfterSingle() {
        assertTrue(canRemap(menu, Press.LONG, listOf(Remap(menu, Press.SINGLE, Action.Home))))
        assertTrue(canRemap(menu, Press.DOUBLE, listOf(Remap(menu, Press.SINGLE, Action.Home))))
    }

    @Test fun cannotRemapSingleOnOk() {
        for (k in listOf(23, 66, 160)) Press.entries.forEach { p -> assertFalse("$k $p", canRemap(k, p, emptyList())) }
    }

    @Test fun cannotRemapSingleOnDpad() {
        for (k in 19..22) Press.entries.forEach { p -> assertFalse("$k $p", canRemap(k, p, listOf(Remap(k, Press.SINGLE, Action.Home)))) }
    }

    @Test fun putRemapRefusesWhatCanRemapRefuses() {
        assertEquals(r, r.putRemap(Remap(23, Press.SINGLE, Action.Home)))
        assertEquals(r, r.putRemap(Remap(19, Press.SINGLE, Action.Home)))
        assertEquals(r, r.putRemap(Remap(19, Press.LONG, Action.Home)))
        val noSingle = Config(remaps = emptyList())
        assertEquals(noSingle, noSingle.putRemap(Remap(menu, Press.LONG, Action.Home)))
    }

    @Test fun resetRemaps() {
        assertEquals(DEFAULT_REMAPS, r.resetRemaps().remaps)
        assertEquals(r.favorites, r.resetRemaps().favorites)
    }
}

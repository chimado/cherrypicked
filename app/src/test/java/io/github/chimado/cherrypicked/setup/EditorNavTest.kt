package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.setup.Nav.BACK
import io.github.chimado.cherrypicked.setup.Nav.DOWN
import io.github.chimado.cherrypicked.setup.Nav.LEFT
import io.github.chimado.cherrypicked.setup.Nav.OK
import io.github.chimado.cherrypicked.setup.Nav.RIGHT
import io.github.chimado.cherrypicked.setup.Nav.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorNavTest {
    private fun nav(favs: List<String> = listOf("a", "input:h", "c"), shelf: List<String> = listOf("x", "y")) = EditorNav(favs, shelf)
    private fun EditorNav.keys(vararg k: Nav) = k.map(::key).last()

    @Test fun startsOnTheFirstFavorite() = nav().run { assertEquals(Zone.FAVS, zone); assertEquals(0, fav) }
    @Test fun startsOnTheShelfWithNoFavorites() = assertEquals(Zone.SHELF, nav(favs = emptyList()).zone)
    @Test fun backLeaves() = assertEquals(Edit.Exit, nav().key(BACK))
    @Test fun backOnTheShelfLeaves() = assertEquals(Edit.Exit, nav().keys(DOWN, BACK))

    @Test fun leftAndRightStopAtTheEnds() = nav().run {
        keys(LEFT); assertEquals(0, fav)
        keys(RIGHT, RIGHT, RIGHT); assertEquals(2, fav)
    }

    @Test fun pickedUpTileMoves() = nav().run {
        assertNull(key(OK)); assertTrue(held)
        assertEquals(Edit.Move("a", 1), key(RIGHT)); assertEquals(1, fav)
        assertNull(key(OK)); assertFalse(held)
    }

    @Test fun pickedUpTileStopsAtTheEnds() = nav().run { assertNull(keys(OK, LEFT)); assertEquals(0, fav) }
    @Test fun backDropsWithoutLeaving() = nav().run { assertNull(keys(OK, BACK)); assertFalse(held) }

    @Test fun appHasNoRename() = assertEquals(listOf(Opt.MAIN, Opt.REMOVE), nav().options)
    @Test fun inputHasRename() = nav().run { key(RIGHT); assertEquals(listOf(Opt.RENAME, Opt.MAIN, Opt.REMOVE), options) }

    @Test fun optionsAreReachedFromAPickedUpTile() = nav().run {
        keys(RIGHT, OK, DOWN); assertEquals(Zone.OPTIONS, zone)
        assertEquals(Edit.Rename("input:h"), key(OK))
        assertEquals(Edit.Main("input:h"), keys(RIGHT, OK))
        assertEquals(Edit.Remove("input:h"), keys(RIGHT, RIGHT, OK))
        assertFalse(held); assertEquals(Zone.FAVS, zone)
    }

    @Test fun upFromOptionsKeepsTheTileHeld() = nav().run { keys(OK, DOWN, UP); assertEquals(Zone.FAVS, zone); assertTrue(held) }
    @Test fun backFromOptionsDrops() = nav().run { assertNull(keys(OK, DOWN, BACK)); assertEquals(Zone.FAVS, zone); assertFalse(held) }

    @Test fun shelfAdds() = nav().run { assertEquals(Edit.Add("y"), keys(DOWN, RIGHT, RIGHT, OK)) }
    @Test fun shelfAtFiveSaysFull() = assertEquals(Edit.Full, nav(favs = listOf("a", "b", "c", "d", "e")).keys(DOWN, OK))
    @Test fun downWithAnEmptyShelfStays() = assertEquals(Zone.FAVS, nav(shelf = emptyList()).apply { key(DOWN) }.zone)
    @Test fun upFromTheShelfNeedsAFavorite() = assertEquals(Zone.SHELF, nav(favs = emptyList()).apply { key(UP) }.zone)

    @Test fun removingTheLastFavoriteLandsOnTheShelf() = nav(favs = listOf("a")).run {
        assertEquals(Edit.Remove("a"), keys(OK, DOWN, RIGHT, OK))
        sync(emptyList(), listOf("a", "x", "y"))
        assertEquals(Zone.SHELF, zone); assertFalse(held)
    }

    @Test fun removingTheRightmostKeepsFocusInRange() = nav().run {
        keys(RIGHT, RIGHT); sync(listOf("a", "input:h"), listOf("c", "x", "y")); assertEquals(1, fav)
    }

    @Test fun addingTheLastShelfItemMovesUp() = nav(shelf = listOf("x")).run {
        keys(DOWN, OK); sync(listOf("a", "input:h", "c", "x"), emptyList()); assertEquals(Zone.FAVS, zone)
    }

    @Test fun nothingToEditDoesNotCrash() = nav(emptyList(), emptyList()).run { assertNull(keys(OK, LEFT, UP, DOWN)); assertEquals(Edit.Exit, key(BACK)) }
}

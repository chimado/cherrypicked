package io.github.chimado.cherrypicked.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupFocusTest {
    // header, section, focusable rows; null = plain header, "#key" = section header.
    private val main = listOf(null, "#favorites", "fav:a", "fav:b", "add-fav", "#autoopen", null, "main", "#hidden", "hide:a", "hide:b")

    @Test fun keepsPreviousIdWhereverItMoved() {
        assertEquals(3, focusTarget(main, "fav:b", null))
        assertEquals(2, focusTarget(listOf(null, "#favorites", "fav:b", "fav:a", "add-fav"), "fav:b", null)) // moved up
    }

    @Test fun missingIdFallsBackToItsSection() = assertEquals(2, focusTarget(main, "fav:gone", "favorites"))

    @Test fun sectionWithoutFocusableRowTakesTheNextOne() =
        assertEquals(7, focusTarget(main, null, "autoopen"))

    @Test fun noHintFocusesFirstFocusable() {
        assertEquals(2, focusTarget(main, null, null))
        assertEquals(1, focusTarget(listOf(null, "pick:a", "pick:b"), "pick:zz", null))
    }

    @Test fun pickerOpensAtCurrentMain() = assertEquals(2, focusTarget(listOf(null, "pick:a", "pick:b"), "pick:b", null))

    @Test fun sectionHeaderIsNeverATarget() = assertEquals(2, focusTarget(main, "#favorites", null))

    @Test fun nothingFocusable() = assertEquals(-1, focusTarget(listOf(null, "#x"), null, "x"))
}

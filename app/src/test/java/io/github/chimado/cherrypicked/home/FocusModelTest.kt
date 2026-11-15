package io.github.chimado.cherrypicked.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModelTest {
    private val up = 19; private val down = 20; private val left = 21; private val right = 22
    private fun m() = FocusModel(
        listOf(3, 4),
        listOf(listOf(480f, 960f, 1440f), listOf(627f, 849f, 1071f, 1293f)),
    )

    @Test fun leftRightStopAtEnds() {
        val f = m()
        assertFalse(f.move(left))
        assertTrue(f.move(right)); assertTrue(f.move(right))
        assertFalse(f.move(right))
        assertEquals(2, f.col)
    }

    @Test fun downPicksNearestCenter() {
        val f = m(); f.move(right)
        assertTrue(f.move(down))
        assertEquals(1, f.row); assertEquals(1, f.col)
        assertFalse(f.move(down))
    }

    @Test fun upReturnsNearest() {
        val f = m(); f.move(down); f.move(right); f.move(right) // row1 col2 = 1071
        assertTrue(f.move(up))
        assertEquals(0, f.row); assertEquals(1, f.col)
        assertFalse(f.move(up))
    }

    @Test fun resetGoesToFirstTile() {
        val f = m(); f.move(right); f.move(down); f.reset()
        assertEquals(0, f.row); assertEquals(0, f.col)
    }

    @Test fun worksOverThreeRows() {
        val f = FocusModel(listOf(1, 1, 1), listOf(listOf(0f), listOf(0f), listOf(0f)))
        f.move(down); f.move(down)
        assertEquals(2, f.row)
    }
}

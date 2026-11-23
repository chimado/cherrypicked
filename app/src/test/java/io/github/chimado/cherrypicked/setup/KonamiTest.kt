package io.github.chimado.cherrypicked.setup

import android.view.KeyEvent.KEYCODE_BACK as B
import android.view.KeyEvent.KEYCODE_DPAD_CENTER as A
import android.view.KeyEvent.KEYCODE_DPAD_DOWN as D
import android.view.KeyEvent.KEYCODE_DPAD_LEFT as L
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT as R
import android.view.KeyEvent.KEYCODE_DPAD_UP as U
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KonamiTest {
    private val code = listOf(U, U, D, D, L, R, L, R, B, A)
    private fun feed(keys: List<Int>, k: Konami = Konami()) = keys.map(k::feed)

    @Test fun fullCodeFiresOnTheLastKeyOnly() = assertEquals(List(9) { false } + true, feed(code))
    @Test fun wrongKeyRestarts() = assertFalse(true in feed(listOf(U, U, D, L) + code.drop(4)))
    @Test fun worksAfterAFalseStart() = assertTrue(feed(listOf(U, D, R) + code).last())
    @Test fun extraUpAtTheStartStillWorks() = assertTrue(feed(listOf(U) + code).last())
    @Test fun enterCountsAsOk() = assertTrue(feed(code.dropLast(1) + android.view.KeyEvent.KEYCODE_ENTER).last())

    @Test fun armedOnlyBeforeBack() {
        val k = Konami()
        assertFalse(k.armed)
        feed(code.take(7), k); assertFalse(k.armed)
        k.feed(R); assertTrue(k.armed)
        k.feed(B); assertFalse(k.armed)
    }

    @Test fun firesAgainAfterFiring() { val k = Konami(); feed(code, k); assertTrue(feed(code, k).last()) }
}

package io.github.chimado.cherrypicked.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {
    private val now = 1_000_000_000L
    private val min = 60_000L

    @Test fun cyclesThroughPresets() {
        var end: Long? = null
        val got = mutableListOf<Long>()
        repeat(5) { end = SleepTimer.next(end, now); got += (end!! - now) / min }
        assertEquals(listOf(15L, 30L, 60L, 90L, 120L), got)
    }

    @Test fun cycleFromLastPresetTurnsOff() = assertNull(SleepTimer.next(now + 120 * min, now))

    @Test fun cycleAfterSomeTimeElapsedStillAdvances() =
        assertEquals(now + 30 * min, SleepTimer.next(now + 15 * min - 10_000, now))

    @Test fun positionIsSmallestPresetAtLeastRemaining() {
        assertNull(SleepTimer.next(now + 100 * min, now))
        assertEquals(now + 60 * min, SleepTimer.next(now + 29 * min, now))
        assertEquals(now + 30 * min, SleepTimer.next(now + 10 * min, now))
    }

    @Test fun restoreFutureRearms() =
        assertEquals(RestoreResult.Arm(now + min), SleepTimer.restore(now + min, now))

    @Test fun restoreRecentlyExpiredFiresNow() =
        assertEquals(RestoreResult.FireNow, SleepTimer.restore(now - 30_000, now))

    @Test fun restoreLongExpiredDrops() =
        assertEquals(RestoreResult.Drop, SleepTimer.restore(now - 10 * min, now))

    @Test fun restoreNullIsDrop() = assertEquals(RestoreResult.Drop, SleepTimer.restore(null, now))

    @Test fun remaining() {
        assertNull(SleepTimer.remainingMs(null, now))
        assertEquals(5 * min, SleepTimer.remainingMs(now + 5 * min, now))
        assertEquals(0L, SleepTimer.remainingMs(now - 1, now))
    }
}

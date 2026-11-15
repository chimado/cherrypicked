package io.github.chimado.cherrypicked.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemapEngineTest {
    private val HOME = 3
    private val BACK = 4
    private val OK = 23
    private val UP = 19
    private val app = Action.OpenApp("a.b")
    private val app2 = Action.OpenApp("c.d")

    private fun RemapEngine.down(k: Int, t: Long, repeat: Int = 0) = onKey(k, true, repeat, false, t)
    private fun RemapEngine.up(k: Int, t: Long, canceled: Boolean = false) = onKey(k, false, 0, canceled, t)
    private fun fire(a: Action) = Effect.Fire(a)
    private fun fires(effects: List<Effect>) = effects.filterIsInstance<Effect.Fire>()

    @Test fun unmappedPassesThrough() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app)))
        assertEquals(Decision(false, emptyList()), e.down(UP, 0))
        assertEquals(Decision(false, emptyList()), e.up(UP, 10))
        // Home and Back with no entries pass through too
        assertEquals(Decision(false, emptyList()), e.down(HOME, 20))
        assertEquals(Decision(false, emptyList()), e.up(HOME, 30))
        assertEquals(emptyList<Effect>(), e.onTimer(10_000))
    }

    @Test fun homeSingleDefaultsToHome() {
        val e = RemapEngine(listOf(Remap(HOME, Press.LONG, Action.QuickPanel)))
        assertTrue(e.down(HOME, 0).consume)
        val d = e.up(HOME, 100)
        assertTrue(d.consume)
        assertEquals(listOf(fire(Action.Home)), fires(d.effects))
    }

    @Test fun singleFiresOnUp() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app)))
        assertEquals(Decision(true, emptyList()), e.down(OK, 0))
        assertEquals(Decision(true, listOf(fire(app))), e.up(OK, 80))
    }

    @Test fun canceledUpFiresNothing() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app)))
        assertTrue(e.down(OK, 0).consume)
        val d = e.up(OK, 80, canceled = true)
        assertTrue(d.consume)
        assertEquals(emptyList<Effect.Fire>(), fires(d.effects))
    }

    @Test fun longFiresAfter500() {
        val e = RemapEngine(listOf(Remap(HOME, Press.LONG, Action.QuickPanel)))
        assertEquals(Decision(true, listOf(Effect.Schedule(LONG_PRESS_MS))), e.down(HOME, 0))
        assertEquals(emptyList<Effect>(), e.onTimer(499)) // early timer ignored
        assertEquals(listOf(fire(Action.QuickPanel)), e.onTimer(500))
        assertEquals(Decision(true, emptyList()), e.up(HOME, 700))
    }

    @Test fun shortPressWithLongMappedFiresSingle() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app), Remap(OK, Press.LONG, app2)))
        e.down(OK, 0)
        val d = e.up(OK, 200)
        assertEquals(Decision(true, listOf(Effect.CancelSchedule, fire(app))), d)
        assertEquals(emptyList<Effect>(), e.onTimer(500)) // stale timer
    }

    @Test fun holdFiresLongOnce() {
        val e = RemapEngine(listOf(Remap(HOME, Press.LONG, Action.QuickPanel)))
        val all = mutableListOf<Effect>()
        all += e.down(HOME, 0).effects
        var t = 50L
        var rep = 1
        while (t <= 1500) {
            if (t >= 500 && rep == 10) all += e.onTimer(500)
            val d = e.down(HOME, t, rep++)
            assertTrue(d.consume)
            all += d.effects
            t += 50
        }
        val u = e.up(HOME, 1510)
        assertTrue(u.consume)
        all += u.effects
        all += e.onTimer(2000)
        assertEquals(listOf(fire(Action.QuickPanel)), fires(all))
    }

    @Test fun doubleWithin300FiresDouble() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app), Remap(OK, Press.DOUBLE, app2)))
        e.down(OK, 0)
        assertEquals(Decision(true, listOf(Effect.Schedule(100 + DOUBLE_PRESS_MS))), e.up(OK, 100))
        assertEquals(Decision(true, listOf(Effect.CancelSchedule)), e.down(OK, 250))
        assertEquals(Decision(true, listOf(fire(app2))), e.up(OK, 320))
        assertEquals(emptyList<Effect>(), e.onTimer(400)) // stale timer after double
    }

    @Test fun singleAfterDoubleWindow() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app), Remap(OK, Press.DOUBLE, app2)))
        e.down(OK, 0)
        e.up(OK, 100)
        assertEquals(emptyList<Effect>(), e.onTimer(399))
        assertEquals(listOf(fire(app)), e.onTimer(400))
        // next press starts fresh
        e.down(OK, 1000)
        assertEquals(listOf(Effect.Schedule(1100 + DOUBLE_PRESS_MS)), e.up(OK, 1100).effects)
    }

    @Test fun otherKeyFlushesPendingSingle() {
        val e = RemapEngine(listOf(
            Remap(OK, Press.SINGLE, app), Remap(OK, Press.DOUBLE, app2),
            Remap(HOME, Press.SINGLE, Action.QuickPanel),
        ))
        e.down(OK, 0)
        e.up(OK, 100)
        val d = e.down(HOME, 200)
        assertTrue(d.consume)
        assertEquals(listOf(fire(app)), fires(d.effects))
        assertEquals(emptyList<Effect>(), e.onTimer(400)) // old window no longer pending
        assertEquals(listOf(fire(Action.QuickPanel)), fires(e.up(HOME, 250).effects))
    }

    @Test fun unmappedKeyStillFlushesPendingSingle() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app), Remap(OK, Press.DOUBLE, app2)))
        e.down(OK, 0)
        e.up(OK, 100)
        val d = e.down(UP, 200)
        assertFalse(d.consume)
        assertEquals(listOf(fire(app)), fires(d.effects))
        assertEquals(Decision(false, emptyList()), e.up(UP, 250))
        assertEquals(emptyList<Effect>(), e.onTimer(400))
    }

    @Test fun backPassesThroughUnlessExtended() {
        val plain = RemapEngine(listOf(Remap(HOME, Press.LONG, Action.QuickPanel)))
        assertEquals(Decision(false, emptyList()), plain.down(BACK, 0))
        assertEquals(Decision(false, emptyList()), plain.up(BACK, 50))

        val ext = RemapEngine(listOf(Remap(BACK, Press.DOUBLE, Action.QuickPanel)))
        assertTrue(ext.down(BACK, 0).consume)
        ext.up(BACK, 50)
        assertEquals(listOf(fire(Action.Back)), ext.onTimer(350))
    }

    @Test fun setRemapsTakesEffect() {
        val e = RemapEngine(emptyList())
        assertFalse(e.down(OK, 0).consume)
        assertFalse(e.up(OK, 10).consume)
        e.setRemaps(listOf(Remap(OK, Press.SINGLE, app)))
        assertTrue(e.down(OK, 100).consume)
        assertEquals(listOf(fire(app)), e.up(OK, 150).effects)
        e.setRemaps(emptyList())
        assertFalse(e.down(OK, 200).consume)
    }

    @Test fun consumedDownKeepsUpConsumedAcrossSetRemaps() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app)))
        assertTrue(e.down(OK, 0).consume)
        e.setRemaps(emptyList())
        assertTrue(e.down(OK, 50, repeat = 1).consume)
        assertTrue(e.up(OK, 80).consume)
        assertFalse(e.down(OK, 200).consume) // next press uses new remaps
    }

    @Test fun unconsumedDownKeepsUpPassingAcrossSetRemaps() {
        val e = RemapEngine(emptyList())
        assertFalse(e.down(OK, 0).consume)
        e.setRemaps(listOf(Remap(OK, Press.SINGLE, app)))
        assertFalse(e.down(OK, 50, repeat = 1).consume)
        assertFalse(e.up(OK, 80).consume)
    }

    @Test fun otherKeyWhileHeldStillConsumesHeldUp() {
        val e = RemapEngine(listOf(Remap(HOME, Press.LONG, Action.QuickPanel)))
        e.down(HOME, 0)
        val d = e.down(UP, 100)
        assertFalse(d.consume)
        assertEquals(emptyList<Effect>(), e.onTimer(500)) // Home no longer tracked
        e.up(UP, 150)
        assertEquals(Decision(true, emptyList()), e.up(HOME, 600))
    }

    @Test fun scheduleReplacesPrevious() {
        // A second Schedule without CancelSchedule replaces the first: only the latest time counts.
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app), Remap(OK, Press.DOUBLE, app2), Remap(OK, Press.LONG, Action.QuickPanel)))
        assertEquals(listOf(Effect.Schedule(500)), e.down(OK, 0).effects)
        assertEquals(listOf(Effect.CancelSchedule, Effect.Schedule(100 + DOUBLE_PRESS_MS)), e.up(OK, 100).effects)
        assertEquals(emptyList<Effect>(), e.onTimer(399))
        assertEquals(listOf(fire(app)), e.onTimer(400))
        assertEquals(emptyList<Effect>(), e.onTimer(500)) // the old long time is dead
    }

    @Test fun onTimerWithNothingPendingIsEmpty() {
        val e = RemapEngine(listOf(Remap(OK, Press.SINGLE, app)))
        assertEquals(emptyList<Effect>(), e.onTimer(0))
        e.down(OK, 0)
        assertEquals(emptyList<Effect>(), e.onTimer(10_000)) // single-only: no timer outstanding
    }
}

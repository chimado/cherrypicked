package io.github.chimado.cherrypicked.keys

const val LONG_PRESS_MS = 500L
const val DOUBLE_PRESS_MS = 300L

private const val KEY_HOME = 3
private const val KEY_BACK = 4

sealed interface Effect {
    data class Fire(val action: Action) : Effect
    /** Run [RemapEngine.onTimer] at [atMs]. Replaces any earlier Schedule: at most one timer is outstanding. */
    data class Schedule(val atMs: Long) : Effect
    object CancelSchedule : Effect
}

data class Decision(val consume: Boolean, val effects: List<Effect>)

/**
 * Pure single/double/long press state machine. Tracks one key at a time.
 * Each [Effect.Schedule] replaces the previous one; [onTimer] ignores early or stale calls.
 */
class RemapEngine(private var remaps: List<Remap>) {
    private var key = -1                        // tracked key, -1 = idle
    private var map: Map<Press, Action> = emptyMap() // snapshot at down, so setRemaps can't split a press
    private var single: Action? = null
    private var held = false
    private var longDone = false
    private var second = false                  // this press is the second of a double
    private var waitingDouble = false
    private var timerAt = -1L
    private val swallowUp = mutableSetOf<Int>() // consumed downs no longer tracked

    fun setRemaps(r: List<Remap>) { remaps = r }

    fun onKey(keyCode: Int, down: Boolean, repeatCount: Int, canceled: Boolean, nowMs: Long): Decision {
        val fx = mutableListOf<Effect>()
        if (key != -1 && keyCode != key && down) {
            if (timerAt >= 0) fx += Effect.CancelSchedule
            if (waitingDouble) single?.let { fx += Effect.Fire(it) }
            else if (held) swallowUp += key
            reset()
        }
        if (keyCode != key) {
            if (!down) return Decision(swallowUp.remove(keyCode), fx)
            val m = remaps.forKey(keyCode)
            // repeatCount > 0: the press began before it was mapped, so its up must pass through too
            if (m.isEmpty() || repeatCount > 0) return Decision(false, fx)
            key = keyCode; map = m; held = true
            single = m[Press.SINGLE] ?: when (keyCode) {
                KEY_HOME -> Action.Home
                KEY_BACK -> Action.Back // m non-empty without SINGLE means Back has a double/long
                else -> null
            }
            if (Press.LONG in m) schedule(nowMs + LONG_PRESS_MS, fx)
            return Decision(true, fx)
        }
        if (down) {
            if (waitingDouble && repeatCount == 0) {
                waitingDouble = false; second = true; held = true
                timerAt = -1; fx += Effect.CancelSchedule
            }
            return Decision(true, fx)
        }
        held = false
        if (timerAt >= 0) { timerAt = -1; fx += Effect.CancelSchedule }
        when {
            longDone || canceled -> reset()
            second -> { map[Press.DOUBLE]?.let { fx += Effect.Fire(it) }; reset() }
            Press.DOUBLE in map -> { waitingDouble = true; schedule(nowMs + DOUBLE_PRESS_MS, fx) }
            else -> { single?.let { fx += Effect.Fire(it) }; reset() }
        }
        return Decision(true, fx)
    }

    fun onTimer(nowMs: Long): List<Effect> {
        if (key == -1 || timerAt < 0 || nowMs < timerAt) return emptyList()
        timerAt = -1
        if (waitingDouble) {
            val s = single
            reset()
            return listOfNotNull(s?.let { Effect.Fire(it) })
        }
        longDone = true // held past LONG_PRESS_MS; repeats and up now fire nothing
        return listOfNotNull(map[Press.LONG]?.let { Effect.Fire(it) })
    }

    private fun schedule(at: Long, fx: MutableList<Effect>) { timerAt = at; fx += Effect.Schedule(at) }

    private fun reset() {
        key = -1; map = emptyMap(); single = null
        held = false; longDone = false; second = false; waitingDouble = false; timerAt = -1
    }
}

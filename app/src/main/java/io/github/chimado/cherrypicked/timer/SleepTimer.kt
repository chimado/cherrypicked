package io.github.chimado.cherrypicked.timer

sealed interface RestoreResult {
    data class Arm(val endMs: Long) : RestoreResult
    object FireNow : RestoreResult
    object Drop : RestoreResult
}

/** Pure sleep-timer logic. All times are wall-clock ms (System.currentTimeMillis) so they survive a process restart. */
object SleepTimer {
    val PRESETS_MIN = listOf(15, 30, 60, 90, 120)
    const val LATE_GRACE_MS = 60_000L
    private const val MIN_MS = 60_000L

    /** off -> 15 -> 30 -> 60 -> 90 -> 120 -> off. A running timer counts as its remaining minutes rounded up. */
    fun next(currentEndMs: Long?, nowMs: Long): Long? {
        val left = remainingMs(currentEndMs, nowMs)
        val leftMin = if (left == null) 0L else (left + MIN_MS - 1) / MIN_MS
        val p = if (left == null) PRESETS_MIN.first() else PRESETS_MIN.firstOrNull { it > leftMin }
        return p?.let { nowMs + it * MIN_MS }
    }

    fun restore(savedEndMs: Long?, nowMs: Long): RestoreResult = when {
        savedEndMs == null -> RestoreResult.Drop
        savedEndMs > nowMs -> RestoreResult.Arm(savedEndMs)
        nowMs - savedEndMs <= LATE_GRACE_MS -> RestoreResult.FireNow
        else -> RestoreResult.Drop
    }

    /** Time left, or null when no timer is set. For the quick panel. */
    fun remainingMs(endMs: Long?, nowMs: Long): Long? = endMs?.let { maxOf(0L, it - nowMs) }
}

package io.github.chimado.cherrypicked.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.decodeAction

/**
 * Shell-only diagnostic (shell-injected keys skip accessibility). Registered with the DUMP permission, so only shell/root can send:
 *   am broadcast -a io.github.chimado.cherrypicked.TEST --es action app:org.moonfin.androidtv
 *   am broadcast -a io.github.chimado.cherrypicked.TEST --ei key 3 --es press LONG
 */
class TestHook(
    private val handler: Handler,
    private val run: (Action) -> Unit,
    private val key: (keyCode: Int, down: Boolean, repeatCount: Int, downTime: Long) -> Unit,
) : BroadcastReceiver() {
    data class Step(val atMs: Long, val down: Boolean, val repeatCount: Int = 0)

    override fun onReceive(c: Context, i: Intent) {
        val action = i.getStringExtra("action")
        if (action != null) {
            val a = decodeAction(action)
            Log.i("CP", "test action=$action")
            if (a == null) Log.w("CP", "test bad action=$action") else run(a)
            return
        }
        @Suppress("DEPRECATION") val raw = i.extras?.get("key") // one typed read: a mismatched getter logs a ClassCastException
        val code = if (raw is Int) raw else raw?.toString()?.toIntOrNull() ?: -1
        val press = Press.entries.firstOrNull { it.name == i.getStringExtra("press") }
        Log.i("CP", "test key=$code press=$press")
        if (code < 0 || press == null) { Log.w("CP", "test needs key=<code> press=SINGLE|DOUBLE|LONG"); return }
        cancel() // a new command replaces any script still running
        var downTime = 0L // uptime at each press's first DOWN, reused for its repeats and UP, like a real KeyEvent
        for (s in script(press)) handler.postDelayed({
            if (s.down && s.repeatCount == 0) downTime = SystemClock.uptimeMillis()
            key(code, s.down, s.repeatCount, downTime)
        }, this, s.atMs)
    }

    /** Drops pending synthetic events. */
    fun cancel() = handler.removeCallbacksAndMessages(this)

    companion object {
        const val ACTION = "io.github.chimado.cherrypicked.TEST"

        fun script(p: Press): List<Step> = when (p) {
            Press.SINGLE -> listOf(Step(0, true), Step(80, false))
            Press.DOUBLE -> listOf(Step(0, true), Step(80, false), Step(200, true), Step(280, false))
            Press.LONG -> listOf(Step(0, true)) + (1..15).map { Step(it * 50L, true, it) } + Step(800, false)
        }
    }
}

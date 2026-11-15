package io.github.chimado.cherrypicked.ui

import android.os.Message
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.decodeAction
import io.github.chimado.cherrypicked.keys.encode

/** Messages between :a11y and :ui. Pure (no Bundle) so it tests on the JVM; [toMessage]/[toWire] are the Android boundary. */
object OverlayProtocol {
    // :a11y -> :ui
    const val MSG_NOTICE = 1
    const val MSG_OPEN = 2
    const val MSG_CLOSE = 3
    const val MSG_KEY = 4
    const val MSG_SLEEP = 5
    // :ui -> :a11y
    const val MSG_CLOSED = 101
    const val MSG_RUN = 102
    const val MSG_IDLE = 103

    /** What crosses the process boundary: Message.what/arg1/arg2 plus one String in Message.data. */
    data class Wire(val what: Int, val arg1: Int, val arg2: Int, val str: String?)

    sealed interface Msg {
        data class Notice(val text: String) : Msg
        /** Longs (wall-clock end, uptime) do not fit an Int arg, so they travel in the string. */
        data class Open(val sleepEndMs: Long?, val openedAt: Long) : Msg
        object Close : Msg
        data class Key(val code: Int, val down: Boolean, val canceled: Boolean) : Msg
        data class Sleep(val endMs: Long?) : Msg
        object Closed : Msg
        data class Run(val action: Action) : Msg
        /** [seen] = messages :ui has received; :a11y unbinds only if it has sent no more since, so a notice in flight is never cut. */
        data class Idle(val seen: Int) : Msg
    }

    fun encode(m: Msg): Wire = when (m) {
        is Msg.Notice -> Wire(MSG_NOTICE, 0, 0, m.text)
        is Msg.Open -> Wire(MSG_OPEN, 0, 0, "${m.sleepEndMs ?: ""}|${m.openedAt}")
        Msg.Close -> Wire(MSG_CLOSE, 0, 0, null)
        is Msg.Key -> Wire(MSG_KEY, m.code, (if (m.down) 1 else 0) or (if (m.canceled) 2 else 0), null)
        is Msg.Sleep -> Wire(MSG_SLEEP, 0, 0, m.endMs?.toString())
        Msg.Closed -> Wire(MSG_CLOSED, 0, 0, null)
        is Msg.Run -> Wire(MSG_RUN, 0, 0, m.action.encode())
        is Msg.Idle -> Wire(MSG_IDLE, m.seen, 0, null)
    }

    fun decode(w: Wire): Msg? = when (w.what) {
        MSG_NOTICE -> w.str?.let { Msg.Notice(it) }
        MSG_OPEN -> w.str?.split('|')?.takeIf { it.size == 2 }?.let { (end, at) -> at.toLongOrNull()?.let { Msg.Open(end.toLongOrNull(), it) } }
        MSG_CLOSE -> Msg.Close
        MSG_KEY -> Msg.Key(w.arg1, w.arg2 and 1 != 0, w.arg2 and 2 != 0)
        MSG_SLEEP -> Msg.Sleep(w.str?.toLongOrNull())
        MSG_CLOSED -> Msg.Closed
        MSG_RUN -> w.str?.let(::decodeAction)?.let { Msg.Run(it) }
        MSG_IDLE -> Msg.Idle(w.arg1)
        else -> null
    }
}

private const val KEY_STR = "s"

fun OverlayProtocol.Wire.toMessage(): Message = Message.obtain(null, what, arg1, arg2).also { m -> str?.let { m.data.putString(KEY_STR, it) } }

fun Message.toWire() = OverlayProtocol.Wire(what, arg1, arg2, peekData()?.getString(KEY_STR))

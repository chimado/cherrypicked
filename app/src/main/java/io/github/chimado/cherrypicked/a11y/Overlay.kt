package io.github.chimado.cherrypicked.a11y

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.WindowManager
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.panel.QuickPanel
import io.github.chimado.cherrypicked.ui.OverlayProtocol
import io.github.chimado.cherrypicked.ui.OverlayProtocol.Msg
import io.github.chimado.cherrypicked.ui.OverlayService
import io.github.chimado.cherrypicked.ui.toMessage
import io.github.chimado.cherrypicked.ui.toWire

/** Where the quick panel and notices are drawn: in :a11y ([InProcessOverlay]) or in the :ui process ([RemoteOverlay]). */
interface Overlay {
    fun showNotice(text: String)
    fun togglePanel()
    fun closePanel()
    val panelOpen: Boolean
    val panelOpenedAt: Long
    fun panelKey(code: Int, down: Boolean, canceled: Boolean): Boolean
    fun sleepChanged(endMs: Long?)
    fun release()
}

/** Phase 2's panel and notice, drawn by the accessibility service itself. The fallback when :ui cannot draw. */
class InProcessOverlay(svc: AccessibilityService, handler: Handler, runner: (Action) -> Unit, sleepEnd: () -> Long?) : Overlay {
    private val panel = QuickPanel(svc, runner, sleepEnd)
    private val notice = Notice(svc, handler, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)

    override fun showNotice(text: String) = notice.show(text)
    override fun togglePanel() = panel.toggle()
    override fun closePanel() = panel.close()
    override val panelOpen get() = panel.isOpen()
    override val panelOpenedAt get() = panel.openedAt
    override fun panelKey(code: Int, down: Boolean, canceled: Boolean) = panel.onKey(code, down, canceled)
    override fun sleepChanged(endMs: Long?) {} // the panel reads sleepEnd() when it redraws
    override fun release() { notice.dismiss(); panel.close() }
}

/**
 * Draws in [OverlayService] (:ui), bound only while something shows: binds on first use, queues until connected,
 * unbinds when :ui reports idle, so :ui (and its memory) is gone between uses.
 */
class RemoteOverlay(
    private val ctx: Context,
    private val handler: Handler,
    private val runAction: (Action) -> Unit,
    @Suppress("unused") private val sleepEnd: () -> Long?, // for MSG_OPEN in Task 2
    private val local: Overlay, // ponytail: panel still drawn in :a11y; Task 2 moves it to :ui and drops this
) : Overlay {
    /** bindService refused: the caller uses the in-process overlay for the rest of this connection. */
    var failed = false
        private set
    private var bound = false
    private var remote: Messenger? = null
    private var sent = 0 // messages delivered to the current :ui instance, compared with Idle.seen
    private val queue = ArrayList<Msg>()
    private val replies = Messenger(Handler(handler.looper) { onReply(it.toWire()); true })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote = Messenger(binder)
            sent = 0
            val q = queue.toList()
            queue.clear()
            q.forEach(::send) // in order
        }

        // :ui died while bound: the system restarts it and reconnects; anything sent meanwhile queues.
        override fun onServiceDisconnected(name: ComponentName) { Log.w("CP", "overlay remote lost"); remote = null }

        override fun onBindingDied(name: ComponentName) { Log.w("CP", "overlay remote died"); val q = queue.toList(); unbind(); q.forEach(::send) }

        override fun onNullBinding(name: ComponentName) { unbind(); failed = true }
    }

    override fun showNotice(text: String) = send(Msg.Notice(text))
    override fun togglePanel() = local.togglePanel()
    override fun closePanel() = local.closePanel()
    override val panelOpen get() = local.panelOpen
    override val panelOpenedAt get() = local.panelOpenedAt
    override fun panelKey(code: Int, down: Boolean, canceled: Boolean) = local.panelKey(code, down, canceled)
    override fun sleepChanged(endMs: Long?) = local.sleepChanged(endMs)
    override fun release() { local.release(); unbind() }

    private fun send(m: Msg) {
        val r = remote
        if (r == null) { queue += m; bind(); return }
        try {
            r.send(OverlayProtocol.encode(m).toMessage().apply { replyTo = replies })
            sent++
        } catch (e: RemoteException) { Log.w("CP", "overlay send failed", e); remote = null; queue += m } // reconnect delivers it
    }

    private fun bind() {
        if (bound) return
        bound = try {
            ctx.bindService(Intent(ctx, OverlayService::class.java), conn, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) { Log.w("CP", "overlay bind refused", e); false }
        if (bound) { Log.i("CP", "overlay remote bind"); return }
        try { ctx.unbindService(conn) } catch (_: IllegalArgumentException) {}
        failed = true
        val q = queue.toList()
        queue.clear()
        for (m in q) if (m is Msg.Notice) local.showNotice(m.text) // nothing is lost on the way to the fallback
    }

    private fun unbind() {
        remote = null
        queue.clear()
        if (!bound) return
        bound = false
        try { ctx.unbindService(conn) } catch (_: IllegalArgumentException) {}
        Log.i("CP", "overlay remote unbind")
    }

    private fun onReply(w: OverlayProtocol.Wire) {
        when (val m = OverlayProtocol.decode(w)) {
            is Msg.Run -> runAction(m.action)
            is Msg.Idle -> if (m.seen == sent && queue.isEmpty()) unbind() // else a newer message is in flight
            else -> {} // Msg.Closed: the panel is still in :a11y until Task 2
        }
    }
}

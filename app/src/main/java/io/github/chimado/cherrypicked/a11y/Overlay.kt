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
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.panel.PANEL_KEYS
import io.github.chimado.cherrypicked.panel.QuickPanel
import io.github.chimado.cherrypicked.ui.OverlayProtocol
import io.github.chimado.cherrypicked.ui.OverlayProtocol.Msg
import io.github.chimado.cherrypicked.ui.OverlayService
import io.github.chimado.cherrypicked.ui.toMessage
import io.github.chimado.cherrypicked.ui.toWire

/** Where the quick panel and notices are drawn: in :a11y ([InProcessOverlay]) or in the :ui process ([RemoteOverlay]). */
interface Overlay {
    fun showNotice(text: String)
    fun dismissNotice()
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
    private val panel = QuickPanel(svc, handler, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, runner, sleepEnd)
    private val notice = Notice(svc, handler, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)

    override fun showNotice(text: String) = notice.show(text)
    override fun dismissNotice() = notice.dismiss()
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
 * unbinds when :ui reports idle, so :ui (and its memory) is gone between uses. Draws nothing in :a11y except
 * through [local] after a failure (bind or draw), which the caller then keeps using until [reset].
 */
class RemoteOverlay(
    private val ctx: Context,
    private val handler: Handler,
    private val runAction: (Action) -> Unit,
    private val sleepEnd: () -> Long?,
    private val local: Overlay, // the in-process fallback, used only when :ui fails
) : Overlay {
    /** Why :ui can't be used ("bind failed" / "draw failed"), or null: the caller stays in-process until [reset]. */
    var failure: String? = null
        private set
    private var bound = false
    private var remote: Messenger? = null
    private var sent = 0 // messages delivered to the current :ui instance, compared with Idle.seen
    private var openSent = Int.MAX_VALUE // `sent` when the last Open was delivered; an older Closed is stale
    private val queue = ArrayList<Msg>()
    private val replies = Messenger(Handler(handler.looper) { onReply(it.toWire()); true })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remote = Messenger(binder)
            sent = 0
            if (queue.isEmpty()) { unbind(); return } // nothing to show (dismissed while connecting): don't keep :ui alive
            val q = queue.toList()
            queue.clear()
            q.forEach(::send) // in order
        }

        // :ui died: drop the binding (a system restart would keep an idle :ui alive) and rebind only for queued work.
        override fun onServiceDisconnected(name: ComponentName) { Log.w("CP", "overlay remote lost"); rebind() }

        override fun onBindingDied(name: ComponentName) { Log.w("CP", "overlay remote died"); rebind() }

        override fun onNullBinding(name: ComponentName) { unbind(); fail("bind failed") }
    }

    override fun showNotice(text: String) = send(Msg.Notice(text))
    override fun dismissNotice() {
        if (!bound) return // never bind just to dismiss
        queue.removeAll { it is Msg.Notice }
        if (remote != null) send(Msg.Dismiss) // else onServiceConnected finds the queue empty and unbinds
    }
    /** Set the moment the panel is asked for (before :ui draws it) so routeKey sends the next keys to it; queued in order. */
    override var panelOpen = false
        private set
    override var panelOpenedAt = 0L
        private set

    override fun togglePanel() {
        if (panelOpen) return closePanel()
        panelOpen = true
        panelOpenedAt = SystemClock.uptimeMillis()
        openSent = Int.MAX_VALUE
        send(Msg.Open(sleepEnd(), panelOpenedAt))
    }

    override fun closePanel() {
        if (!panelOpen) return // never bind just to close
        panelOpen = false
        send(Msg.Close)
    }

    override fun panelKey(code: Int, down: Boolean, canceled: Boolean): Boolean {
        if (code !in PANEL_KEYS) return false
        send(Msg.Key(code, down, canceled))
        return true
    }

    override fun sleepChanged(endMs: Long?) { if (panelOpen) send(Msg.Sleep(endMs)) }
    override fun release() = unbind()

    /** A new accessibility connection tries :ui again. */
    fun reset() { failure = null }

    private fun fail(why: String) { Log.w("CP", "overlay remote $why"); failure = why }

    /** :ui died: any panel died with it, so keys go back to the engine; only notices are worth resending. */
    private fun rebind() { val q = queue.filterIsInstance<Msg.Notice>(); unbind(); q.forEach(::send) }

    private fun send(m: Msg) {
        val r = remote
        if (r == null) { queue += m; bind(); return }
        try {
            r.send(OverlayProtocol.encode(m).toMessage().apply { replyTo = replies })
            sent++
            if (m is Msg.Open) openSent = sent
        } catch (e: RemoteException) { Log.w("CP", "overlay send failed", e); remote = null; queue += m } // reconnect delivers it
    }

    private fun bind() {
        if (bound) return
        bound = try {
            ctx.bindService(Intent(ctx, OverlayService::class.java), conn, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) { Log.w("CP", "overlay bind refused", e); false }
        if (bound) { Log.i("CP", "overlay remote bind"); return }
        try { ctx.unbindService(conn) } catch (_: IllegalArgumentException) {}
        fail("bind failed")
        val q = queue.toList()
        queue.clear()
        for (m in q) if (m is Msg.Notice) local.showNotice(m.text) // nothing is lost on the way to the fallback
        if (panelOpen) panelFailed()
    }

    private fun unbind() {
        remote = null
        queue.clear()
        panelOpen = false // :ui exits once unbound
        if (!bound) return
        bound = false
        try { ctx.unbindService(conn) } catch (_: IllegalArgumentException) {}
        Log.i("CP", "overlay remote unbind")
    }

    private fun onReply(w: OverlayProtocol.Wire) {
        when (val m = OverlayProtocol.decode(w)) {
            is Msg.Run -> runAction(m.action)
            is Msg.Idle -> if (m.seen == sent && queue.isEmpty()) unbind() // else a newer message is in flight
            is Msg.Failed -> { fail("draw failed"); local.showNotice(m.text) } // :ui's Idle follows and unbinds it
            is Msg.Closed -> if (m.seen >= openSent) panelOpen = false
            Msg.PanelFailed -> { fail("draw failed"); panelFailed() }
            else -> {}
        }
    }

    /** Reopen the panel in-process: with [failure] set, the caller's QuickPanel action now picks [local]. */
    private fun panelFailed() {
        panelOpen = false
        runAction(Action.QuickPanel)
    }
}

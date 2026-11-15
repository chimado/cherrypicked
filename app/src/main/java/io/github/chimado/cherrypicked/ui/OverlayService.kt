package io.github.chimado.cherrypicked.ui

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.util.Log
import android.view.WindowManager
import io.github.chimado.cherrypicked.a11y.Notice
import io.github.chimado.cherrypicked.panel.QuickPanel
import io.github.chimado.cherrypicked.ui.OverlayProtocol.Msg

/**
 * The :ui process: draws notices and the quick panel for :a11y so their views and fonts never load there.
 * Bound only while something shows; when unbound it exits, taking the process with it.
 */
class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var client: Messenger? = null
    private var seen = 0
    private var lastText = ""
    private var sleepEnd: Long? = null // from Open/Sleep; the panel's sleep row reads it
    private val notice by lazy { Notice(this, handler, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) { failed -> if (failed) reply(Msg.Failed(lastText)); idleIfDone() } }
    private val panel by lazy {
        QuickPanel(this, handler, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, { reply(Msg.Run(it)) }, { sleepEnd }) {
            reply(Msg.Closed(seen)); idleIfDone()
        }
    }
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { onMsg(it); true })

    override fun onCreate() {
        super.onCreate()
        Log.i("CP", "ui start pid=${Process.myPid()}")
    }

    override fun onBind(intent: Intent): IBinder = inbox.binder

    override fun onUnbind(intent: Intent): Boolean {
        stopSelf()
        return false
    }

    override fun onDestroy() {
        Log.i("CP", "ui stop")
        client = null // :a11y already unbound: no Closed/Idle from the teardown
        notice.dismiss()
        panel.close()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
        Process.killProcess(Process.myPid()) // nothing else lives in :ui; don't leave an empty cached process
    }

    private fun onMsg(m: Message) {
        seen++
        m.replyTo?.let { client = it }
        when (val msg = OverlayProtocol.decode(m.toWire())) {
            is Msg.Notice -> { lastText = msg.text; notice.show(msg.text) } // its timeout or failure calls idleIfDone
            Msg.Dismiss -> { notice.dismiss(); idleIfDone() }
            is Msg.Open -> { sleepEnd = msg.sleepEndMs; if (!panel.open()) { reply(Msg.PanelFailed); idleIfDone() } }
            // Each of these closes through panel.close() (Closed, then Idle) or, with no panel open, reports idle itself.
            Msg.Close -> if (panel.isOpen()) panel.close() else idleIfDone()
            is Msg.Key -> if (panel.isOpen()) panel.onKey(msg.code, msg.down, msg.canceled) else idleIfDone()
            is Msg.Sleep -> { sleepEnd = msg.endMs; if (panel.isOpen()) panel.redraw() else idleIfDone() }
            else -> idleIfDone()
        }
    }

    private fun idleIfDone() {
        if (!notice.showing && !panel.isOpen()) reply(Msg.Idle(seen))
    }

    private fun reply(m: Msg) {
        try { client?.send(OverlayProtocol.encode(m).toMessage()) } catch (_: RemoteException) {} // :a11y gone: its unbind stops us
    }
}

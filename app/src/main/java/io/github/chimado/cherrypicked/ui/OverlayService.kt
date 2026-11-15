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
import io.github.chimado.cherrypicked.ui.OverlayProtocol.Msg

/**
 * The :ui process: draws notices (and, from Task 2, the panel) for :a11y so their views and fonts never load there.
 * Bound only while something shows; when unbound it exits, taking the process with it.
 */
class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var client: Messenger? = null
    private var seen = 0
    private var lastText = ""
    private val notice by lazy { Notice(this, handler, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) { failed -> if (failed) reply(Msg.Failed(lastText)); idleIfDone() } }
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
        notice.dismiss()
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
            else -> idleIfDone() // panel messages arrive in Task 2
        }
    }

    private fun idleIfDone() {
        if (!notice.showing) reply(Msg.Idle(seen))
    }

    private fun reply(m: Msg) {
        try { client?.send(OverlayProtocol.encode(m).toMessage()) } catch (_: RemoteException) {} // :a11y gone: its unbind stops us
    }
}

package io.github.chimado.cherrypicked.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Boot auto-open runs from CpAccessibilityService.onServiceConnected (receivers are often blocked at boot on TCL).
        Log.i("CP", "boot received pid=${android.os.Process.myPid()}")
    }
}

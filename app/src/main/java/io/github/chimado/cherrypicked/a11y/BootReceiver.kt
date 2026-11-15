package io.github.chimado.cherrypicked.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) autoOpen(ctx, Trigger.BOOT, null, emptySet())
    }
}

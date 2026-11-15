package io.github.chimado.cherrypicked.a11y

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.home.HomeActivity
import io.github.chimado.cherrypicked.store.ConfigStore
import java.io.File
import java.io.IOException

class CpAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastWindowPkg: String? = null
    private var registered = false
    private val tvInputPackages by lazy { tvInputPackages(this) }

    private val wakeCheck = Runnable {
        val fg = lastWindowPkg
        val pkg = autoOpen(this, Trigger.WAKE, fg, tvInputPackages)
        Log.i("CP", "wake fg=$fg open=$pkg")
    }

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Log.i("CP", "${i.action?.substringAfterLast('.')} pid=${Process.myPid()} t=${SystemClock.elapsedRealtime()}")
            handler.removeCallbacks(wakeCheck) // screen off before settling: drop the pending check
            if (i.action == Intent.ACTION_SCREEN_ON) handler.postDelayed(wakeCheck, AutoOpenPolicy.WAKE_SETTLE_MS)
        }
    }

    override fun onServiceConnected() {
        Log.i("CP", "connected pid=${Process.myPid()}")
        bootCheck()
        if (registered) return
        // Protected system broadcasts only; the system still delivers them to a non-exported receiver.
        registerReceiver(screenRx, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
        registered = true
    }

    /** The boot auto-open: once per BOOT_COUNT, and only soon after boot so a late first bind never takes the screen. */
    private fun bootCheck() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val f = File(filesDir, "last_boot_count")
        val last = try { f.readText().trim().toInt() } catch (_: IOException) { null } catch (_: NumberFormatException) { null }
        if (boot < 0 || boot == last) return
        try { f.writeText(boot.toString()) } catch (e: IOException) { Log.w("CP", "boot count not saved", e); return } // never risk opening twice
        if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) { Log.i("CP", "boot open=null (late bind)"); return }
        Log.i("CP", "boot open=${autoOpen(this, Trigger.BOOT, null, emptySet())}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!HomeKeyPolicy.intercept(event.keyCode)) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }

    /** Tracks the foreground app: only events whose class is an activity of that package (not dialogs, toasts, banners). */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            lastWindowPkg = pkg
        } catch (_: PackageManager.NameNotFoundException) {}
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("CP", "unbind pid=${Process.myPid()}")
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i("CP", "destroy pid=${Process.myPid()}")
        stop()
        super.onDestroy()
    }

    private fun stop() {
        handler.removeCallbacks(wakeCheck)
        if (registered) { unregisterReceiver(screenRx); registered = false }
    }
}

private const val BOOT_WINDOW_MS = 10 * 60_000L

/** Runs the auto-open decision and launches the result; returns the package decided, if any (logs a failed launch). */
internal fun autoOpen(ctx: Context, trigger: Trigger, fg: String?, tvInputs: Set<String>): String? {
    val config = ConfigStore(ctx.filesDir).load()
    val pm = ctx.packageManager
    // Only mainApp matters, so no full app-list query in :a11y.
    val installed = config.mainApp
        ?.takeIf { pm.getLeanbackLaunchIntentForPackage(it) != null || pm.getLaunchIntentForPackage(it) != null }
        ?.let(::setOf) ?: emptySet()
    val pkg = AutoOpenPolicy.decide(trigger, config, installed, fg, tvInputs) ?: return null
    if (!AppCatalog(ctx).launch(pkg)) Log.w("CP", "launch failed $pkg") // launch() catches and returns false
    return pkg
}

/** Packages that show TV inputs: passthrough (HDMI) handlers plus the live-channels app. */
internal fun tvInputPackages(ctx: Context): Set<String> {
    val pm = ctx.packageManager
    val passthrough = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput("x")), 0)
        .map { it.activityInfo.packageName }
    val channels = pm.resolveActivity(Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI), 0)
        ?.activityInfo?.packageName?.takeIf { it != "android" } // "android" = the chooser, not an app
    return (passthrough + listOfNotNull(channels)).toSet().also { Log.i("CP", "tvInputs=${it.joinToString(",")}") }
}

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
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Effect
import io.github.chimado.cherrypicked.keys.KeyNames
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.keys.RemapEngine
import io.github.chimado.cherrypicked.keys.encode
import io.github.chimado.cherrypicked.panel.PanelRoute
import io.github.chimado.cherrypicked.panel.panelRoute
import io.github.chimado.cherrypicked.store.ConfigStore
import io.github.chimado.cherrypicked.store.writeDurably
import io.github.chimado.cherrypicked.timer.RestoreResult
import io.github.chimado.cherrypicked.timer.SleepTimer
import java.io.File
import java.io.IOException

class CpAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastWindowPkg: String? = null
    private var wakeAt = -1L // uptime of the last screen on, until our home's first showing after it
    private var registered = false
    private val tvInputPackages by lazy { tvInputPackages(this) }

    private val store by lazy { ConfigStore(filesDir) } // caches by config.json lastModified
    private var remaps: List<Remap> = emptyList() // identity check only: reload when the store hands back a new list
    private val engine = RemapEngine(emptyList())
    private val runner: ActionRunner by lazy { ActionRunner(this, ::cycleSleepTimer) { overlay().togglePanel() } }
    private val inProcessLazy = lazy { InProcessOverlay(this, handler, { runner.run(it) }) { sleepEndMs } }
    private val inProcess by inProcessLazy
    private val remoteLazy = lazy { RemoteOverlay(this, handler, { runner.run(it) }, { sleepEndMs }, inProcess) }
    private val remote by remoteLazy
    /** The overlay last used; routeKey asks it whether the panel is open. */
    private var current: Overlay? = null
    private var fallbackLogged = false
    private val engineTimer = Runnable { handle(engine.onTimer(SystemClock.uptimeMillis())) }
    private val testHook = TestHook(handler, { runner.run(it) }) { code, down, repeat, downTime ->
        Log.i("CP", "test feed key=$code down=$down repeat=$repeat consume=${routeKey(code, down, repeat, false, downTime)}")
    }

    /** Wall-clock end of the running sleep timer, or null. Read by the in-process panel; sent to :ui in MSG_OPEN/MSG_SLEEP. */
    var sleepEndMs: Long? = null
        private set
    private val sleepFile by lazy { File(filesDir, "sleep_timer") }
    private val sleepFire = Runnable {
        Log.i("CP", "sleep fire")
        saveSleep(null)
        runner.run(Action.ScreenOff)
    }

    private fun saveSleep(end: Long?) {
        sleepEndMs = end
        handler.removeCallbacks(sleepFire)
        try {
            if (end == null) sleepFile.delete() else sleepFile.writeDurably(end.toString())
        } catch (e: IOException) { Log.w("CP", "sleep timer not saved", e) }
        if (end != null) handler.postAtTime(sleepFire, SystemClock.uptimeMillis() + (end - System.currentTimeMillis()).coerceAtLeast(0))
    }

    private fun cycleSleepTimer() {
        val now = System.currentTimeMillis()
        val end = SleepTimer.next(sleepEndMs, now)
        saveSleep(end)
        Log.i("CP", if (end == null) "sleep off" else "sleep set end=$end (${(end - now) / 60_000} min)")
        val msg = if (end == null) "Sleep timer off" else "Sleep in ${(end - now) / 60_000} min"
        val o = overlay()
        o.sleepChanged(end)
        if (!o.panelOpen) o.showNotice(msg) // the open panel's row shows it instead
    }

    /** Picked on each use (the permission can change), but never switched while the panel is open. */
    private fun overlay(): Overlay {
        current?.let { if (it.panelOpen) return it }
        val why = if (!Settings.canDrawOverlays(this)) "no permission" else remote.failure
        val o = if (why == null) remote else {
            if (!fallbackLogged) { Log.i("CP", "overlay in-process ($why)"); fallbackLogged = true }
            inProcess
        }
        current = o
        return o
    }

    private fun restoreSleep() {
        val saved = try { sleepFile.readText().trim().toLong() } catch (_: IOException) { null } catch (_: NumberFormatException) { null }
        val r = SleepTimer.restore(saved, System.currentTimeMillis())
        Log.i("CP", "sleep restore ${r.javaClass.simpleName}")
        when (r) {
            is RestoreResult.Arm -> saveSleep(r.endMs)
            RestoreResult.FireNow -> sleepFire.run()
            RestoreResult.Drop -> saveSleep(null)
        }
    }

    private val wakeCheck = Runnable {
        val fg = lastWindowPkg
        val pkg = autoOpen(this, Trigger.WAKE, fg, tvInputPackages)
        Log.i("CP", "wake fg=$fg open=$pkg")
    }

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Log.i("CP", "${i.action?.substringAfterLast('.')} pid=${Process.myPid()} t=${SystemClock.elapsedRealtime()}")
            if (i.action == Intent.ACTION_SCREEN_OFF) { current?.closePanel(); current?.dismissNotice(); if (remoteLazy.isInitialized()) remote.dismissNotice(); if (inProcessLazy.isInitialized()) inProcess.dismissNotice() } // a notice must not reappear on wake
            if (i.action == Intent.ACTION_SCREEN_OFF && sleepEndMs != null) { Log.i("CP", "sleep cleared (screen off)"); saveSleep(null) } // uptime pauses in sleep: a stale timer would fire after wake
            wakeAt = if (i.action == Intent.ACTION_SCREEN_ON) SystemClock.uptimeMillis() else -1L
            if (i.action == Intent.ACTION_SCREEN_ON) refreshRemaps()
            // No wait on wake: open main as the screen goes off, so it is already up when the TV turns on; check
            // again on wake in case that open didn't stick. A TV that sleeps on an input is left on it both times.
            handler.post(wakeCheck)
        }
    }

    private val configRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refreshRemaps()
    }

    private var learning = false
    private var learnedDown = -1 // the learned key whose ACTION_UP is still to be consumed

    private val learnEnd = Runnable { finishLearn(-1) }

    private val learnRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (learning) return // one session at a time
            learning = true
            handler.removeCallbacks(engineTimer) // drop any press in progress; learn mode owns the keys now
            engine.reset()
            handler.postDelayed(learnEnd, KeyLearn.WINDOW_MS)
        }
    }

    private fun finishLearn(key: Int) {
        learning = false
        handler.removeCallbacks(learnEnd)
        sendBroadcast(Intent(KeyLearn.ACTION_RESULT).setPackage(packageName).putExtra(KeyLearn.EXTRA_KEY, key))
    }

    override fun onServiceConnected() {
        Log.i("CP", "connected pid=${Process.myPid()}")
        fallbackLogged = false
        if (remoteLazy.isInitialized()) remote.reset()
        bootCheck()
        handler.removeCallbacks(engineTimer)
        engine.reset()
        remaps = store.load().remaps
        engine.setRemaps(remaps)
        restoreSleep()
        if (registered) return
        // Protected system broadcasts only; the system still delivers them to a non-exported receiver.
        registerReceiver(screenRx, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
        registerReceiver(learnRx, IntentFilter(KeyLearn.ACTION_START), KeyLearn.PERMISSION, null, RECEIVER_NOT_EXPORTED)
        registerReceiver(configRx, IntentFilter(KeyLearn.ACTION_CONFIG_CHANGED), KeyLearn.PERMISSION, null, RECEIVER_NOT_EXPORTED)
        // Exported because the sender is the shell uid; DUMP keeps normal apps out.
        registerReceiver(testHook, IntentFilter(TestHook.ACTION), "android.permission.DUMP", handler, RECEIVER_EXPORTED)
        registered = true
    }

    /** The boot auto-open: once per BOOT_COUNT, and only soon after boot so a late first bind never takes the screen. */
    private fun bootCheck() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val f = File(filesDir, "last_boot_count")
        val last = try { f.readText().trim().toInt() } catch (_: IOException) { null } catch (_: NumberFormatException) { null }
        if (boot < 0 || boot == last) return
        if (sleepFile.delete()) Log.i("CP", "sleep cleared (boot)") // a reboot or power cut ends the timer; restoreSleep runs after
        try { f.writeText(boot.toString()) } catch (e: IOException) { Log.w("CP", "boot count not saved", e); return } // never risk opening twice
        if (SystemClock.elapsedRealtime() > BOOT_WINDOW_MS) { Log.i("CP", "boot open=null (late bind)"); return }
        Log.i("CP", "boot open=${autoOpen(this, Trigger.BOOT, null, emptySet())}")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (learning && event.action == KeyEvent.ACTION_DOWN) {
            Log.i("CP", "learn key=${event.keyCode} ${KeyNames.label(event.keyCode)}")
            learnedDown = event.keyCode
            finishLearn(event.keyCode)
            return true
        }
        if (learnedDown == event.keyCode) { // key repeats, then the UP, of the learned press
            if (event.action == KeyEvent.ACTION_UP) learnedDown = -1
            return true
        }
        if (learning) { Log.i("CP", "learn key=${event.keyCode} ${KeyNames.label(event.keyCode)}"); return true }
        return routeKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, event.isCanceled, event.downTime)
    }

    /** Shared by real keys and [TestHook]: while open the panel owns every key (see [panelRoute]), otherwise the remap engine. */
    private fun routeKey(code: Int, down: Boolean, repeat: Int, canceled: Boolean, downTime: Long): Boolean {
        val o = current // no permission check per key: a panel can only be open on the overlay that opened it
        return when (panelRoute(o?.panelOpen == true, downTime, o?.panelOpenedAt ?: 0L, code, down)) {
            PanelRoute.ENGINE -> feedKey(code, down, repeat, canceled)
            PanelRoute.CLOSE -> { o?.closePanel(); true }
            PanelRoute.PANEL -> o?.panelKey(code, down, canceled) ?: false
        }
    }

    /** The one remap path. Uptime clock throughout, matching handler.postAtTime. */
    private fun feedKey(code: Int, down: Boolean, repeat: Int, canceled: Boolean): Boolean {
        val d = engine.onKey(code, down, repeat, canceled, SystemClock.uptimeMillis())
        handle(d.effects)
        return d.consume
    }

    private fun handle(fx: List<Effect>) {
        for (e in fx) when (e) {
            is Effect.Fire -> { Log.i("CP", "fire ${e.action.encode()}"); runner.run(e.action) }
            is Effect.Schedule -> handler.postAtTime(engineTimer, e.atMs)
            Effect.CancelSchedule -> handler.removeCallbacks(engineTimer)
        }
    }

    private fun refreshRemaps() {
        val r = store.load().remaps // same list instance while config.json is unchanged
        if (r !== remaps) { remaps = r; engine.setRemaps(r); Log.i("CP", "remaps reloaded n=${r.size}") }
    }

    /** Tracks the foreground app: only events whose class is an activity of that package (not dialogs, toasts, banners). */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            lastWindowPkg = pkg
            if (wakeAt >= 0 && AutoOpenPolicy.recheckOnHome(pkg, packageName, SystemClock.uptimeMillis() - wakeAt)) {
                wakeAt = -1L
                handler.post(wakeCheck)
            }
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
        handler.removeCallbacks(learnEnd)
        handler.removeCallbacks(engineTimer)
        handler.removeCallbacks(sleepFire) // the saved file stays, so a reconnect re-arms it
        if (remoteLazy.isInitialized()) remote.release() // unbinds :ui
        if (inProcessLazy.isInitialized()) inProcess.release()
        current?.release()
        current = null
        testHook.cancel()
        engine.reset()
        learning = false
        learnedDown = -1
        if (registered) { unregisterReceiver(screenRx); unregisterReceiver(learnRx); unregisterReceiver(configRx); unregisterReceiver(testHook); registered = false }
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

package io.github.chimado.cherrypicked.setup

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.chimado.cherrypicked.ACCENTS
import io.github.chimado.cherrypicked.BASES
import io.github.chimado.cherrypicked.PATTERNS
import io.github.chimado.cherrypicked.Theme
import io.github.chimado.cherrypicked.cycle
import io.github.chimado.cherrypicked.palette
import io.github.chimado.cherrypicked.a11y.KeyLearn
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.health.HomeHealth
import io.github.chimado.cherrypicked.home.AppEntry
import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.home.PatternDrawable
import io.github.chimado.cherrypicked.home.TvInput
import io.github.chimado.cherrypicked.home.named
import io.github.chimado.cherrypicked.rank.UsageLedger
import io.github.chimado.cherrypicked.rank.foregroundLast14d
import io.github.chimado.cherrypicked.inputs.InputCatalog
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.KeyNames
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.keys.encode
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.ConfigStore
import io.github.chimado.cherrypicked.store.CONFIG_FILE_NAME
import io.github.chimado.cherrypicked.store.ImportCheck
import io.github.chimado.cherrypicked.store.MAX_IMPORT_BYTES
import io.github.chimado.cherrypicked.store.MAX_INPUT_NAME
import io.github.chimado.cherrypicked.store.checkImport
import io.github.chimado.cherrypicked.store.toJson
import io.github.chimado.cherrypicked.store.writeDurably
import java.io.File
import java.io.IOException
import kotlin.math.max

private const val PKG = "io.github.chimado.cherrypicked"
private const val TEST_PROMPT = "Press any button on your remote…"

private const val WAY_HOME = "Keep a way back to Cherrypicked"
private fun remapId(key: Int, press: Press) = "remap:$key:${press.name}"
private fun pressLabel(p: Press) = p.name.lowercase().replaceFirstChar(Char::uppercase)

/** [id]: a stable focus id for an actionable row, `#key` for a section header (the target of a "section" extra). */
/** [onSide]: left/right on the row, with -1 or 1 (Appearance choices). */
private class Row(val text: String, val id: String? = null, val onSide: ((Int) -> Unit)? = null, val onClick: (() -> Unit)? = null) { // onClick last: rows pass it as a trailing lambda
    val section get() = id?.startsWith("#") == true
}

/** A sub-screen; leaving it focuses [opener], or the first row of [section] when that row is gone. */
private sealed class Sub(val opener: String, val section: String) {
    object PickMain : Sub("main", "autoopen")
    object Hidden : Sub("hidden", "hidden")
    object Appearance : Sub("appearance", "appearance")
    class ImportConfirm(val check: ImportCheck.Ok) : Sub("import", "backup")
    class RemapActions(val r: Remap) : Sub(remapId(r.keyCode, r.press), "buttons")
    object Learn : Sub("add-remap", "buttons")
    class PickPress(val key: Int) : Sub("add-remap", "buttons")
    /** Opener is the remap's row: focused after a pick; gone (falls back to the section) when an add is abandoned. */
    class PickAction(val key: Int, val press: Press) : Sub(remapId(key, press), "buttons")
}

/** Plain list setup screen in its own `:setup` process; every change is saved at once. */
class SetupActivity : Activity() {
    private lateinit var store: ConfigStore
    private lateinit var config: Config
    private lateinit var apps: List<AppEntry>
    private lateinit var tvInputs: List<TvInput> // under the TV's own labels
    private val inputs get() = tvInputs.named(config)
    private var pad: PinPad? = null
    private var editor: FavEditor? = null
    private var gate = false // the pad on screen is the lock, not a new PIN being set
    private var regate = false // left while locked: ask for the PIN again on return
    private val konami = Konami()
    private var simpleNote: String? = null
    private var dialog: AlertDialog? = null
    private var backDown = false // a Back we handled on its way down: swallow its repeats and its up
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var regular: Typeface
    private var vw = 0f
    private var sub: Sub? = null
    private var note: String? = null
    private var usageNote = false
    private var overlayNote = false
    private var started = false
    private var learnText: String? = null
    private var buttonsNote: String? = null
    private var backupNote: String? = null
    private var renders = 0
    private var armedFor = -1 // reset is armed only in the render right after its first OK

    private val learnRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val k = i.getIntExtra(KeyLearn.EXTRA_KEY, -1)
            if (sub === Sub.Learn) {
                if (k < 0) { sub = null; buttonsNote = "No button seen"; render("add-remap", "buttons") }
                else { sub = Sub.PickPress(k); render(null, null) }
                return
            }
            if (learnText != TEST_PROMPT) return // a learn the user left with Back; not a button test
            learnText = if (k < 0) "No button seen" else "You pressed: ${KeyNames.label(k)} ($k)"
            render(focusedId(), null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(filesDir)
        config = store.load()
        Theme.load(config)
        apps = AppCatalog(this).apps().sortedBy { it.label.lowercase() }
        tvInputs = InputCatalog(this).inputs()
        val b = windowManager.currentWindowMetrics.bounds
        vw = max(b.width(), b.height()) / 100f
        regular = Typeface.createFromAsset(assets, "Inter-Regular.ttf")
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((8 * vw).toInt(), (5 * vw).toInt(), (8 * vw).toInt(), (5 * vw).toInt()) }
        scroll = ScrollView(this).apply {
            background = baseBg()
            addView(list)
        }
        registerReceiver(learnRx, IntentFilter(KeyLearn.ACTION_RESULT), KeyLearn.PERMISSION, null, RECEIVER_NOT_EXPORTED)
        if (config.pin != null) showGate() else enter()
    }

    /** Past the lock (or there is none): the list, or the editor when Home's "Pick favorites" tile sent us. */
    private fun enter() {
        val section = intent.getStringExtra("section")
        intent.removeExtra("section") // only the first time in
        if (section == "edit-favorites") showEditor() else showList(null, section)
    }

    private fun showList(focusId: String?, section: String?) {
        pad = null; editor = null; gate = false
        setContentView(scroll)
        render(focusId, section)
    }

    private fun showPad(title: String, onPin: (String) -> Unit) {
        dialog?.dismiss() // an open rename must not sit on top of the lock
        editor = null
        pad = PinPad(this, vw, regular, title, onPin).also { setContentView(it) }
    }

    /** Simple mode's lock. Nothing in Setup is reachable until the PIN, or the code in [Konami], is entered. */
    private fun showGate() {
        gate = true
        showPad("Enter the PIN") { if (it == store.load().pin) enter() else pad?.ask("Enter the PIN", "Wrong PIN") }
    }

    private fun askNewPin() {
        var first: String? = null
        showPad("New PIN") { pin ->
            when (first) {
                null -> { first = pin; pad?.ask("Repeat the PIN") }
                pin -> {
                    // Changing the PIN keeps the row that was frozen when simple mode was turned on.
                    val changed = config.pin != null
                    edit { if (it.pin != null) it.lock(pin, it.frozenRow) else it.lock(pin, ranking(it)) }
                    simpleNote = if (note != null) null else if (changed) "PIN changed" else "Simple mode is on"
                    showList("simple-pin", "simple")
                }
                else -> { first = null; pad?.ask("New PIN", "The PINs didn't match") }
            }
        }
    }

    /** Every app in today's ranking, to freeze: the whole list, so the lower row stays full when favorites change. */
    private fun ranking(c: Config): List<String> {
        val scores = UsageLedger(File(filesDir, "usage.json")).scores(System.currentTimeMillis(), foregroundLast14d(this))
        return HomeModel.rankAll(apps, c, scores).map { it.pkg }
    }

    /** Asks for an input's nickname; [done] runs after a save. */
    private fun rename(i: TvInput, done: () -> Unit) {
        val field = EditText(this).apply {
            setText(config.inputNames[i.id].orEmpty())
            hint = i.label
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(MAX_INPUT_NAME))
            setSingleLine()
            selectAll()
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Name for ${i.label}")
            .setMessage("Leave it empty to use the TV's name.")
            .setView(field)
            .setPositiveButton("Save") { _, _ -> edit { it.nameInput(i.id, field.text.toString()) }; done() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditor() {
        pad = null
        val b = windowManager.currentWindowMetrics.bounds
        editor = FavEditor(this, max(b.width(), b.height()).toFloat(), kotlin.math.min(b.width(), b.height()).toFloat(), regular, apps, tvInputs,
            { config }, ::edit, ::rename) { showList("edit-favs", "favorites") }.also { setContentView(it) }
    }

    override fun onStart() {
        super.onStart()
        // The saved config, not this instance's copy: another Setup opened on top may have turned the lock on.
        if (regate && store.load().pin != null) showGate()
        regate = false
    }

    override fun onStop() {
        super.onStop()
        // Any way out (Home, another app, a system settings screen) locks Setup again.
        if (!isChangingConfigurations) regate = true
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        val down = e.action == KeyEvent.ACTION_DOWN
        // A held Back must not act twice: once in the editor or on the lock, then again on the screen underneath.
        if (k == KeyEvent.KEYCODE_BACK && backDown) { if (!down) backDown = false; return true }
        editor?.let { ed ->
            val nav = when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Nav.LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> Nav.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> Nav.UP; KeyEvent.KEYCODE_DPAD_DOWN -> Nav.DOWN
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Nav.OK
                KeyEvent.KEYCODE_BACK -> Nav.BACK
                else -> return super.dispatchKeyEvent(e)
            }
            if (nav == Nav.BACK) { if (down && e.repeatCount == 0) { backDown = true; ed.key(nav) } }
            else if (down && (nav != Nav.OK || e.repeatCount == 0)) ed.key(nav)
            return true
        }
        val p = pad ?: return super.dispatchKeyEvent(e)
        if (!down) return super.dispatchKeyEvent(e)
        if (gate && e.repeatCount == 0) { // a held key is one press of the code
            val armed = konami.armed
            if (konami.feed(k)) { enter(); return true }
            if (k == KeyEvent.KEYCODE_BACK && armed) { backDown = true; return true } // the code's B, not "leave"
        }
        if (k == KeyEvent.KEYCODE_DEL) { p.delete(); return true }
        val digit = when (k) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> k - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> k - KeyEvent.KEYCODE_NUMPAD_0
            else -> return super.dispatchKeyEvent(e)
        }
        if (e.repeatCount == 0) p.type('0' + digit)
        return true
    }

    override fun onResume() {
        super.onResume()
        // Returning from a system settings screen: refresh the statuses.
        if (!started) started = true // first resume keeps the "section" focus
        else if (pad == null && editor == null) render(focusedId(), null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gate) return finish()
        if (pad != null) return showList("simple-pin", "simple") // a new PIN abandoned
        val s = sub ?: return finish()
        sub = null
        render(s.opener, s.section)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(learnRx)
        Process.killProcess(Process.myPid()) // alone in :setup; also covers leaving with Home, not only finish()
    }

    private fun edit(edit: (Config) -> Config) {
        config = edit(store.load()) // fresh: Home may have saved since
        note = try { store.save(config); null } catch (_: IOException) { "Couldn't save" }
        if (note == null) sendBroadcast(Intent(KeyLearn.ACTION_CONFIG_CHANGED).setPackage(PKG))
        if (Theme.load(config)) scroll.background = baseBg() // an appearance change or an import
    }

    /** A favorite entry's label: an app package or `input:<id>`. */
    private fun label(entry: String) = if (entry.startsWith("input:"))
        "Input: " + (inputs.firstOrNull { "input:${it.id}" == entry }?.label ?: entry.removePrefix("input:"))
        else apps.firstOrNull { it.pkg == entry }?.label ?: entry

    private fun actionLabel(a: Action) = when (a) {
        is Action.OpenApp -> "Open " + label(a.pkg) + if (apps.none { it.pkg == a.pkg }) " (app missing)" else ""
        is Action.OpenInput -> label("input:${a.inputId}")
        Action.Home -> "Home"
        Action.Back -> "Back"
        Action.QuickPanel -> "Quick panel"
        Action.SleepTimer -> "Sleep timer"
        Action.ScreenOff -> "Screen off"
        Action.Nothing -> "Do nothing"
    }

    private fun remapText(r: Remap) = "${KeyNames.label(r.keyCode)} · ${pressLabel(r.press)} → ${actionLabel(r.action)}"

    private fun rows(): List<Row> = when (val s = sub) {
        Sub.PickMain -> listOf(Row("Main app")) +
            (apps.map { it.pkg } + inputs.map { "input:${it.id}" }).map { m -> Row(label(m), "pick:$m") { edit { it.setMainApp(m) }; sub = null } }
        is Sub.ImportConfirm -> listOf(Row("Import this setup?")) + s.check.summary.map { Row(it) } + listOf(
            Row("Apply", "apply") { edit { s.check.config }; backupNote = note ?: "Setup imported"; sub = null },
            Row("Cancel", "cancel") { sub = null },
        )
        Sub.Hidden -> listOf(Row("Hidden apps")) +
            apps.map { a -> Row((if (a.pkg in config.hidden) "✓  " else "     ") + a.label, "hide:${a.pkg}") { edit { it.toggleHidden(a.pkg) } } }
        is Sub.RemapActions -> listOf(
            Row(remapText(s.r)),
            Row("Change action", "change") { sub = Sub.PickAction(s.r.keyCode, s.r.press) },
            Row(if (config.removeRemap(s.r.keyCode, s.r.press).remaps.size < config.remaps.size - 1) "Remove (also removes Double/Long)" else "Remove", "remove") {
                edit { it.removeRemap(s.r.keyCode, s.r.press) }
                if (config.remaps.any { it.keyCode == s.r.keyCode && it.press == s.r.press }) buttonsNote = WAY_HOME // refused
                sub = null
            },
        )
        Sub.Learn -> listOf(Row("Press the button to change… (waits ${KeyLearn.WINDOW_MS / 1000} s)"))
        is Sub.PickPress -> listOf(Row(KeyNames.label(s.key) + ": which press?")) + Press.entries.map { p ->
            if (canRemap(s.key, p, config.remaps)) Row(pressLabel(p), "press:${p.name}") { sub = Sub.PickAction(s.key, p) }
            else Row(pressLabel(p) + if (isNavKey(s.key)) ": needed to move around" else ": Set a single press first")
        }
        is Sub.PickAction -> {
            fun pick(a: Action, text: String) = Row(text, "act:${a.encode()}") {
                val r = Remap(s.key, s.press, a)
                edit { it.putRemap(r) }
                if (r !in config.remaps) buttonsNote = WAY_HOME // refused
                sub = null
            }
            listOf(Row("${KeyNames.label(s.key)} · ${pressLabel(s.press)} →")) +
                apps.filter { it.pkg !in config.hidden }.map { pick(Action.OpenApp(it.pkg), it.label) } +
                inputs.map { pick(Action.OpenInput(it.id), "Input: ${it.label}") } +
                listOf(Action.Home, Action.Back, Action.QuickPanel, Action.SleepTimer, Action.ScreenOff, Action.Nothing).map { pick(it, actionLabel(it)) }
        }
        Sub.Appearance -> {
            val p = palette(config)
            fun look(id: String, label: String, change: (Config, Int) -> Config): Row {
                val step = { d: Int -> edit { change(it, d) } }
                return Row("‹ $label ›", id, onSide = step) { step(1) }
            }
            listOf(
                Row("Appearance"),
                look("pattern", "Pattern: ${p.pattern}") { c, d -> c.copy(pattern = cycle(PATTERNS, c.pattern, d)) },
                look("accent", "Accent: ${p.accentName}") { c, d -> c.copy(accent = cycle(ACCENTS.keys.toList(), c.accent, d)) },
                look("base", "Base: ${p.baseName}") { c, d -> c.copy(base = cycle(BASES.keys.toList(), c.base, d)) },
            )
        }
        null -> mainRows()
    }

    private fun baseBg() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Theme.BG_TOP, Theme.BG_BOTTOM))

    private fun mainRows(): List<Row> = buildList {
        note?.let { add(Row(it)) }
        add(Row("Simple mode", "#simple"))
        simpleNote?.let { add(Row(it)) }
        if (config.pin == null) {
            add(Row("Locks this setup behind a PIN. The home screen stops changing and stops asking questions."))
            add(Row("Turn on…", "simple-pin") { askNewPin() })
        } else {
            add(Row("On. Setup asks for the PIN."))
            add(Row("Change PIN", "simple-pin") { askNewPin() })
            add(Row("Turn off", "simple-off") { edit { it.unlock() }; if (note == null) simpleNote = "Simple mode is off" })
        }
        add(Row("Forgot the PIN? On the PIN screen press Up Up Down Down Left Right Left Right Back OK."))

        add(Row("Appearance", "#appearance"))
        palette(config).let { p -> add(Row("Choose look… (${p.pattern}, ${p.accentName}, ${p.baseName})", "appearance") { sub = Sub.Appearance }) }

        add(Row("Favorites", "#favorites"))
        add(Row(config.favorites.joinToString(", ", transform = ::label).ifEmpty { "None yet" }))
        add(Row("Edit favorites…", "edit-favs") { showEditor() })

        if (tvInputs.isNotEmpty()) {
            add(Row("Input names", "#inputs"))
            tvInputs.forEach { i ->
                val name = config.inputNames[i.id]
                add(Row(if (name == null) i.label else "${i.label}: $name", "name:${i.id}") { rename(i) { render("name:${i.id}", null) } })
            }
        }

        add(Row("Auto-open", "#autoopen"))
        add(if (config.mainApp == null) Row("Open main app on start: set a main app first")
            else Row("Open main app on start: " + if (config.autoOpen) "On" else "Off", "autoopen") { edit { it.toggleAutoOpen() } })
        add(Row("Main app: " + (config.mainApp?.let(::label) ?: "none"), "main") { sub = Sub.PickMain })

        add(Row("Hidden apps", "#hidden"))
        add(Row("Choose hidden apps… (" + config.hidden.count { h -> apps.any { it.pkg == h } } + " hidden)", "hidden") { sub = Sub.Hidden })

        val bound = HomeHealth.isEnabled(this@SetupActivity)
        add(Row("Home button", "#home"))
        if (bound) add(Row("Connected")) else {
            add(Row("Not connected"))
            add(Row("1. Settings > Accessibility > Cherrypicked > On"))
            add(Row("2. If that is greyed out, run from a computer: adb shell settings put secure enabled_accessibility_services <current list>:$PKG/.a11y.CpAccessibilityService"))
            add(Row("3. On TCL TVs also allow auto-start: adb shell appops set $PKG APP_AUTO_START allow"))
        }
        add(Row("Open accessibility settings", "a11y") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) })

        add(Row("Display over other apps", "#overlay"))
        add(Row(if (Settings.canDrawOverlays(this@SetupActivity)) "Allowed (saves memory)" else "Not allowed"))
        add(Row("Lets the quick panel and notices run in their own short-lived process, so Cherrypicked uses less memory."))
        add(Row("Open settings", "overlay-perm") { overlayNote = !open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$PKG")) })
        if (overlayNote) add(Row("No settings screen for this here. From a computer run: adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow"))

        add(Row("Buttons", "#buttons"))
        val remaps = config.remaps.sortedWith(compareBy({ it.keyCode }, { it.press }))
        if (!bound) {
            add(Row("Buttons need the Home button connected"))
            remaps.forEach { add(Row(remapText(it))) }
        } else {
            buttonsNote?.let { add(Row(it)) }
            remaps.forEach { r -> add(Row(remapText(r), remapId(r.keyCode, r.press)) { sub = Sub.RemapActions(r) }) }
            add(Row("Add button…", "add-remap") { sub = Sub.Learn; sendBroadcast(Intent(KeyLearn.ACTION_START).setPackage(PKG)) })
            val armed = armedFor == renders
            add(Row(if (armed) "Reset buttons: press OK again to confirm" else "Reset buttons", "reset-remaps") {
                if (armed) { edit { it.resetRemaps() }; armedFor = -1 } else armedFor = renders + 1
            })
            add(Row(learnText ?: "Button test", "button-test") {
                learnText = TEST_PROMPT
                sendBroadcast(Intent(KeyLearn.ACTION_START).setPackage(PKG))
            })
        }

        add(Row("Usage access", "#usage"))
        add(Row(if (usageAllowed()) "Allowed" else "Not allowed"))
        add(Row("Lets Cherrypicked rank apps by how much you watch them, not only by launches from Cherrypicked."))
        add(Row("Open usage access settings", "usage") { usageNote = !open(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
        if (usageNote) add(Row("No settings screen for this here. From a computer run: adb shell appops set $PKG GET_USAGE_STATS allow"))

        add(Row("Backup", "#backup"))
        backupNote?.let { add(Row(it)) }
        add(Row("Export setup", "export") { backupNote = exportSetup() })
        add(Row("Import setup…", "import") { importSetup() })
    }

    private fun backupFile() = getExternalFilesDir(null)?.let { File(it, CONFIG_FILE_NAME) }

    private fun exportSetup(): String {
        val f = backupFile() ?: return "Storage not available"
        return try { f.writeDurably(store.load().toJson()); "Saved. Copy it off with: adb pull /sdcard/Android/data/$PKG/files/$CONFIG_FILE_NAME" }
        catch (_: IOException) { "Couldn't save" }
    }

    private fun importSetup() {
        val f = backupFile() ?: run { backupNote = "Storage not available"; return }
        if (!f.exists()) { backupNote = "No setup file. Put one there with: adb push $CONFIG_FILE_NAME /sdcard/Android/data/$PKG/files/"; return }
        if (f.length() > MAX_IMPORT_BYTES) { backupNote = "File too large"; return }
        val text = try { f.readText() } catch (_: IOException) { backupNote = "Couldn't read the file"; return }
        when (val c = checkImport(text, apps.map { it.pkg }.toSet(), ::label)) {
            is ImportCheck.Refused -> backupNote = c.reason
            is ImportCheck.Ok -> sub = Sub.ImportConfirm(c)
        }
    }

    @Suppress("DEPRECATION")
    private fun usageAllowed() = getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED

    private fun open(action: String, data: Uri? = null): Boolean = try {
        startActivity(Intent(action, data)); true
    } catch (_: RuntimeException) { false } // ActivityNotFound, or SecurityException from a protected settings screen

    private fun focusedId() = currentFocus?.tag as? String

    /** Runs a row's action, then re-renders with focus on the same row, the new sub-screen's first row, or the opener. */
    private fun click(r: Row) {
        val before = sub
        val id = focusedId()
        buttonsNote = null
        backupNote = null
        simpleNote = null
        r.onClick?.invoke()
        if (pad != null || editor != null) return // the row opened another screen
        val now = sub
        when {
            now === before -> render(id, null)
            now == null -> render(before!!.opener, before.section)
            else -> render(if (now is Sub.PickMain) config.mainApp?.let { "pick:$it" } else null, null)
        }
    }

    private fun render(focusId: String?, section: String?) {
        renders++
        list.removeAllViews()
        val rows = rows()
        rows.forEach { list.addView(row(it)) }
        if (sub === Sub.Appearance) list.addView(Preview(this), LinearLayout.LayoutParams((48 * vw).toInt(), (27 * vw).toInt()).apply {
            topMargin = (1.5 * vw).toInt(); leftMargin = (1.5 * vw).toInt()
        }) // after the rows, so focus indices still match
        val i = focusTarget(rows.map { if (it.onClick != null || it.section) it.id else null }, focusId, section)
        val target = list.getChildAt(i) ?: return
        target.post { target.requestFocus(); target.requestRectangleOnScreen(android.graphics.Rect(0, 0, target.width, target.height)) }
    }

    private fun row(r: Row) = TextView(this).apply {
        text = r.text
        typeface = regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, vw * if (r.section) 1.4f else 1.7f)
        setTextColor(if (r.section) Theme.ACCENT else if (r.onClick == null) Theme.DIM else Theme.TEXT)
        setPadding((1.5 * vw).toInt(), (if (r.section) 2.2 * vw else 0.9 * vw).toInt(), (1.5 * vw).toInt(), (0.9 * vw).toInt())
        if (r.onClick != null) {
            tag = r.id
            r.onSide?.let { side ->
                setOnKeyListener { _, code, e ->
                    val d = when (code) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
                    if (d != 0 && e.action == KeyEvent.ACTION_DOWN) { note = null; side(d); render(r.id, null) }
                    d != 0
                }
            }
            isFocusable = true
            setOnClickListener { click(r) }
            setOnFocusChangeListener { v, f ->
                v.background = if (f) GradientDrawable().apply { setColor(Theme.FOCUS_BG); setStroke(0, 0) } else ColorDrawable(0)
                (v as TextView).setTextColor(if (f) Theme.ACCENT else Theme.TEXT)
                // Focus moved off an armed reset: disarm. Posted, so a removal by render (parent gone) doesn't count.
                if (!f && r.id == "reset-remaps") v.post {
                    if (v.parent != null && !v.hasFocus() && armedFor == renders) { armedFor = -1; v.text = "Reset buttons" }
                }
            }
        }
    }
}

/** A small home screen in the current look: pattern, four tiles (the first focused), the tier-2 row and the clock. */
private class Preview(ctx: Context) : android.view.View(ctx) {
    private val bg = PatternDrawable(Theme.palette)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val clockFont = Typeface.createFromAsset(ctx.assets, "Inter-ExtraLight.ttf")
    private val tiles = intArrayOf(0xFF3A3F4A.toInt(), 0xFF2F3A36.toInt(), 0xFF40343A.toInt(), 0xFF333847.toInt())

    override fun onDraw(c: android.graphics.Canvas) {
        bg.setBounds(0, 0, width, height)
        bg.draw(c)
        c.save()
        c.scale(width / 960f, height / 540f) // the mockup's design space
        val tw = 211f; val th = 119f; val x0 = (960 - (tw * 4 + 26 * 3)) / 2; val y0 = 540 * .40f
        for (i in 0 until 4) {
            val x = x0 + i * (tw + 26)
            c.save()
            if (i == 0) {
                c.scale(1.07f, 1.07f, x + tw / 2, y0 + th / 2)
                paint.style = android.graphics.Paint.Style.FILL; paint.color = (0x33 shl 24) or (Theme.ACCENT and 0xFFFFFF)
                c.drawRoundRect(x - 6, y0 - 6, x + tw + 6, y0 + th + 6, 24f, 24f, paint)
            }
            paint.style = android.graphics.Paint.Style.FILL; paint.color = tiles[i]
            c.drawRoundRect(x, y0, x + tw, y0 + th, 19f, 19f, paint)
            if (i == 0) {
                paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 3.4f; paint.color = Theme.ACCENT
                c.drawRoundRect(x, y0, x + tw, y0 + th, 19f, 19f, paint)
            }
            c.restore()
        }
        paint.style = android.graphics.Paint.Style.FILL; paint.color = 0x593A3D45 // tier 2 at 35%
        val sx0 = (960 - (150 * 5 + 18 * 4)) / 2f
        for (i in 0 until 5) c.drawRoundRect(sx0 + i * 168, 540 * .72f, sx0 + i * 168 + 150, 540 * .72f + 84, 13f, 13f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT; paint.typeface = clockFont; paint.textSize = 33f; paint.color = Theme.ACCENT
        c.drawText(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), 912f, 540 * .06f + 32, paint)
        c.restore()
    }
}


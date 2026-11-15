package io.github.chimado.cherrypicked.setup

import android.view.KeyEvent
import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.store.Config
import io.github.chimado.cherrypicked.store.DEFAULT_REMAPS

/** Pure config edits for Setup; each returns the same config when the edit is refused. */
fun Config.moveFavorite(pkg: String, delta: Int): Config {
    val i = favorites.indexOf(pkg)
    val j = i + delta
    if (i < 0 || j !in favorites.indices) return this
    return copy(favorites = favorites.toMutableList().also { java.util.Collections.swap(it, i, j) })
}

fun Config.removeFavorite(pkg: String) = copy(favorites = favorites - pkg)

fun Config.addFavorite(pkg: String): Config =
    if (pkg in favorites || favorites.size >= HomeModel.MAX_FAVORITES) this else copy(favorites = favorites + pkg)

fun Config.toggleHidden(pkg: String) = copy(hidden = if (pkg in hidden) hidden - pkg else hidden + pkg)

fun Config.setMainApp(pkg: String) = copy(mainApp = pkg, autoOpen = true)

fun Config.toggleAutoOpen() = if (mainApp == null) this else copy(autoOpen = !autoOpen)

/**
 * Index of the row to focus: [prevId] if it is still shown, else the first focusable row from [section]'s header on,
 * else the first focusable row; -1 if none. [ids] holds a focusable row's id, `#key` for a section header, null otherwise.
 */
fun focusTarget(ids: List<String?>, prevId: String?, section: String?): Int {
    fun ok(i: Int) = ids[i]?.startsWith("#") == false
    if (prevId != null) ids.indexOf(prevId).takeIf { it >= 0 && ok(it) }?.let { return it }
    val from = section?.let { ids.indexOf("#$it") }?.coerceAtLeast(0) ?: 0
    return (from until ids.size).firstOrNull(::ok) ?: ids.indices.firstOrNull(::ok) ?: -1
}

/** Replaces the remap with the same key and press; refuses an empty app/input id, which would not survive a save. */
fun Config.putRemap(r: Remap): Config {
    val a = r.action
    if ((a is Action.OpenApp && a.pkg.isEmpty()) || (a is Action.OpenInput && a.inputId.isEmpty())) return this
    if (!canRemap(r.keyCode, r.press, remaps)) return this
    return copy(remaps = remaps.filterNot { it.keyCode == r.keyCode && it.press == r.press } + r)
}

/** Removing a key's single also drops its double/long when those can't stand alone (they would swallow single presses). */
fun Config.removeRemap(keyCode: Int, press: Press): Config {
    val left = remaps.filterNot { it.keyCode == keyCode && it.press == press }
    return copy(remaps = left.filter { it.keyCode != keyCode || canRemap(it.keyCode, it.press, left) })
}

/** OK and the D-pad: remapping their single press would lock the owner out of every screen, Setup included. */
fun isNavKey(keyCode: Int) = keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * A consumed key can't be re-injected, so double/long needs a single remap unless the single press is reproducible (Home, Back).
 * Nav keys can't have a single remap, so they can't have any.
 */
fun canRemap(keyCode: Int, press: Press, existing: List<Remap>): Boolean = !isNavKey(keyCode) && (
    press == Press.SINGLE || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK ||
        existing.any { it.keyCode == keyCode && it.press == Press.SINGLE })

fun Config.resetRemaps() = copy(remaps = DEFAULT_REMAPS)

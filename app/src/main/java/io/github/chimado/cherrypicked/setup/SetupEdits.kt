package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.home.HomeModel
import io.github.chimado.cherrypicked.store.Config

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

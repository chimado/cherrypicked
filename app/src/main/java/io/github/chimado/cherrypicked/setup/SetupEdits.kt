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

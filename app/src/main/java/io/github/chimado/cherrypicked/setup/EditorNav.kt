package io.github.chimado.cherrypicked.setup

import io.github.chimado.cherrypicked.home.HomeModel

enum class Nav { LEFT, RIGHT, UP, DOWN, OK, BACK }
enum class Zone { FAVS, OPTIONS, SHELF }
enum class Opt(val label: String) { RENAME("Rename"), MAIN("Set as main"), REMOVE("Remove") }

/** What a key asks the editor to do to the config; entries are packages or `input:<id>`. */
sealed interface Edit {
    data class Move(val entry: String, val d: Int) : Edit
    data class Add(val entry: String) : Edit
    data class Remove(val entry: String) : Edit
    data class Main(val entry: String) : Edit
    data class Rename(val entry: String) : Edit
    object Full : Edit
    object Exit : Edit
}

/** Focus and key handling of the favorites editor; after applying an [Edit] the host calls [sync]. */
class EditorNav(favs: List<String>, shelf: List<String>) {
    var favs = favs; private set
    var shelf = shelf; private set
    var zone = Zone.FAVS; private set
    var fav = 0; private set
    var shelfCol = 0; private set
    var opt = 0; private set
    /** The focused favorite is picked up: Left and Right move it. */
    var held = false; private set

    init { sync(favs, shelf) }

    /** Rename is for inputs only: apps keep their own names. */
    val options get() = favs.getOrNull(fav)?.let { listOfNotNull(Opt.RENAME.takeIf { _ -> it.startsWith("input:") }, Opt.MAIN, Opt.REMOVE) }.orEmpty()

    fun sync(favs: List<String>, shelf: List<String>) {
        this.favs = favs; this.shelf = shelf
        fav = fav.coerceIn(0, maxOf(0, favs.lastIndex))
        shelfCol = shelfCol.coerceIn(0, maxOf(0, shelf.lastIndex))
        if (favs.isEmpty()) { zone = Zone.SHELF; held = false } else if (shelf.isEmpty() && zone == Zone.SHELF) zone = Zone.FAVS
    }

    fun key(k: Nav): Edit? {
        val d = when (k) { Nav.LEFT -> -1; Nav.RIGHT -> 1; else -> 0 }
        when (zone) {
            Zone.FAVS -> when {
                d != 0 -> if (fav + d in favs.indices) { fav += d; if (held) return Edit.Move(favs[fav - d], d) }
                k == Nav.OK -> held = !held && favs.isNotEmpty()
                k == Nav.BACK -> if (held) held = false else return Edit.Exit
                k == Nav.DOWN -> if (held) { zone = Zone.OPTIONS; opt = 0 } else if (shelf.isNotEmpty()) zone = Zone.SHELF
            }
            Zone.OPTIONS -> when {
                d != 0 -> opt = (opt + d).coerceIn(0, options.lastIndex)
                k == Nav.UP -> zone = Zone.FAVS
                k == Nav.BACK -> { zone = Zone.FAVS; held = false }
                k == Nav.OK -> return when (options[opt]) {
                    Opt.RENAME -> Edit.Rename(favs[fav])
                    Opt.MAIN -> Edit.Main(favs[fav])
                    Opt.REMOVE -> { zone = Zone.FAVS; held = false; Edit.Remove(favs[fav]) }
                }
            }
            Zone.SHELF -> when {
                d != 0 -> shelfCol = (shelfCol + d).coerceIn(0, maxOf(0, shelf.lastIndex))
                k == Nav.UP -> if (favs.isNotEmpty()) zone = Zone.FAVS
                k == Nav.BACK -> return Edit.Exit
                k == Nav.OK -> return shelf.getOrNull(shelfCol)?.let { if (favs.size >= HomeModel.MAX_FAVORITES) Edit.Full else Edit.Add(it) }
            }
        }
        return null
    }
}

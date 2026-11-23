package io.github.chimado.cherrypicked.store

import io.github.chimado.cherrypicked.palette
import io.github.chimado.cherrypicked.setup.canRemap
import io.github.chimado.cherrypicked.setup.hasWayHome

const val CONFIG_FILE_NAME = "cherrypicked.json"
const val MAX_IMPORT_BYTES = 256 * 1024
const val MAX_IMPORT_ENTRIES = 500

sealed class ImportCheck {
    data class Ok(val config: Config, val summary: List<String>) : ImportCheck()
    data class Refused(val reason: String) : ImportCheck()
}

/** Validates an exported setup and summarises it; [installed] marks missing apps (inputs are never marked). */
fun checkImport(text: String, installed: Set<String>, label: (String) -> String): ImportCheck {
    val c = try { Config.fromJson(text) } catch (e: ConfigFormatException) {
        return ImportCheck.Refused(if (e.message == NEWER_VERSION) "This file is from a newer Cherrypicked" else "This file isn't a Cherrypicked setup")
    } catch (_: StackOverflowError) { // Android's org.json recurses without a depth limit
        return ImportCheck.Refused("This file isn't a Cherrypicked setup")
    }
    // Setup builds one row per entry; a huge list would hang it on every open.
    if (maxOf(maxOf(c.favorites.size, c.hidden.size, c.declined.size, c.remaps.size), c.frozenRow.size, c.inputNames.size) > MAX_IMPORT_ENTRIES) return ImportCheck.Refused("This file isn't a Cherrypicked setup")
    // Only remaps Setup itself could make: the engine ignores nav keys, so a Home/QuickPanel remap there isn't a way home.
    if (!c.remaps.all { canRemap(it.keyCode, it.press, c.remaps) }) return ImportCheck.Refused("This file has button settings Cherrypicked can't use")
    if (!hasWayHome(c.remaps)) return ImportCheck.Refused("This setup leaves no way back to Cherrypicked")
    fun name(e: String) = label(e) + if (!e.startsWith("input:") && e !in installed) " (not installed)" else ""
    return ImportCheck.Ok(c, listOf(
        "Favorites: " + (c.favorites.joinToString(", ", transform = ::name).ifEmpty { "none" }),
        "Main app: " + (c.mainApp?.let(::name) ?: "none"),
        "Open main app on start: " + if (c.autoOpen) "On" else "Off",
        "Hidden apps: ${c.hidden.size}",
        "Buttons: ${c.remaps.size}",
        "Simple mode: " + if (c.pin != null) "On" else "Off",
        palette(c).let { "Appearance: ${it.pattern}, ${it.accentName}, ${it.baseName}" },
    ))
}

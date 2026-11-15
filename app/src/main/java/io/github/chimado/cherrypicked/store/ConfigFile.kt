package io.github.chimado.cherrypicked.store

import io.github.chimado.cherrypicked.setup.hasWayHome

const val CONFIG_FILE_NAME = "cherrypicked.json"
const val MAX_IMPORT_BYTES = 256 * 1024

sealed class ImportCheck {
    data class Ok(val config: Config, val summary: List<String>) : ImportCheck()
    data class Refused(val reason: String) : ImportCheck()
}

/** Validates an exported setup and summarises it; [installed] marks missing apps (inputs are never marked). */
fun checkImport(text: String, installed: Set<String>, label: (String) -> String): ImportCheck {
    val c = try { Config.fromJson(text) } catch (e: ConfigFormatException) {
        return ImportCheck.Refused(if (e.message?.contains("newer") == true) "This file is from a newer Cherrypicked" else "This file isn't a Cherrypicked setup")
    }
    if (!hasWayHome(c.remaps)) return ImportCheck.Refused("This setup leaves no way back to Cherrypicked")
    fun name(e: String) = label(e) + if (!e.startsWith("input:") && e !in installed) " (not installed)" else ""
    return ImportCheck.Ok(c, listOf(
        "Favorites: " + (c.favorites.joinToString(", ", transform = ::name).ifEmpty { "none" }),
        "Main app: " + (c.mainApp?.let(::name) ?: "none"),
        "Open main app on start: " + if (c.autoOpen) "On" else "Off",
        "Hidden apps: ${c.hidden.size}",
        "Buttons: ${c.remaps.size}",
    ))
}

package io.github.chimado.cherrypicked.store

import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.Press
import io.github.chimado.cherrypicked.keys.Remap
import io.github.chimado.cherrypicked.keys.decodeAction
import io.github.chimado.cherrypicked.keys.encode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ConfigFormatException(msg: String) : Exception(msg)
const val NEWER_VERSION = "newer config version" // the one ConfigFormatException message callers tell apart

val DEFAULT_REMAPS = listOf(Remap(3, Press.LONG, Action.QuickPanel))

const val MAX_INPUT_NAME = 20
fun isPin(s: String) = s.length == 4 && s.all { it in '0'..'9' }

/** Entries are package names; [declined] holds "fav:<pkg>" and "main:<pkg>" keys. */
data class Config(
    val version: Int = 1,
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val mainApp: String? = null,
    val autoOpen: Boolean = false,
    val declined: Set<String> = emptySet(),
    val hintShown: Boolean = false,
    val remaps: List<Remap> = DEFAULT_REMAPS,
    val pattern: String = "Plain", // preset names; unknown ones resolve to the default in palette()
    val accent: String = "Cherry",
    val base: String = "Graphite",
    val inputNames: Map<String, String> = emptyMap(), // input id to nickname
    val pin: String? = null, // four digits; set means simple mode is on
    val frozenRow: List<String> = emptyList(), // every app in the order it ranked when simple mode was turned on
) {
    companion object
}

fun Config.toJson(): String = JSONObject().apply {
    put("version", version)
    put("favorites", JSONArray(favorites))
    put("hidden", JSONArray(hidden))
    mainApp?.let { put("mainApp", it) }
    put("autoOpen", autoOpen)
    put("declined", JSONArray(declined))
    put("hintShown", hintShown)
    put("pattern", pattern)
    put("accent", accent)
    put("base", base)
    put("inputNames", JSONObject(inputNames))
    pin?.let { put("pin", it) }
    put("frozenRow", JSONArray(frozenRow))
    put("remaps", JSONArray().apply {
        remaps.forEach { put(JSONObject().put("key", it.keyCode).put("press", it.press.name).put("action", it.action.encode())) }
    })
}.toString()

fun Config.Companion.fromJson(s: String): Config = try {
    val o = JSONObject(s)
    val d = Config()
    if (o.has("version") && o.getInt("version") > 1) throw JSONException(NEWER_VERSION)
    fun strings(k: String): List<String>? = if (!o.has(k)) null else o.getJSONArray(k).let { a ->
        List(a.length()) { i -> a.get(i) as? String ?: throw JSONException("$k[$i] not a string") }
    }
    fun str(k: String, def: String): String = if (o.has(k)) o.get(k) as? String ?: throw JSONException("$k not a string") else def
    fun <T> opt(k: String, get: (String) -> T, def: T): T = if (o.has(k)) get(k) else def
    Config(
        version = opt("version", o::getInt, d.version),
        favorites = strings("favorites") ?: d.favorites,
        hidden = strings("hidden")?.toSet() ?: d.hidden,
        mainApp = if (o.has("mainApp")) o.get("mainApp") as? String ?: throw JSONException("mainApp not a string") else null,
        autoOpen = opt("autoOpen", o::getBoolean, d.autoOpen),
        declined = strings("declined")?.toSet() ?: d.declined,
        hintShown = opt("hintShown", o::getBoolean, d.hintShown),
        // Lenient per entry (forward compat): unknown action/press entries are skipped.
        remaps = if (!o.has("remaps")) d.remaps else o.getJSONArray("remaps").let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.optJSONObject(i) ?: return@mapNotNull null
                val press = runCatching { Press.valueOf(e.optString("press")) }.getOrNull() ?: return@mapNotNull null
                val action = decodeAction(e.optString("action")) ?: return@mapNotNull null
                val key = (e.opt("key") as? Number)?.toInt() ?: return@mapNotNull null
                Remap(key, press, action)
            }.associateBy { it.keyCode to it.press }.values.toList() // duplicate (key, press): last wins
        },
        pattern = str("pattern", d.pattern),
        accent = str("accent", d.accent),
        base = str("base", d.base),
        inputNames = if (!o.has("inputNames")) d.inputNames else o.getJSONObject("inputNames").let { n ->
            n.keys().asSequence().associateWith { k -> (n.get(k) as? String ?: throw JSONException("inputNames.$k not a string")).trim().take(MAX_INPUT_NAME) }
                .filterValues { it.isNotEmpty() }
        },
        // A pin nobody can type on the pad would lock the owner out (short of the bypass), so it is refused.
        pin = if (!o.has("pin")) null else (o.get("pin") as? String)?.takeIf(::isPin) ?: throw JSONException("pin not four digits"),
        frozenRow = strings("frozenRow") ?: d.frozenRow,
    )
} catch (e: JSONException) {
    throw ConfigFormatException(e.message ?: "bad config")
}

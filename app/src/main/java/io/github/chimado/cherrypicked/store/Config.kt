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

val DEFAULT_REMAPS = listOf(Remap(3, Press.LONG, Action.QuickPanel))

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
    put("remaps", JSONArray().apply {
        remaps.forEach { put(JSONObject().put("key", it.keyCode).put("press", it.press.name).put("action", it.action.encode())) }
    })
}.toString()

fun Config.Companion.fromJson(s: String): Config = try {
    val o = JSONObject(s)
    val d = Config()
    if (o.has("version") && o.getInt("version") > 1) throw JSONException("newer config version")
    fun strings(k: String): List<String>? = if (!o.has(k)) null else o.getJSONArray(k).let { a ->
        List(a.length()) { i -> a.get(i) as? String ?: throw JSONException("$k[$i] not a string") }
    }
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
                if (!e.has("key")) null else Remap(e.optInt("key"), press, action)
            }
        },
    )
} catch (e: JSONException) {
    throw ConfigFormatException(e.message ?: "bad config")
}

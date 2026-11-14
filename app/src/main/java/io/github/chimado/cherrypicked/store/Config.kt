package io.github.chimado.cherrypicked.store

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ConfigFormatException(msg: String) : Exception(msg)

/** Entries are package names; [declined] holds "fav:<pkg>" and "main:<pkg>" keys. */
data class Config(
    val version: Int = 1,
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val mainApp: String? = null,
    val autoOpen: Boolean = false,
    val declined: Set<String> = emptySet(),
    val hintShown: Boolean = false,
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
    )
} catch (e: JSONException) {
    throw ConfigFormatException(e.message ?: "bad config")
}

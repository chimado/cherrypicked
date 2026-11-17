package io.github.chimado.cherrypicked.rank

import io.github.chimado.cherrypicked.store.writeDurably
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.pow

const val HALF_LIFE_MS = 14L * 24 * 3600 * 1000
const val MAX_EVENTS_PER_APP = 50
private const val HOUR_MS = 3600_000L

class UsageLedger(private val file: File) {
    private var cache: Map<String, List<Long>> = emptyMap()
    private var cacheAt = 0L // lastModified of the file [cache] came from; 0 = none

    /** Parses only when the file changed since the last read or write. */
    private fun read(): Map<String, List<Long>> {
        val m = file.lastModified()
        if (m != 0L && m == cacheAt) return cache
        return parse().also { cache = it; cacheAt = m }
    }

    private fun parse(): Map<String, List<Long>> = try {
        val o = JSONObject(file.readText())
        o.keys().asSequence().associateWith { k ->
            val a = o.getJSONArray(k)
            List(a.length()) { a.getLong(it) }
        }
    } catch (_: JSONException) {
        emptyMap()
    } catch (_: IOException) {
        emptyMap() // missing/unreadable ledger is just "no history"
    }

    fun recordLaunch(pkg: String, nowMs: Long) {
        val m = read().toMutableMap()
        m[pkg] = ((m[pkg] ?: emptyList()) + nowMs).takeLast(MAX_EVENTS_PER_APP)
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        try {
            file.writeDurably(o.toString())
            cache = m
            cacheAt = file.lastModified()
        } catch (_: IOException) {
            // best-effort data: losing one launch must not break launching
        }
    }

    fun scores(nowMs: Long, foregroundMsLast14d: Map<String, Long> = emptyMap()): Map<String, Double> {
        val out = HashMap<String, Double>()
        read().forEach { (pkg, ts) ->
            out[pkg] = ts.sumOf { 0.5.pow((nowMs - it).coerceAtLeast(0).toDouble() / HALF_LIFE_MS) }
        }
        foregroundMsLast14d.forEach { (pkg, ms) ->
            out[pkg] = (out[pkg] ?: 0.0) + ms.toDouble() / HOUR_MS
        }
        return out
    }
}

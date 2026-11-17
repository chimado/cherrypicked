package io.github.chimado.cherrypicked.store

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ConfigStore(private val dir: File) {
    private val file get() = File(dir, "config.json")
    private var loaded = Config()
    private var loadedAt = 0L // lastModified of the file [loaded] came from; 0 = none

    /** Parses only when the file changed since the last load or save through this store. */
    fun load(): Config {
        val m = file.lastModified()
        if (m != 0L && m == loadedAt) return loaded
        return (parse() ?: return Config()).also { loaded = it; loadedAt = m }
    }

    /** null = unreadable right now: use defaults for this call but cache nothing, so a later save can't persist them. */
    private fun parse(): Config? {
        if (!file.exists()) return Config()
        return try {
            Config.fromJson(file.readText())
        } catch (e: ConfigFormatException) {
            keepBad()
            Config()
        } catch (e: IOException) {
            null // never crash boot on unreadable config
        }
    }

    private fun keepBad() {
        try { file.copyTo(File(dir, "config.json.bad"), overwrite = true) } catch (_: IOException) {}
    }

    fun save(c: Config) {
        file.writeDurably(c.toJson())
        loaded = c
        loadedAt = file.lastModified()
    }
}

/** Writes via `<name>.tmp`, fsyncs it, then renames over this file, so a power cut leaves the old or the new content. */
fun File.writeDurably(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    try {
        FileOutputStream(tmp).use { it.write(text.toByteArray()); it.fd.sync() }
        if (!tmp.renameTo(this)) throw IOException("rename failed")
    } catch (e: IOException) {
        tmp.delete()
        throw e
    }
}

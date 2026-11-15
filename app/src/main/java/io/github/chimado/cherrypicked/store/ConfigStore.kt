package io.github.chimado.cherrypicked.store

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ConfigStore(private val dir: File) {
    private val file get() = File(dir, "config.json")

    fun load(): Config {
        if (!file.exists()) return Config()
        return try {
            Config.fromJson(file.readText())
        } catch (e: ConfigFormatException) {
            keepBad()
            Config()
        } catch (e: IOException) {
            Config() // never crash boot on unreadable config
        }
    }

    private fun keepBad() {
        try { file.copyTo(File(dir, "config.json.bad"), overwrite = true) } catch (_: IOException) {}
    }

    fun save(c: Config) = file.writeDurably(c.toJson())
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

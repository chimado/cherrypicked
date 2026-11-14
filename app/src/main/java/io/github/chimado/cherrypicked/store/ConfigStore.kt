package io.github.chimado.cherrypicked.store

import java.io.File
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

    fun save(c: Config) {
        val tmp = File(dir, "config.json.tmp")
        tmp.writeText(c.toJson())
        if (!tmp.renameTo(file)) throw IOException("rename failed")
    }
}

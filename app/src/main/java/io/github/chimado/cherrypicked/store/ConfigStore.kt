package io.github.chimado.cherrypicked.store

import java.io.File

class ConfigStore(private val dir: File) {
    private val file get() = File(dir, "config.json")

    fun load(): Config {
        if (!file.exists()) return Config()
        return try {
            Config.fromJson(file.readText())
        } catch (e: ConfigFormatException) {
            file.copyTo(File(dir, "config.json.bad"), overwrite = true)
            Config()
        }
    }

    fun save(c: Config) {
        val tmp = File(dir, "config.json.tmp")
        tmp.writeText(c.toJson())
        if (!tmp.renameTo(file)) throw java.io.IOException("rename failed")
    }
}

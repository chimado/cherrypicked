package io.github.chimado.cherrypicked.a11y

import io.github.chimado.cherrypicked.store.Config

enum class Trigger { BOOT, WAKE }

object AutoOpenPolicy {
    const val WAKE_SETTLE_MS = 2000L

    /** Package to open, or null. A wake onto a TV input (HDMI/CEC took the screen) or already in main does nothing. */
    fun decide(
        trigger: Trigger,
        config: Config,
        installed: Set<String>,
        foregroundAfterSettle: String?,
        tvInputPackages: Set<String>,
    ): String? {
        val main = config.mainApp ?: return null
        if (!config.autoOpen || main !in installed) return null
        if (trigger == Trigger.WAKE && (foregroundAfterSettle in tvInputPackages || foregroundAfterSettle == main)) return null
        return main
    }
}

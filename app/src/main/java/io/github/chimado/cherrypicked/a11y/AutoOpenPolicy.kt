package io.github.chimado.cherrypicked.a11y

import io.github.chimado.cherrypicked.store.Config

enum class Trigger { BOOT, WAKE }

object AutoOpenPolicy {
    const val HOME_AFTER_WAKE_MS = 2000L

    /** Our home showing just after wake was started by the TV (remote power button), not chosen: check the wake again. */
    fun recheckOnHome(windowPkg: String, self: String, sinceWakeMs: Long) =
        windowPkg == self && sinceWakeMs in 0..HOME_AFTER_WAKE_MS

    /**
     * Package to open, or null. A wake onto a TV input (HDMI/CEC took the screen), already in main, or with an
     * unknown foreground does nothing: when in doubt, never take the screen.
     */
    fun decide(
        trigger: Trigger,
        config: Config,
        installed: Set<String>,
        foregroundAfterSettle: String?,
        tvInputPackages: Set<String>,
    ): String? {
        val main = config.mainApp ?: return null
        if (!config.autoOpen || main !in installed) return null
        if (trigger == Trigger.WAKE &&
            (foregroundAfterSettle == null || foregroundAfterSettle in tvInputPackages || foregroundAfterSettle == main)) return null
        return main
    }
}

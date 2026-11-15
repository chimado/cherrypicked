package io.github.chimado.cherrypicked.health

import android.content.Context
import android.provider.Settings

object HomeHealth {
    const val COMPONENT = "io.github.chimado.cherrypicked/.a11y.CpAccessibilityService"
    private const val FULL = "io.github.chimado.cherrypicked/io.github.chimado.cherrypicked.a11y.CpAccessibilityService"

    fun isEnabled(ctx: Context): Boolean =
        listed(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))

    /** [enabled] is the colon-separated ENABLED_ACCESSIBILITY_SERVICES value; entries may be short or full form. */
    fun listed(enabled: String?): Boolean =
        enabled?.split(':')?.any { it.trim().let { e -> e.equals(COMPONENT, true) || e.equals(FULL, true) } } == true
}

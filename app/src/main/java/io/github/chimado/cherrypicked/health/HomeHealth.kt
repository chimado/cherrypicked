package io.github.chimado.cherrypicked.health

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object HomeHealth {
    const val COMPONENT = "io.github.chimado.cherrypicked/.a11y.CpAccessibilityService"
    private const val FULL = "io.github.chimado.cherrypicked/io.github.chimado.cherrypicked.a11y.CpAccessibilityService"

    /** Listed in the setting AND actually bound: TCL lists the service but never binds it without APP_AUTO_START. */
    fun isEnabled(ctx: Context): Boolean {
        if (!listed(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES))) return false
        val ours = ComponentName.unflattenFromString(COMPONENT)
        return ctx.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) } == ours }
    }

    /** [enabled] is the colon-separated ENABLED_ACCESSIBILITY_SERVICES value; entries may be short or full form. */
    fun listed(enabled: String?): Boolean =
        enabled?.split(':')?.any { it.trim().let { e -> e.equals(COMPONENT, true) || e.equals(FULL, true) } } == true
}

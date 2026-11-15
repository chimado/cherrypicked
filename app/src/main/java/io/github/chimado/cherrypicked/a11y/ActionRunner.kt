package io.github.chimado.cherrypicked.a11y

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import io.github.chimado.cherrypicked.apps.AppCatalog
import io.github.chimado.cherrypicked.home.HomeActivity
import io.github.chimado.cherrypicked.keys.Action

class ActionRunner(private val svc: AccessibilityService, private val cycleSleepTimer: () -> Unit) {
    fun run(a: Action) {
        when (a) {
            Action.Home -> svc.startActivity(Intent(svc, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Action.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            is Action.OpenApp -> if (!AppCatalog(svc).launch(a.pkg)) Log.w("CP", "remap app missing ${a.pkg}")
            Action.ScreenOff -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            // ponytail: placeholders until Tasks 6-7 land
            is Action.OpenInput -> Log.i("CP", "inputs not ready ${a.inputId}")
            Action.QuickPanel -> Log.i("CP", "quick_panel not ready")
            Action.SleepTimer -> cycleSleepTimer()
            Action.Nothing -> {}
        }
    }

    private fun global(g: Int) { if (!svc.performGlobalAction(g)) Log.w("CP", "global action $g failed") }
}

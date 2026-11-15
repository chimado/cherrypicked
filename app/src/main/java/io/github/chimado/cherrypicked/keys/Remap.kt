package io.github.chimado.cherrypicked.keys

enum class Press { SINGLE, DOUBLE, LONG }

sealed interface Action {
    data class OpenApp(val pkg: String) : Action
    data class OpenInput(val inputId: String) : Action
    object Home : Action
    object Back : Action
    object QuickPanel : Action
    object SleepTimer : Action
    object ScreenOff : Action
    object Nothing : Action
}

data class Remap(val keyCode: Int, val press: Press, val action: Action)

fun Action.encode(): String = when (this) {
    is Action.OpenApp -> "app:$pkg"
    is Action.OpenInput -> "input:$inputId"
    Action.Home -> "home"
    Action.Back -> "back"
    Action.QuickPanel -> "quick_panel"
    Action.SleepTimer -> "sleep_timer"
    Action.ScreenOff -> "screen_off"
    Action.Nothing -> "nothing"
}

fun decodeAction(s: String): Action? = when {
    s.startsWith("app:") && s.length > 4 -> Action.OpenApp(s.removePrefix("app:"))
    s.startsWith("input:") && s.length > 6 -> Action.OpenInput(s.removePrefix("input:"))
    s == "home" -> Action.Home
    s == "back" -> Action.Back
    s == "quick_panel" -> Action.QuickPanel
    s == "sleep_timer" -> Action.SleepTimer
    s == "screen_off" -> Action.ScreenOff
    s == "nothing" -> Action.Nothing
    else -> null
}

fun List<Remap>.forKey(keyCode: Int): Map<Press, Action> =
    filter { it.keyCode == keyCode }.associate { it.press to it.action }

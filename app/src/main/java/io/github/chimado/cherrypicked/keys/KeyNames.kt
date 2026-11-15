package io.github.chimado.cherrypicked.keys

import android.view.KeyEvent as K

object KeyNames {
    // Vendor app buttons (Netflix, YouTube, Prime Video) send vendor codes with no KeyEvent constant; they fall back until probed on the TV.
    private val names = mapOf(
        K.KEYCODE_HOME to "Home", K.KEYCODE_BACK to "Back",
        K.KEYCODE_DPAD_CENTER to "OK", K.KEYCODE_ENTER to "OK",
        K.KEYCODE_DPAD_UP to "Up", K.KEYCODE_DPAD_DOWN to "Down", K.KEYCODE_DPAD_LEFT to "Left", K.KEYCODE_DPAD_RIGHT to "Right",
        K.KEYCODE_MENU to "Menu", K.KEYCODE_SETTINGS to "Settings", K.KEYCODE_GUIDE to "Guide", K.KEYCODE_INFO to "Info",
        K.KEYCODE_VOLUME_UP to "Volume Up", K.KEYCODE_VOLUME_DOWN to "Volume Down", K.KEYCODE_VOLUME_MUTE to "Mute",
        K.KEYCODE_CHANNEL_UP to "Channel Up", K.KEYCODE_CHANNEL_DOWN to "Channel Down",
        K.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause",
        K.KEYCODE_PROG_RED to "Red", K.KEYCODE_PROG_GREEN to "Green", K.KEYCODE_PROG_YELLOW to "Yellow", K.KEYCODE_PROG_BLUE to "Blue",
        K.KEYCODE_TV_INPUT to "TV Input",
    )

    fun label(keyCode: Int): String = names[keyCode] ?: fallback(keyCode)

    // The android.jar stub throws in plain unit tests, so fall back to the bare number there.
    private fun fallback(keyCode: Int): String = try {
        K.keyCodeToString(keyCode).removePrefix("KEYCODE_").split('_')
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar(Char::uppercase) }
    } catch (_: RuntimeException) { "Key $keyCode" }
}

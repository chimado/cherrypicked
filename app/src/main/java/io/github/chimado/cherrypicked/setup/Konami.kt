package io.github.chimado.cherrypicked.setup

import android.view.KeyEvent

private val CODE = intArrayOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER,
)

/** The way past the PIN pad without the PIN: Up Up Down Down Left Right Left Right Back OK. */
class Konami {
    private var i = 0

    /** The next key of the code is Back, so Back must not leave the screen. */
    val armed get() = CODE[i] == KeyEvent.KEYCODE_BACK

    /** Feed every key down; true when [keyCode] completes the code. */
    fun feed(keyCode: Int): Boolean {
        val k = if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) KeyEvent.KEYCODE_DPAD_CENTER else keyCode
        i = when {
            k == CODE[i] -> i + 1
            k == KeyEvent.KEYCODE_DPAD_UP -> if (i == 2) 2 else 1 // a third Up is still "Up Up"
            else -> 0
        }
        return (i == CODE.size).also { if (it) i = 0 }
    }
}

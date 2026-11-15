package io.github.chimado.cherrypicked.a11y

object HomeKeyPolicy {
    const val KEYCODE_HOME = 3
    fun intercept(keyCode: Int): Boolean = keyCode == KEYCODE_HOME
}

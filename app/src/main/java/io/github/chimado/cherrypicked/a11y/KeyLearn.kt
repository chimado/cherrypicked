package io.github.chimado.cherrypicked.a11y

/** Broadcast contract between Setup (:setup) and the service (:a11y). Both are package-scoped and non-exported. */
object KeyLearn {
    const val ACTION_START = "io.github.chimado.cherrypicked.LEARN_START"
    const val ACTION_RESULT = "io.github.chimado.cherrypicked.LEARN_RESULT"
    const val EXTRA_KEY = "key"
    const val WINDOW_MS = 10_000L
    /** Setup -> service after every config save, so remaps reload without a per-key stat. */
    const val ACTION_CONFIG_CHANGED = "io.github.chimado.cherrypicked.CONFIG_CHANGED"
}

package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config

sealed interface Suggestion {
    data class MainApp(val app: AppEntry) : Suggestion
    data class Favorite(val app: AppEntry) : Suggestion
}

/** Proposes at most one thing; the caller applies it only after an explicit yes. */
object Suggester {
    const val MIN_SCORE = 3.0

    fun next(apps: List<AppEntry>, config: Config, scores: Map<String, Double>): Suggestion? {
        fun best(ok: (AppEntry) -> Boolean) = apps
            .filter { it.pkg !in config.hidden && (scores[it.pkg] ?: 0.0) >= MIN_SCORE && ok(it) }
            .maxByOrNull { scores[it.pkg] ?: 0.0 }
        // A declined top pick means no main-app prompt at all, not a runner-up.
        if (config.mainApp == null) best { true }?.takeIf { "main:${it.pkg}" !in config.declined }?.let { return Suggestion.MainApp(it) }
        if (config.favorites.size < HomeModel.MAX_FAVORITES)
            best { it.pkg !in config.favorites && "fav:${it.pkg}" !in config.declined }?.let { return Suggestion.Favorite(it) }
        return null
    }
}

fun Config.accept(s: Suggestion): Config = when (s) {
    is Suggestion.MainApp -> copy(mainApp = s.app.pkg, autoOpen = true)
    is Suggestion.Favorite -> copy(favorites = favorites + s.app.pkg)
}

fun Config.decline(s: Suggestion): Config = copy(
    declined = declined + when (s) {
        is Suggestion.MainApp -> "main:${s.app.pkg}"
        is Suggestion.Favorite -> "fav:${s.app.pkg}"
    },
)

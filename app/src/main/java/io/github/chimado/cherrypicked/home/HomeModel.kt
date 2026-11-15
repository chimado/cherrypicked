package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config

data class AppEntry(val pkg: String, val label: String)

sealed interface Tile {
    data class App(val app: AppEntry) : Tile
    object PickFavorites : Tile
    object AllApps : Tile
    object Setup : Tile
    object FixHome : Tile
}

data class HomeRows(val favorites: List<Tile>, val tier2: List<Tile>)

object HomeModel {
    const val MAX_FAVORITES = 5
    const val MAX_TIER2_APPS = 8

    fun build(apps: List<AppEntry>, config: Config, scores: Map<String, Double>, homeBroken: Boolean): HomeRows {
        val byPkg = apps.associateBy { it.pkg }
        val favs = config.favorites
            .filter { it !in config.hidden }
            .mapNotNull { byPkg[it] }
            .take(MAX_FAVORITES)
        val favPkgs = favs.map { it.pkg }.toSet()
        val tier2 = rankAll(apps, config, scores)
            .filter { it.pkg !in favPkgs }
            .take(MAX_TIER2_APPS)
            .map<AppEntry, Tile> { Tile.App(it) } +
            listOfNotNull(Tile.FixHome.takeIf { homeBroken }) + Tile.AllApps + Tile.Setup
        return HomeRows(if (favs.isEmpty()) listOf(Tile.PickFavorites) else favs.map { Tile.App(it) }, tier2)
    }

    fun rankAll(apps: List<AppEntry>, config: Config, scores: Map<String, Double>): List<AppEntry> =
        apps.filter { it.pkg !in config.hidden }
            .sortedWith(compareByDescending<AppEntry> { scores[it.pkg] ?: 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

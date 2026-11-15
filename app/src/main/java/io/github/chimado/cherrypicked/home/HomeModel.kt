package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config

data class TvInput(val id: String, val label: String)

data class AppEntry(val pkg: String, val label: String)

sealed interface Tile {
    data class App(val app: AppEntry) : Tile
    data class Input(val input: TvInput) : Tile
    object PickFavorites : Tile
    object AllApps : Tile
    object Setup : Tile
    object FixHome : Tile
}

data class HomeRows(val favorites: List<Tile>, val tier2: List<Tile>)

object HomeModel {
    const val MAX_FAVORITES = 5
    const val MAX_TIER2_APPS = 6

    fun build(apps: List<AppEntry>, config: Config, scores: Map<String, Double>, homeBroken: Boolean, inputs: List<TvInput> = emptyList()): HomeRows {
        val byPkg = apps.associateBy { it.pkg }
        val byInput = inputs.associateBy { "input:${it.id}" }
        val favs = config.favorites
            .filter { it !in config.hidden }
            .mapNotNull { f -> byInput[f]?.let { Tile.Input(it) } ?: byPkg[f]?.let { Tile.App(it) } }
            .take(MAX_FAVORITES)
        val favPkgs = favs.mapNotNull { (it as? Tile.App)?.app?.pkg }.toSet()
        val tier2 = rankAll(apps, config, scores)
            .filter { it.pkg !in favPkgs }
            .take(MAX_TIER2_APPS)
            .map<AppEntry, Tile> { Tile.App(it) } +
            listOfNotNull(Tile.FixHome.takeIf { homeBroken }) + Tile.AllApps + Tile.Setup
        return HomeRows(if (favs.isEmpty()) listOf(Tile.PickFavorites) else favs, tier2)
    }

    /** Scale (<= 1) that makes [n] tiles of [tile] width with [gap] between them fit in [max] (all in the same unit). */
    fun fitScale(n: Int, tile: Float, gap: Float, max: Float): Float = minOf(1f, max / (n * tile + (n - 1) * gap))

    fun rankAll(apps: List<AppEntry>, config: Config, scores: Map<String, Double>): List<AppEntry> =
        apps.filter { it.pkg !in config.hidden }
            .sortedWith(compareByDescending<AppEntry> { scores[it.pkg] ?: 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

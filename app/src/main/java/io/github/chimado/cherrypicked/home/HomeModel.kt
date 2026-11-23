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
        val tier2 = tier2Apps(apps, config, scores, favPkgs).map<AppEntry, Tile> { Tile.App(it) } +
            listOfNotNull(Tile.FixHome.takeIf { homeBroken }) + Tile.AllApps + Tile.Setup
        return HomeRows(if (favs.isEmpty()) listOf(Tile.PickFavorites) else favs, tier2)
    }

    /** The lower row's apps: the most used, or in simple mode the ranking as it was when the lock was turned on. */
    fun tier2Apps(apps: List<AppEntry>, config: Config, scores: Map<String, Double>, favPkgs: Set<String>): List<AppEntry> {
        val ranked = rankAll(apps, config, scores) // also drops hidden apps
        // Locked: the frozen order, then anything it doesn't know (installed since, or a lock imported without a row) by name.
        val row = if (config.pin == null) ranked else config.frozenRow.mapNotNull { p -> ranked.firstOrNull { it.pkg == p } }
            .let { frozen -> frozen + (ranked - frozen.toSet()).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
        return row.filter { it.pkg !in favPkgs }.take(MAX_TIER2_APPS)
    }

    /** Scale (<= 1) that makes [n] tiles of [tile] width with [gap] between them fit in [max] (all in the same unit). */
    fun fitScale(n: Int, tile: Float, gap: Float, max: Float): Float = minOf(1f, max / (n * tile + (n - 1) * gap))

    fun rankAll(apps: List<AppEntry>, config: Config, scores: Map<String, Double>): List<AppEntry> =
        apps.filter { it.pkg !in config.hidden }
            .sortedWith(compareByDescending<AppEntry> { scores[it.pkg] ?: 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

/** Inputs under the names the owner gave them. */
fun List<TvInput>.named(c: Config) = map { i -> c.inputNames[i.id]?.let { i.copy(label = it) } ?: i }

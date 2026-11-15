package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeModelTest {
    private fun app(p: String, l: String = p) = AppEntry(p, l)
    private fun cfg(fav: List<String> = emptyList(), hidden: Set<String> = emptySet()) =
        Config(favorites = fav, hidden = hidden)
    private fun pkgs(t: List<Tile>) = t.map { (it as? Tile.App)?.app?.pkg ?: it.toString() }

    @Test fun favoritesInConfigOrder() {
        val r = HomeModel.build(listOf(app("a"), app("b"), app("c")), cfg(listOf("c", "a")), emptyMap(), false)
        assertEquals(listOf("c", "a"), pkgs(r.favorites))
    }

    @Test fun uninstalledFavoritesAreSkipped() {
        val r = HomeModel.build(listOf(app("a")), cfg(listOf("zz", "a")), emptyMap(), false)
        assertEquals(listOf("a"), pkgs(r.favorites))
    }

    @Test fun hiddenNeverShown() {
        val r = HomeModel.build(listOf(app("a"), app("b")), cfg(listOf("a"), setOf("a", "b")), emptyMap(), false)
        assertEquals(listOf(Tile.PickFavorites), r.favorites)
        assertEquals(listOf(Tile.AllApps, Tile.Setup), r.tier2)
    }

    @Test fun emptyFavoritesShowsPick() {
        val r = HomeModel.build(listOf(app("a")), cfg(), emptyMap(), false)
        assertEquals(listOf(Tile.PickFavorites), r.favorites)
    }

    @Test fun tier2RankedAndCapped() {
        val apps = (1..12).map { app("p$it") }
        val scores = apps.associate { it.pkg to it.pkg.drop(1).toDouble() }
        val r = HomeModel.build(apps, cfg(), scores, false)
        assertEquals((12 downTo 7).map { "p$it" } + listOf("AllApps", "Setup").map { if (it == "AllApps") Tile.AllApps.toString() else Tile.Setup.toString() }, pkgs(r.tier2))
    }

    @Test fun rowsFitTheScreen() {
        assertEquals(1f, HomeModel.fitScale(3, 22f, 3f, 90f), 0f) // 72vw: full size
        assertEquals(90f / 97f, HomeModel.fitScale(4, 22f, 3f, 90f), 1e-6f)
        assertEquals(90f / 122f, HomeModel.fitScale(5, 22f, 3f, 90f), 1e-6f)
        assertEquals(1f, HomeModel.fitScale(8, 10f, 1.6f, 96f), 0f) // 6 apps + Apps + Setup = 91.2vw
        assertEquals(96f / 102.8f, HomeModel.fitScale(9, 10f, 1.6f, 96f), 1e-6f) // + Fix Home
    }

    @Test fun tier2ExcludesFavorites() {
        val r = HomeModel.build(listOf(app("a"), app("b")), cfg(listOf("a")), mapOf("a" to 9.0, "b" to 1.0), false)
        assertEquals(listOf("b", Tile.AllApps.toString(), Tile.Setup.toString()), pkgs(r.tier2))
    }

    @Test fun tier2TiesAlphabetical() {
        val apps = listOf(app("1", "banana"), app("2", "Apple"), app("3", "cherry"))
        assertEquals(listOf("2", "1", "3"), HomeModel.rankAll(apps, cfg(), emptyMap()).map { it.pkg })
    }

    @Test fun fixHomeInsertedWhenBroken() {
        val r = HomeModel.build(listOf(app("a")), cfg(), emptyMap(), true)
        assertEquals(listOf(Tile.App(app("a")), Tile.FixHome, Tile.AllApps, Tile.Setup), r.tier2)
    }

    private val hdmi = TvInput("tv/HW3", "HDMI 1")

    @Test fun inputFavoriteBecomesInputTile() {
        val r = HomeModel.build(listOf(app("a")), cfg(listOf("input:tv/HW3", "a")), emptyMap(), false, listOf(hdmi))
        assertEquals(listOf(Tile.Input(hdmi), Tile.App(app("a"))), r.favorites)
    }

    @Test fun missingInputFavoriteSkipped() {
        val r = HomeModel.build(listOf(app("a")), cfg(listOf("input:gone", "a")), emptyMap(), false, listOf(hdmi))
        assertEquals(listOf("a"), pkgs(r.favorites))
    }

    @Test fun inputsNeverInTier2() {
        val r = HomeModel.build(listOf(app("a")), cfg(), emptyMap(), false, listOf(hdmi))
        assertEquals(false, r.tier2.any { it is Tile.Input })
    }
}

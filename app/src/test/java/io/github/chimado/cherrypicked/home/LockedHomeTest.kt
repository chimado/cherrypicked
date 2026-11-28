package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockedHomeTest {
    private val apps = listOf("a", "b", "c", "d").map { AppEntry(it, it) }
    private val scores = mapOf("c" to 9.0, "d" to 8.0, "b" to 1.0)
    private fun row(c: Config) = HomeModel.build(apps, c, scores, false).tier2.mapNotNull { (it as? Tile.App)?.app?.pkg }

    @Test fun unlockedRowIsRanked() = assertEquals(listOf("c", "d", "b"), row(Config(favorites = listOf("a"))))

    @Test fun lockedRowKeepsItsOrder() =
        assertEquals(listOf("b", "d", "c"), row(Config(favorites = listOf("a"), pin = "1234", frozenRow = listOf("b", "d", "c"))))

    @Test fun lockedRowDropsMissingHiddenAndFavoriteApps() =
        assertEquals(listOf("b", "d"), row(Config(favorites = listOf("a"), hidden = setOf("c"), pin = "1234", frozenRow = listOf("zz", "a", "c", "b", "d"))))

    // A favorite removed while locked must not vanish from both rows.
    @Test fun removedFavoriteReturnsAtItsFrozenPlace() =
        assertEquals(listOf("a", "b", "c", "d"), row(Config(pin = "1234", frozenRow = listOf("a", "b", "c", "d"))))

    // An imported lock with no row, or apps installed since: by name, never by use.
    @Test fun appsOutsideTheFrozenRowFollowByName() =
        assertEquals(listOf("c", "a", "b", "d"), row(Config(pin = "1234", frozenRow = listOf("c"))))

    @Test fun frozenRowIsIgnoredWhenUnlocked() =
        assertEquals(listOf("c", "d", "b"), row(Config(favorites = listOf("a"), frozenRow = listOf("b"))))

    @Test fun tier2AppsIsWhatLockingSnapshots() =
        assertEquals(listOf("c", "d", "b"), HomeModel.tier2Apps(apps, Config(), scores, setOf("a")).map { it.pkg })

    @Test fun noSuggestionsWhileLocked() {
        assertEquals(Suggestion.MainApp(apps[2]), Suggester.next(apps, Config(), scores))
        assertNull(Suggester.next(apps, Config(pin = "1234"), scores))
    }

    @Test fun nicknameReplacesTheLabel() = assertEquals(
        listOf(TvInput("h1", "Console"), TvInput("h2", "HDMI 2")),
        listOf(TvInput("h1", "HDMI 1"), TvInput("h2", "HDMI 2")).named(Config(inputNames = mapOf("h1" to "Console"))))
}

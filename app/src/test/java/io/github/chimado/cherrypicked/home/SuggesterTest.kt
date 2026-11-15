package io.github.chimado.cherrypicked.home

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggesterTest {
    private val a = AppEntry("a", "A"); private val b = AppEntry("b", "B"); private val c = AppEntry("c", "C")
    private val apps = listOf(a, b, c)

    @Test fun nothingBelowThreshold() =
        assertNull(Suggester.next(apps, Config(), mapOf("a" to 2.9)))

    @Test fun mainAppFirst() =
        assertEquals(Suggestion.MainApp(b), Suggester.next(apps, Config(), mapOf("a" to 3.0, "b" to 5.0)))

    @Test fun declinedMainNotRepeated() {
        val cfg = Config(declined = setOf("main:b"))
        assertEquals(Suggestion.Favorite(b), Suggester.next(apps, cfg, mapOf("a" to 3.0, "b" to 5.0)))
    }

    @Test fun favoriteSkipsExisting() {
        val cfg = Config(mainApp = "z", favorites = listOf("b"))
        assertEquals(Suggestion.Favorite(a), Suggester.next(apps, cfg, mapOf("a" to 3.0, "b" to 5.0)))
    }

    @Test fun noFavoriteWhenFull() {
        val cfg = Config(mainApp = "z", favorites = listOf("1", "2", "3", "4", "5"))
        assertNull(Suggester.next(apps, cfg, mapOf("a" to 9.0)))
    }

    @Test fun hiddenNeverSuggested() {
        val cfg = Config(hidden = setOf("a", "b"))
        assertEquals(Suggestion.MainApp(c), Suggester.next(apps, cfg, mapOf("a" to 9.0, "b" to 8.0, "c" to 3.0)))
        assertNull(Suggester.next(apps, cfg.copy(mainApp = "z"), mapOf("a" to 9.0, "b" to 8.0)))
    }

    @Test fun excludedNeverSuggested() {
        val ex = setOf("b", "c")
        assertEquals(Suggestion.MainApp(a), Suggester.next(apps, Config(), mapOf("a" to 3.0, "b" to 9.0, "c" to 8.0), ex))
        assertNull(Suggester.next(apps, Config(mainApp = "z"), mapOf("b" to 9.0, "c" to 8.0), ex))
    }

    @Test fun declinedFavoriteNotRepeated() {
        val cfg = Config(mainApp = "z").decline(Suggestion.Favorite(b))
        assertEquals(Suggestion.Favorite(a), Suggester.next(apps, cfg, mapOf("a" to 3.0, "b" to 5.0)))
        assertNull(Suggester.next(apps, cfg, mapOf("b" to 5.0)))
    }

    @Test fun acceptMainSetsAutoOpen() {
        val r = Config().accept(Suggestion.MainApp(a))
        assertEquals("a", r.mainApp); assertTrue(r.autoOpen)
    }

    @Test fun acceptFavoriteAppends() =
        assertEquals(listOf("x", "a"), Config(favorites = listOf("x")).accept(Suggestion.Favorite(a)).favorites)

    @Test fun declineRecordsKey() {
        assertEquals(setOf("main:a"), Config().decline(Suggestion.MainApp(a)).declined)
        assertEquals(setOf("fav:a"), Config().decline(Suggestion.Favorite(a)).declined)
    }
}

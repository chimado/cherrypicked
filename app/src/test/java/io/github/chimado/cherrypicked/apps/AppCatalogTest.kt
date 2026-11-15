package io.github.chimado.cherrypicked.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** ActionRunner's OpenApp goes through AppCatalog.launch -> tryLaunch: a remapped app that is gone does nothing. */
class AppCatalogTest {
    @Test fun missingAppDoesNothing() = assertFalse(tryLaunch<String>(null) { fail("nothing should start") })

    @Test fun refusedLaunchIsFalse() = assertFalse(tryLaunch("x") { throw SecurityException() })

    @Test fun launchedIsTrue() {
        var started: String? = null
        assertTrue(tryLaunch("x") { started = it })
        assertEquals("x", started)
    }
}

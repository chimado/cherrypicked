package io.github.chimado.cherrypicked.a11y

import io.github.chimado.cherrypicked.store.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoOpenPolicyTest {
    private val on = Config(mainApp = "com.main", autoOpen = true)
    private val installed = setOf("com.main")
    private val tv = setOf("com.tcl.tv")

    private fun decide(t: Trigger, c: Config = on, inst: Set<String> = installed, fg: String? = null) =
        AutoOpenPolicy.decide(t, c, inst, fg, tv)

    @Test fun offDoesNothing() {
        assertNull(decide(Trigger.BOOT, on.copy(autoOpen = false)))
        assertNull(decide(Trigger.WAKE, on.copy(mainApp = null)))
    }
    @Test fun uninstalledMainAppDoesNothing() = assertNull(decide(Trigger.BOOT, inst = emptySet()))
    @Test fun bootOpensMain() = assertEquals("com.main", decide(Trigger.BOOT, fg = "com.tcl.tv"))
    @Test fun wakeOpensMain() = assertEquals("com.main", decide(Trigger.WAKE, fg = "com.other"))
    @Test fun wakeToHdmiDoesNothing() = assertNull(decide(Trigger.WAKE, fg = "com.tcl.tv"))
    @Test fun wakeWithUnknownForegroundDoesNothing() = assertNull(decide(Trigger.WAKE, fg = null))
    @Test fun bootIgnoresUnknownForeground() = assertEquals("com.main", decide(Trigger.BOOT, fg = null))
    @Test fun wakeAlreadyInMainDoesNothing() = assertNull(decide(Trigger.WAKE, fg = "com.main"))

    // The remote's power button makes the TV start home as it wakes, after the screen-on check has run.
    @Test fun homeSoonAfterWakeRechecks() = assertTrue(AutoOpenPolicy.recheckOnHome("me", "me", 400))
    @Test fun homeLongAfterWakeDoesNot() = assertFalse(AutoOpenPolicy.recheckOnHome("me", "me", 2500))
    @Test fun otherAppAfterWakeDoesNot() = assertFalse(AutoOpenPolicy.recheckOnHome("com.x", "me", 400))
}

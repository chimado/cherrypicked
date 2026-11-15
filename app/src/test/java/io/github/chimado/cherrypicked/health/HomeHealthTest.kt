package io.github.chimado.cherrypicked.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHealthTest {
    private val full = "io.github.chimado.cherrypicked/io.github.chimado.cherrypicked.a11y.CpAccessibilityService"

    @Test fun absent() {
        assertFalse(HomeHealth.listed(null))
        assertFalse(HomeHealth.listed(""))
        assertFalse(HomeHealth.listed("flar2.homebutton/a.i:com.spocky.projengmenu/.services.ProjectivyAccessibilityService"))
        assertFalse(HomeHealth.listed("io.github.chimado.cherrypicked/.a11y.CpAccessibilityServiceX"))
    }

    @Test fun shortOrFullAnywhereInList() {
        assertTrue(HomeHealth.listed(HomeHealth.COMPONENT))
        assertTrue(HomeHealth.listed(full))
        assertTrue(HomeHealth.listed("flar2.homebutton/a.i:$full"))
        assertTrue(HomeHealth.listed("${HomeHealth.COMPONENT}:flar2.homebutton/a.i"))
        assertTrue(HomeHealth.listed("a/.B:${HomeHealth.COMPONENT}:c/.D"))
    }
}

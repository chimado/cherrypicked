package io.github.chimado.cherrypicked.a11y

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeyPolicyTest {
    @Test fun onlyHome() {
        assertTrue(HomeKeyPolicy.intercept(3))
        for (k in listOf(4, 23, 82)) assertFalse(HomeKeyPolicy.intercept(k))
    }
}

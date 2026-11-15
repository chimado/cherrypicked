package io.github.chimado.cherrypicked.panel

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPanelTest {
    @Test fun sleepLabel() {
        assertEquals("Sleep timer: Off", sleepLabel(null, 1_000))
        assertEquals("Sleep timer: 15 min left", sleepLabel(1_000 + 15 * 60_000L, 1_000))
        assertEquals("Sleep timer: 15 min left", sleepLabel(1_000 + 14 * 60_000L + 1, 1_000)) // rounds up
        assertEquals("Sleep timer: 1 min left", sleepLabel(1_500, 1_000))
        assertEquals("Sleep timer: 0 min left", sleepLabel(500, 1_000))
    }
}

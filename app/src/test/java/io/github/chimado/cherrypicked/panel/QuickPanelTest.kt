package io.github.chimado.cherrypicked.panel

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPanelTest {
    @Test fun sleepLabel() {
        assertEquals("Sleep timer: Off", sleepLabel(null, 1_000))
        assertEquals("Sleep timer: 15 min left", sleepLabel(1_000 + 15 * 60_000L, 1_000))
        assertEquals("Sleep timer: 15 min left", sleepLabel(1_000 + 14 * 60_000L + 1, 1_000)) // rounds up
        assertEquals("Sleep timer: 1 min left", sleepLabel(1_500, 1_000))
        assertEquals("Sleep timer: Off", sleepLabel(500, 1_000)) // expired, not yet fired
        assertEquals("Sleep timer: Off", sleepLabel(1_000, 1_000))
    }

    private val home = KeyEvent.KEYCODE_HOME
    private val back = KeyEvent.KEYCODE_BACK
    private val down = KeyEvent.KEYCODE_DPAD_DOWN

    @Test fun closedGoesToEngine() {
        assertEquals(PanelRoute.ENGINE, panelRoute(false, 200, 100, down, true))
        assertEquals(PanelRoute.ENGINE, panelRoute(false, 200, 100, back, false))
    }

    @Test fun openingPressStaysWithEngine() {
        assertEquals(PanelRoute.ENGINE, panelRoute(true, 50, 100, home, true)) // repeat after open
        assertEquals(PanelRoute.ENGINE, panelRoute(true, 50, 100, home, false)) // its UP
    }

    @Test fun backAndHomeUpClose() {
        assertEquals(PanelRoute.CLOSE, panelRoute(true, 100, 100, back, false))
        assertEquals(PanelRoute.CLOSE, panelRoute(true, 300, 100, home, false))
        assertEquals(PanelRoute.PANEL, panelRoute(true, 300, 100, back, true)) // the DOWN is just consumed
        assertEquals(PanelRoute.PANEL, panelRoute(true, 300, 100, home, true))
    }

    @Test fun otherKeysGoToPanel() {
        assertEquals(PanelRoute.PANEL, panelRoute(true, 300, 100, down, true))
        assertEquals(PanelRoute.PANEL, panelRoute(true, 300, 100, KeyEvent.KEYCODE_DPAD_CENTER, false))
    }

    @Test fun nonPanelKeysGoToEngineWhileOpen() { // volume, mute, media, channel keep working
        for (k in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MENU))
            for (d in listOf(true, false)) assertEquals("$k $d", PanelRoute.ENGINE, panelRoute(true, 300, 100, k, d))
    }

    @Test fun panelKeysStayWithPanel() {
        for (k in listOf(19, 20, 21, 22, 23, 66, 160)) assertEquals("$k", PanelRoute.PANEL, panelRoute(true, 300, 100, k, true))
    }
}

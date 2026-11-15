package io.github.chimado.cherrypicked.ui

import io.github.chimado.cherrypicked.keys.Action
import io.github.chimado.cherrypicked.keys.encode
import io.github.chimado.cherrypicked.ui.OverlayProtocol.Msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayProtocolTest {
    private fun roundTrip(m: Msg) = assertEquals(m, OverlayProtocol.decode(OverlayProtocol.encode(m)))

    @Test fun everyMessageRoundTrips() = listOf(
        Msg.Notice("Sleep in 30 min"),
        Msg.Notice(""),
        Msg.Open(1_763_200_000_000L),
        Msg.Open(null),
        Msg.Close,
        Msg.Key(20, down = true, canceled = false),
        Msg.Key(23, down = false, canceled = true),
        Msg.Key(4, down = false, canceled = false),
        Msg.Sleep(1_763_200_000_000L),
        Msg.Sleep(null),
        Msg.Closed(3),
        Msg.PanelFailed,
        Msg.Run(Action.SleepTimer),
        Msg.Idle(7),
        Msg.Dismiss,
        Msg.Failed("Sleep timer off"),
    ).forEach(::roundTrip)

    @Test fun runCarriesEncodedActions() = listOf(
        Action.OpenApp("org.moonfin.androidtv"), Action.OpenInput("com.tcl.tvinput/.HdmiInput/HW5"),
        Action.Home, Action.Back, Action.QuickPanel, Action.SleepTimer, Action.ScreenOff, Action.Nothing,
    ).forEach {
        val w = OverlayProtocol.encode(Msg.Run(it))
        assertEquals(OverlayProtocol.MSG_RUN, w.what)
        assertEquals(it.encode(), w.str)
        roundTrip(Msg.Run(it))
    }

    @Test fun unknownWhatIsNull() = assertNull(OverlayProtocol.decode(OverlayProtocol.Wire(999, 0, 0, null)))

    @Test fun badRunActionIsNull() = assertNull(OverlayProtocol.decode(OverlayProtocol.Wire(OverlayProtocol.MSG_RUN, 0, 0, "bogus")))
}

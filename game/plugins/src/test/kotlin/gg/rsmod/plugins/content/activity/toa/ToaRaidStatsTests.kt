package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.activity.toa.ui.ToaSummaryUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToaRaidStatsTests {
    @Test fun `room times run from the first start to the clear and fill the summary tabs`() {
        val s = ToaRaidStats()
        s.started(ToaRoomKey(ToaPath.HET, false), 100)
        s.cleared(ToaRoomKey(ToaPath.HET, false), 200)
        // A retried room keeps its first start (ONZEKER in ToaRaidStats): 300 -> 650 = 350 cycles.
        s.started(ToaRoomKey(ToaPath.HET, true), 300)
        s.started(ToaRoomKey(ToaPath.HET, true), 500)
        s.cleared(ToaRoomKey(ToaPath.HET, true), 650)
        s.started(ToaRoomKey(ToaPath.CRONDIS, false), 700)
        s.cleared(ToaRoomKey(ToaPath.CRONDIS, false), 760)
        val t = s.times(1000)
        assertEquals(100, t.het)
        assertEquals(350, t.akkha)
        assertEquals(60, t.crondis)
        assertEquals(510, t.challengeTotal)
        assertEquals(1000, t.overall)
        assertNull(t.wardens)
        assertNull(t.kephri)
    }

    @Test fun `summary time text is minutes and seconds of game ticks`() {
        assertEquals("01:00", ToaSummaryUi.time(100))
        assertEquals("-", ToaSummaryUi.time(null))
    }

    @Test fun `player table rows are pre-made per column after the 482 port extras`() {
        assertEquals(107, ToaSummaryUi.rowComponent(0, 0))
        assertEquals(107 + 8 * 4 + 7, ToaSummaryUi.rowComponent(4, 7))
    }
}

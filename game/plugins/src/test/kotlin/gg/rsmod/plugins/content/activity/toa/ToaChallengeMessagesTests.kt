package gg.rsmod.plugins.content.activity.toa

import kotlin.test.*

class ToaChallengeMessagesTests {
    private val splits = Regex("Challenge complete: (?:Path of )?([A-Za-z-]+).*Total:.*?([0-9]+:[.0-9]+).*")
    private val wardens = Regex("Challenge complete: The (Wardens).*?completion time:.*?([0-9]+:[.0-9]+).*")

    @Test fun `start message is the LlemonDuck beam-timer trigger`() {
        assertEquals("Challenge started: Path of Het.", ToaChallengeMessages.started(ToaRoomKey(ToaPath.HET, false)))
        assertEquals("Challenge started: Ba-Ba.", ToaChallengeMessages.started(ToaRoomKey(ToaPath.APMEKEN, true)))
    }

    @Test fun `complete messages match the RuneLite splits patterns`() {
        val het = ToaChallengeMessages.complete(ToaRoomKey(ToaPath.HET, false), 100, 250, 150)
        assertTrue(het.startsWith("Challenge complete: Path of Het. Duration: 1:00.00."), het)
        val m = splits.matchEntire(het)!!
        assertEquals("Het", m.groupValues[1]); assertEquals("2:30.00", m.groupValues[2].trimEnd('.'))
        assertEquals("Ba-Ba", splits.matchEntire(ToaChallengeMessages.complete(ToaRoomKey(ToaPath.APMEKEN, true), 5, 5, 0))!!.groupValues[1])
        val w = ToaChallengeMessages.complete(ToaRoomKey(null, true), 300, 3000, 300)
        assertEquals("30:00.00", wardens.matchEntire(w)!!.groupValues[2].trimEnd('.'))
        assertTrue("Tombs of Amascut: Expert Mode total completion time" in w)
    }

    @Test fun `completion count names the mode like the OSRS kill count`() {
        assertEquals("Your completed Tombs of Amascut count is: <col=ff0000>1,234</col>.", ToaChallengeMessages.count(150, 1234))
        assertEquals("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>1</col>.", ToaChallengeMessages.count(0, 1))
        assertEquals("0:00.60", ToaChallengeMessages.time(1))
    }
}

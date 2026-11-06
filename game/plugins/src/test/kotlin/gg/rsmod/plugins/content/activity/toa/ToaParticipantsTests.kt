package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import kotlin.test.*

class ToaParticipantsTests {
    @Test fun `individual death spectates and only all entrants lost wipes`() {
        val progress = ToaRaidProgress(ToaRaidSettings())
        val run = progress.challenge(ToaPath.HET, 0)!!
        val party = ToaParticipants()
        party.begin(run)
        val a = PlayerUID(1); val b = PlayerUID(2)
        assertFalse(party.wiped(run))
        assertTrue(party.enter(run, a)); assertTrue(party.enter(run, b))
        assertTrue(party.lose(run, a)); assertFalse(party.lose(run, a))
        assertFalse(party.enter(run, a)) // no resurrection by double-clicking a door
        assertFalse(party.wiped(run))
        assertTrue(party.lose(run, b)); assertTrue(party.wiped(run))
        run.wipe(1); progress.cycle(2)
        val retry = progress.challenge(ToaPath.HET, 3)!!
        party.begin(retry)
        assertFalse(party.lose(run, a)); assertFalse(party.wiped(retry))
        assertTrue(party.enter(retry, a)); assertTrue(party.alive(retry, a))
    }
}

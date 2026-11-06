package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import kotlin.test.*

class ToaPartyStateTests {
    private fun uid(n: Int) = PlayerUID(n)

    @Test fun `one to eight members and ninth rejected`() {
        val party = ToaParty(1, uid(1))
        for (n in 2..8) assertTrue(party.join(uid(n)))
        assertFalse(party.join(uid(9)))
        assertEquals(8, party.members.size)
    }

    @Test fun `duplicate join leave kick and leader succession`() {
        val party = ToaParty(1, uid(1))
        assertFalse(party.join(uid(1)))
        assertTrue(party.join(uid(2)))
        assertTrue(party.join(uid(3)))
        assertFalse(party.kick(uid(2), uid(3)))
        assertTrue(party.leave(uid(1)))
        assertEquals(uid(2), party.leader)
        assertTrue(party.kick(uid(2), uid(3)))
        assertFalse(party.kick(uid(2), uid(2)))
        assertTrue(party.transfer(uid(2), uid(2)))
    }

    @Test fun `only eligible leader starts once and roster cannot change`() {
        val party = ToaParty(1, uid(1))
        party.join(uid(2))
        assertNull(party.start(uid(2)) { true })
        assertNull(party.start(uid(1)) { it != uid(1) }) // leader disconnected during entry
        assertFalse(party.started)
        assertEquals(listOf(uid(1), uid(2)), party.start(uid(1)) { true })
        assertNull(party.start(uid(1)) { true })
        assertFalse(party.join(uid(3)))
        assertFalse(party.leave(uid(1)))
        assertFailsWith<IllegalStateException> { party.settings = ToaRaidSettings(150) }
    }

    @Test fun `failed allocation permits retry`() {
        val party = ToaParty(1, uid(1))
        party.start(uid(1)) { true }
        party.allocationFailed()
        assertNotNull(party.start(uid(1)) { true })
    }
}

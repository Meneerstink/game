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

    @Test fun `N604 apply accept reject kick blocks until unblock`() {
        val party = ToaParty(1, uid(1))
        assertFalse(party.apply(uid(1)))                 // the leader is already in
        assertTrue(party.apply(uid(2)))
        assertFalse(party.apply(uid(2)))                 // one application
        assertFalse(party.accept(uid(3), uid(2)))        // only the leader accepts
        assertTrue(party.accept(uid(1), uid(2)))
        assertEquals(listOf(uid(1), uid(2)), party.members)
        assertTrue(party.applicants.isEmpty())
        assertTrue(party.apply(uid(3)))
        assertTrue(party.reject(uid(1), uid(3)))
        assertTrue(party.apply(uid(3)))
        assertTrue(party.withdraw(uid(3)))
        assertTrue(party.kick(uid(1), uid(2)))
        assertFalse(party.apply(uid(2)))                 // kicked = blocked
        assertFalse(party.unblock(uid(2)))               // not the leader
        assertTrue(party.unblock(uid(1)))
        assertTrue(party.apply(uid(2)))
        for (n in 4..10) party.join(uid(n))
        assertFalse(party.apply(uid(11)))                // full party
        assertFalse(party.accept(uid(1), uid(2)))        // no room any more
    }

    @Test fun `failed allocation permits retry`() {
        val party = ToaParty(1, uid(1))
        party.start(uid(1)) { true }
        party.allocationFailed()
        assertNotNull(party.start(uid(1)) { true })
    }
}

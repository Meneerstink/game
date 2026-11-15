package gg.rsmod.plugins.content.activity.cox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CoX parties (besluit 5a groep 1-8; tsps chambers-of-xeric.md 'Parties' + 'Leaving'). */
class CoxPartyTests {
    @Test
    fun `a party holds at most eight raiders and nobody twice`() {
        CoxParties.clearForTests()
        val party = CoxParties.create("Lead")!!
        (1..7).forEach { assertTrue(party.join("m$it")) }
        assertFalse(party.join("m8"))
        assertFalse(party.join("lead"))
        assertEquals(8, party.members.size)
    }

    @Test
    fun `one party per player`() {
        CoxParties.clearForTests()
        CoxParties.create("Lead")!!.join("Other")
        assertNull(CoxParties.create("other"))
        assertNull(CoxParties.create("LEAD"))
    }

    @Test
    fun `a leaving leader hands the party on and the last one out disbands it`() {
        CoxParties.clearForTests()
        val party = CoxParties.create("Lead")!!
        party.join("Second")
        CoxParties.leave("lead")
        assertEquals("second", party.leader)
        CoxParties.leave("second")
        assertNull(CoxParties.byId(party.id))
    }

    @Test
    fun `only the leader kicks and never himself`() {
        CoxParties.clearForTests()
        val party = CoxParties.create("Lead")!!
        party.join("A")
        assertFalse(party.kick("a", "lead"))
        assertFalse(party.kick("lead", "lead"))
        assertTrue(party.kick("lead", "a"))
        assertFalse(party.isMember("a"))
    }

    @Test
    fun `the board lists advertised parties only while the advert runs`() {
        CoxParties.clearForTests()
        val party = CoxParties.create("Lead")!!
        assertTrue(CoxParties.listed(0).isEmpty())
        party.advertisedUntil = 100
        assertEquals(listOf(party), CoxParties.listed(50))
        assertTrue(CoxParties.listed(100).isEmpty())
    }
}

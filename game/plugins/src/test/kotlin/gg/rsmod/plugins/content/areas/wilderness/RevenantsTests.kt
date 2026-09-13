package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** RCV-010 D6: revenant roster mapping and per-revenant heal pools. */
class RevenantsTests {
    private fun npc(id: Int): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.id } returns id
        every { npc.attr } returns AttributeMap()
        return npc
    }

    @Test
    fun `every revenant id maps to its own row, not the imp fallback`() {
        val wrong = Revenants.ids.filter { id -> Revenant.forId(id).id != id }
        assertEquals(emptyList(), wrong, "ids falling back to REVENANT_IMP")
        assertEquals(Revenants.ids.toSet(), Revenant.values().map { it.id }.toSet(), "combat binding roster vs Revenant table")
    }

    @Test
    fun `revenants of the same type keep separate heal pools for the whole roster`() {
        Revenants.ids.forEach { id ->
            val a = npc(id)
            val b = npc(id)
            Revenants.resetHeals(a)
            Revenants.resetHeals(b)
            repeat(Revenants.HEALS_PER_LIFE + 2) { Revenants.consumeHeal(a) }
            assertEquals(0, Revenants.healsLeft(a), "$id: a exhausted and never negative")
            assertEquals(Revenants.HEALS_PER_LIFE, Revenants.healsLeft(b), "$id: b untouched by a's heals")
            Revenants.resetHeals(a)
            assertEquals(Revenants.HEALS_PER_LIFE, Revenants.healsLeft(a), "$id: respawn refills a")
            Revenants.consumeHeal(b)
            Revenants.resetHeals(a)
            assertEquals(Revenants.HEALS_PER_LIFE - 1, Revenants.healsLeft(b), "$id: a's respawn does not refill b")
        }
    }
}

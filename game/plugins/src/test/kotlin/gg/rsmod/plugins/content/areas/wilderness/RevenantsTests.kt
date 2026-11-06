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

    /**
     * Dagrun 2026-11-06 (test client: a charged Bracelet of ethereum worn, the OSRS Revenant hellhound attacked anyway): npc_aggro's
     * global spawn hook sets aggroCheck on the same spawn, so the bracelet check must be put on a tick later and wrap that check.
     */
    @Test
    fun `the charged-bracelet tolerance of the OSRS revenants is set after the global aggression hook and wraps it`() {
        val src = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/bosses/revenant_cave_revenants.plugin.kts").readText()
        val spawn = src.substringAfter("on_npc_spawn(rev.id) {").substringBefore("on_npc_combat")
        kotlin.test.assertTrue("wait(1)" in spawn, "bracelet check one tick after the global hook")
        kotlin.test.assertTrue("!RevenantGear.wearsCharged(p) && (base == null || base(n, p))" in spawn, "wraps the global check")
        val global = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/aggro/npc_aggro.plugin.kts").readText()
        kotlin.test.assertTrue("on_global_npc_spawn" in global && "npc.aggroCheck =" in global, "the global hook this ordering guards against")
    }
}

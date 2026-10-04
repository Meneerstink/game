package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Owner 2026-10-04 ("all our gear doesn't degrade correctly"): Barrows, Moon armour and the Amulet of the damned degrade by the
 * OSRS points of their Wiki pages (audit-2026-10-04-degradation.md) - 1,000 / 3,000 / 1,000 points, one per combat entry and one
 * per 90 combat ticks - and repair costs the spent points.
 */
class CombatPointsTests {
    private fun spendN(item: Item, n: Int): Item? {
        var current: Item? = item
        repeat(n) { current = current?.let { CombatPoints.spend(it)?.item } }
        return current
    }

    @Test
    fun `Barrows - the first point makes the 100 piece, then 250 points a stage, 0 at the end`() {
        val helm = Item(Items.DHAROKS_HELM, 1)
        val first = CombatPoints.spend(helm)!!.item!!
        assertEquals(Items.DHAROKS_HELM_100, first.id)
        assertEquals(999, first.attr[ItemAttribute.DEGRADE_POINTS])
        assertEquals(Items.DHAROKS_HELM_100, spendN(helm, 250 - 1)!!.id) // 1000..751 is the 100 quarter
        assertEquals(Items.DHAROKS_HELM_100 + 1, spendN(helm, 250)!!.id) // 75
        assertEquals(Items.DHAROKS_HELM_100 + 3, spendN(helm, 999)!!.id) // 25
        assertEquals(Items.DHAROKS_HELM_100 + 4, spendN(helm, 1000)!!.id) // 0
        // 15 hours of combat = 90,000 ticks = 1,000 points x 90 ticks.
        assertEquals(15 * 60 * 100, 1000 * CombatPoints.TICKS_PER_POINT)
    }

    @Test
    fun `Barrows repair costs the spent points at 60 90 80 100 coins`() {
        val helm = spendN(Item(Items.DHAROKS_HELM, 1), 1000)!!
        assertEquals(60_000, CombatPoints.repairCost(helm))
        val axe = spendN(Item(Items.DHAROKS_GREATAXE, 1), 10)!!
        assertEquals(1_000, CombatPoints.repairCost(axe))
        assertEquals(0, CombatPoints.repairCost(Item(Items.DHAROKS_GREATAXE, 1)))
    }

    @Test
    fun `an item saved under the old tick model keeps its place`() {
        val old = Item(Items.DHAROKS_HELM_100 + 1, 1) // 75
        old.attr[ItemAttribute.CHARGES] = 22_500 / 2
        val chain = CombatPoints.chainFor(old.id)!!
        assertEquals(500 + 125, CombatPoints.pointsOf(chain, old))
    }

    @Test
    fun `Moon armour - 3,000 points, broken at 0, repair 500 coins a point`() {
        val helm = Item(Items.ECLIPSE_MOON_HELM, 1)
        assertEquals(Items.ECLIPSE_MOON_HELM_DEGRADED, CombatPoints.spend(helm)!!.item!!.id)
        val broken = spendN(helm, 3000)!!
        assertEquals(Items.ECLIPSE_MOON_HELM_BROKEN, broken.id)
        assertEquals(1_500_000, CombatPoints.repairCost(broken))
        assertNull(CombatPoints.spend(broken))
    }

    @Test
    fun `Amulet of the damned turns to dust after 1,000 points`() {
        val amulet = Item(Items.AMULET_OF_THE_DAMNED_FULL, 1)
        assertEquals(Items.AMULET_OF_THE_DAMNED, spendN(amulet, 999)!!.id)
        assertNull(spendN(amulet, 1000))
    }

    @Test
    fun `PvP death - Barrows and Moon drop broken, the used amulet is dust, crystal gives its seed`() {
        val death = gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
        listOf(Items.DHAROKS_HELM, Items.DHAROKS_HELM_100, Items.ECLIPSE_MOON_HELM, Items.ECLIPSE_MOON_HELM_DEGRADED, Items.AMULET_OF_THE_DAMNED).forEach {
            kotlin.test.assertTrue(death.converts(it), "$it converts on a PvP death")
        }
        kotlin.test.assertFalse(death.pointsDeath(Items.AMULET_OF_THE_DAMNED_FULL), "the full amulet drops as it is")
        assertEquals(Items.CRYSTAL_ARMOUR_SEED, death.crystalSeedOnDeath(Items.CRYSTAL_HELM))
        assertEquals(Items.CRYSTAL_SEED, death.crystalSeedOnDeath(Items.CRYSTAL_BOW_OSRS))
    }
}

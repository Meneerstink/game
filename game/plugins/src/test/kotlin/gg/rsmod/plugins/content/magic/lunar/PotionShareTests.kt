package gg.rsmod.plugins.content.magic.lunar

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.potion.Potion
import gg.rsmod.plugins.content.items.potion.PotionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stat Restore Pot Share / Boost Potion Share rules ([PotionShare]; RuneScape Wiki 2011 pages, OSRS Wiki for OSRS imports):
 * one dose per recipient from the vial in hand, the lists of each spell, no barbarian mixes.
 */
class PotionShareTests {
    @Test
    fun `a full vial has four doses and each recipient takes one`() {
        val four = Potion.values().first { it.item == Items.SUPER_STRENGTH_4 }
        assertEquals(4, PotionShare.doses(four))
        assertEquals(Items.SUPER_STRENGTH_3, PotionShare.remainder(four, 1))
        assertEquals(Items.SUPER_STRENGTH_1, PotionShare.remainder(four, 3))
        assertEquals(Items.VIAL, PotionShare.remainder(four, 4))
        val one = Potion.values().first { it.item == Items.SUPER_STRENGTH_1 }
        assertEquals(1, PotionShare.doses(one))
        assertEquals(Items.VIAL, PotionShare.remainder(one, 1))
    }

    @Test
    fun `each spell takes only its own potions`() {
        assertNotNull(PotionShare.shareable(Items.SUPER_STRENGTH_4, PotionShare.BOOST))
        assertNull(PotionShare.shareable(Items.SUPER_STRENGTH_4, PotionShare.STAT_RESTORE))
        assertNotNull(PotionShare.shareable(Items.PRAYER_POTION_4, PotionShare.STAT_RESTORE))
        assertNull(PotionShare.shareable(Items.PRAYER_POTION_4, PotionShare.BOOST))
        // RuneScape Wiki Boost Potion Share: not Saradomin / Zamorak brews, Combat potions, extremes.
        listOf(PotionType.SARADOMIN_BREW, PotionType.ZAMORAK_BREW, PotionType.COMBAT, PotionType.EXTREME_MAGIC).forEach {
            assertTrue(it !in PotionShare.BOOST && it !in PotionShare.STAT_RESTORE, "$it must not be shared")
        }
        // No potion is in both lists.
        assertTrue(PotionShare.BOOST.intersect(PotionShare.STAT_RESTORE).isEmpty())
    }

    @Test
    fun `barbarian mixes are never shared`() {
        Potion.values().filter { it.mixHeal > 0 }.forEach { mix ->
            assertNull(PotionShare.shareable(mix.item, PotionShare.BOOST), "${mix.name}")
            assertNull(PotionShare.shareable(mix.item, PotionShare.STAT_RESTORE), "${mix.name}")
        }
    }
}

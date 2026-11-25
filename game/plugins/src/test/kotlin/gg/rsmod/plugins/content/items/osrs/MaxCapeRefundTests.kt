package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.SkillcapeFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N410 deel 2 (owner 2026-11-25): Max buys the Max cape + hood back for 79,200 coins (80% of 99,000), dupe-safe. */
class MaxCapeRefundTests {
    @Test
    fun `the refund is eighty percent of the price`() {
        assertEquals(99_000, MaxCapeRefund.PRICE)
        assertEquals(79_200, MaxCapeRefund.REFUND)
    }

    @Test
    fun `cape and hood are taken once and paid once`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(MaxCapes.MAX_CAPE)
        f.inventory[1] = Item(MaxCapes.MAX_HOOD)
        assertTrue(MaxCapeRefund.sellBack(f.player))
        assertEquals(0, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
        assertEquals(0, f.inventory.getItemCount(MaxCapes.MAX_HOOD))
        assertEquals(79_200, f.inventory.getItemCount(Items.COINS_995))
        assertFalse(MaxCapeRefund.sellBack(f.player), "a second click pays nothing")
        assertEquals(79_200, f.inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `without both pieces in the backpack nothing changes`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(MaxCapes.MAX_CAPE)
        f.equipment[0] = Item(MaxCapes.MAX_HOOD)
        assertFalse(MaxCapeRefund.sellBack(f.player), "worn hood does not count")
        assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
        assertEquals(0, f.inventory.getItemCount(Items.COINS_995))
        val g = SkillcapeFixture()
        g.inventory[0] = Item(MaxCapes.MAX_HOOD)
        assertFalse(MaxCapeRefund.sellBack(g.player), "hood alone")
        assertEquals(1, g.inventory.getItemCount(MaxCapes.MAX_HOOD))
    }

    @Test
    fun `coins that do not fit give cape and hood back`() {
        val f = SkillcapeFixture()
        f.inventory[0] = Item(MaxCapes.MAX_CAPE)
        f.inventory[1] = Item(MaxCapes.MAX_HOOD)
        f.inventory[2] = Item(Items.COINS_995, Int.MAX_VALUE - 1)
        assertFalse(MaxCapeRefund.sellBack(f.player))
        assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_CAPE))
        assertEquals(1, f.inventory.getItemCount(MaxCapes.MAX_HOOD))
        assertEquals(Int.MAX_VALUE - 1, f.inventory.getItemCount(Items.COINS_995))
    }
}

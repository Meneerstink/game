package gg.rsmod.plugins.content.mechanics.trading.impl

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit E-04 (per-exemplar trade moves, attribute-aware snapshot) and E-11 (Long trade value). */
class TradeItemsTests {
    private val definitions: DefinitionSet =
        mockk<DefinitionSet>(relaxed = true).also { defs ->
            every { defs.get(ItemDef::class.java, RING) } returns ItemDef(RING).apply { name = "Ring"; cost = 75_000 }
            every { defs.get(ItemDef::class.java, COINS) } returns ItemDef(COINS).apply { name = "Coins"; stacks = true; cost = 1 }
            every { defs.get(ItemDef::class.java, GEM) } returns ItemDef(GEM).apply { name = "Gem"; stacks = true; cost = 1_000_000 }
        }

    private fun inventory() = ItemContainer(definitions, 28, ContainerStackType.NORMAL)

    /** One full ring and four nearly empty ones, as in the audit repro. */
    private fun rings(): ItemContainer =
        inventory().also { inv ->
            inv[0] = Item(RING).putAttr(ItemAttribute.CHARGES, 1000)
            (1..4).forEach { inv[it] = Item(RING).putAttr(ItemAttribute.CHARGES, 1) }
        }

    @Test
    fun `offer-all then remove-all keeps every ring's own charges`() {
        val inv = rings()
        val before = TradeItems.totals(inv)
        val offer = inventory()

        // Offer-All clicked on the full ring, then Remove-All clicked on it again.
        TradeItems.moveEach(from = inv, to = offer, itemId = RING, amount = 5, startSlot = 0)
        assertEquals(5, offer.getItemCount(RING))
        assertEquals(0, inv.getItemCount(RING))
        assertEquals(before, TradeItems.totals(offer), "offered rings keep their own charges")

        val emptied = TradeItems.moveEach(from = offer, to = inv, itemId = RING, amount = 5, startSlot = 0)
        assertEquals(5, emptied.size)
        assertEquals(before, TradeItems.totals(inv), "attribute multiset unchanged after offer-all / remove-all")
        assertEquals(1, inv.rawItems.count { it?.getAttr(ItemAttribute.CHARGES) == 1000 }, "still exactly one full ring")
    }

    @Test
    fun `offering part of the rings moves the clicked one first, each with its own charges`() {
        val inv = rings()
        val offer = inventory()
        TradeItems.moveEach(from = inv, to = offer, itemId = RING, amount = 2, startSlot = 3)
        assertEquals(listOf(1, 1), offer.rawItems.filterNotNull().map { it.getAttr(ItemAttribute.CHARGES) })
        assertEquals(1000, inv[0]?.getAttr(ItemAttribute.CHARGES))
        assertEquals(3, inv.getItemCount(RING))
    }

    @Test
    fun `the snapshot check compares attributes, not only ids and amounts`() {
        val snapshot = rings()
        val offer = inventory()
        val real = rings()
        assertTrue(TradeItems.matchesSnapshot(real, snapshot, offer))

        // Same ids and amounts, but a charge changed (or a different exemplar) - the trade must not complete.
        real[1] = Item(RING).putAttr(ItemAttribute.CHARGES, 1000)
        assertFalse(TradeItems.matchesSnapshot(real, snapshot, offer))
    }

    @Test
    fun `stackables still move by amount`() {
        val inv = inventory().also { it.add(COINS, 1_000) }
        val offer = inventory()
        TradeItems.moveEach(from = inv, to = offer, itemId = COINS, amount = 400, startSlot = 0)
        assertEquals(600, inv.getItemCount(COINS))
        assertEquals(400, offer.getItemCount(COINS))
    }

    @Test
    fun `trade value is summed in Long and shown clamped instead of wrapped`() {
        val offer = inventory().also { it.add(GEM, 5_000) }
        assertEquals(5_000_000_000L, TradeItems.value(definitions, offer))
        assertEquals(Int.MAX_VALUE, TradeItems.displayValue(TradeItems.value(definitions, offer)))
        assertEquals(0, TradeItems.displayValue(-5L))
        assertEquals(1_000_000, TradeItems.displayValue(TradeItems.value(definitions, inventory().also { it.add(GEM, 1) })))
    }

    companion object {
        const val RING = 20655
        const val COINS = 995
        const val GEM = 1631
    }
}

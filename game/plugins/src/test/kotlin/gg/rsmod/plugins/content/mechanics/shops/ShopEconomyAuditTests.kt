package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopCurrency
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit E-03 (shop sell price >= high alchemy), E-05 (buy < sell), E-09 (no coins destroyed), E-10 (point shop sell). */
class ShopEconomyAuditTests {
    private val bodyDef = ItemDef(BODY).apply { name = "Studded body"; cost = 850; tradeable = true }

    private val definitions: DefinitionSet =
        mockk<DefinitionSet>(relaxed = true).also { defs ->
            every { defs.get(ItemDef::class.java, COINS) } returns ItemDef(COINS).apply { name = "Coins"; stacks = true; cost = 1 }
            every { defs.get(ItemDef::class.java, TICKETS) } returns ItemDef(TICKETS).apply { name = "Tickets"; stacks = true; cost = 1 }
            every { defs.get(ItemDef::class.java, BODY) } returns bodyDef
            every { defs.get(ItemDef::class.java, JUNK) } returns ItemDef(JUNK).apply { name = "Junk"; cost = 1 }
            every { defs.getNullable(ItemDef::class.java, BODY) } returns bodyDef
        }

    private val world: World = mockk<World>(relaxed = true).also { every { it.definitions } returns definitions }

    private fun player(inventory: ItemContainer = ItemContainer(definitions, 28, ContainerStackType.NORMAL)): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        return player
    }

    private fun shop(
        currency: ShopCurrency,
        item: ShopItem,
        policy: PurchasePolicy = PurchasePolicy.BUY_STOCK,
    ): Shop {
        val items = arrayOfNulls<ShopItem?>(4)
        items[0] = item
        return Shop("Test shop", StockType.NORMAL, policy, currency, items, arrayOfNulls(4), containsSamples = false)
    }

    @Test
    fun `a shop never pays more for an item than it sells it for`() {
        // Ranging Guild style: 51 tickets to buy, the cache-based buy-back would be 0.4 * 850 = 340 tickets.
        val tickets = ItemCurrency(TICKETS, "ticket", "tickets")
        val body = ShopItem(BODY, amount = 10, sellPrice = 51)
        assertEquals(340, tickets.getBuyPrice(0, world, BODY))
        assertEquals(50, tickets.buyPriceOf(world, body, BODY, 0))
        assertTrue(tickets.buyPriceOf(world, body, BODY, 0) < tickets.sellPriceOf(world, body))

        // An explicit buy price above the sell price is clamped too.
        val coins = ItemCurrency(COINS, "coin", "coins")
        val generous = ShopItem(BODY, amount = 10, sellPrice = 900, buyPrice = 5_000)
        assertEquals(899, coins.buyPriceOf(world, generous, BODY, 0))
    }

    @Test
    fun `selling to a shop pays the clamped price`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL).also { it.add(BODY, 1) }
        val p = player(inventory)
        val tickets = ItemCurrency(TICKETS, "ticket", "tickets")
        val s = shop(tickets, ShopItem(BODY, amount = 10, sellPrice = 51))
        tickets.buyFromPlayer(p, s, slot = 0, amt = 1)
        assertEquals(50, inventory.getItemCount(TICKETS))
        assertEquals(0, inventory.getItemCount(BODY))
    }

    @Test
    fun `a coin shop never sells below the high alchemy value`() {
        val coins = ItemCurrency(COINS, "coin", "coins")
        assertEquals(510, coins.sellPriceOf(world, ShopItem(BODY, amount = 1, sellPrice = 100)), "ceil(0.6 * 850)")
        assertEquals(900, coins.sellPriceOf(world, ShopItem(BODY, amount = 1, sellPrice = 900)))
        assertEquals(0, coins.sellPriceOf(world, ShopItem(BODY, amount = 1, sellPrice = 0)), "an unavailable item stays unavailable")
        assertEquals(100, ItemCurrency(TICKETS, "ticket", "tickets").sellPriceOf(world, ShopItem(BODY, amount = 1, sellPrice = 100)), "coins only")
        assertEquals(510L, ItemCurrency.highAlchValue(850))
        assertEquals(1L, ItemCurrency.highAlchValue(1))
    }

    @Test
    fun `a full inventory never destroys coins`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        repeat(27) { inventory[it] = Item(JUNK) }
        inventory[27] = Item(COINS, 50 * 900)
        val coins = ItemCurrency(COINS, "coin", "coins")
        val s = shop(coins, ShopItem(BODY, amount = 100, sellPrice = 900))

        coins.sellToPlayer(player(inventory), s, slot = 0, amt = 50)

        val value = inventory.getItemCount(COINS).toLong() + inventory.getItemCount(BODY) * 900L
        assertEquals(50L * 900, value, "coins plus bought items keep their value")
    }

    @Test
    fun `a purchase is limited to the free slots and charged exactly`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        repeat(25) { inventory[it] = Item(JUNK) }
        inventory[25] = Item(COINS, 50 * 900)
        val coins = ItemCurrency(COINS, "coin", "coins")
        val s = shop(coins, ShopItem(BODY, amount = 100, sellPrice = 900))

        coins.sellToPlayer(player(inventory), s, slot = 0, amt = 50)

        assertEquals(2, inventory.getItemCount(BODY))
        assertEquals(48 * 900, inventory.getItemCount(COINS))
        assertEquals(98, s.items[0]!!.currentAmount)
    }

    @Test
    fun `selling to a point shop answers with a message instead of throwing`() {
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL).also { it.add(BODY, 1) }
        val points = PointCurrency("point", "points")
        val s = shop(points, ShopItem(BODY, amount = 1, sellPrice = 10), PurchasePolicy.BUY_NONE)
        points.buyFromPlayer(player(inventory), s, slot = 0, amt = 1)
        points.onBuyValueMessage(player(inventory), s, BODY)
        assertEquals(0, points.getBuyPrice(0, world, BODY))
        assertEquals(1, inventory.getItemCount(BODY), "nothing was taken")
    }

    companion object {
        const val COINS = 995
        const val TICKETS = 1464
        const val BODY = 1133
        const val JUNK = 1511
    }
}

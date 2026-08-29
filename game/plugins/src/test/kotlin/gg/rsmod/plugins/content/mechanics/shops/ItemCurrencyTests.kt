package gg.rsmod.plugins.content.mechanics.shops

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the zero-compensation item-loss fix in
 * [ItemCurrency.buyFromPlayer]: a sale must be rejected before the player's
 * item is removed whenever the resolved buy price is not positive, leaving
 * the player's inventory, currency and the shop's stock unchanged.
 */
class ItemCurrencyTests {
    private val currency = ItemCurrency(itemCurrency = COINS, singularCurrency = "coin", pluralCurrency = "coins")

    @Test
    fun `zero compensation sale is rejected without mutating state`() {
        val inventory = newInventory(SELL_ITEM, amount = 5)
        val player = newPlayer(inventory)
        val shop = newShop(SELL_ITEM, buyPrice = 0)

        currency.buyFromPlayer(player, shop, slot = 0, amt = 1)

        assertEquals(5, inventory.getItemCount(SELL_ITEM), "player's item must be untouched")
        assertEquals(0, inventory.getItemCount(COINS), "player must not receive any currency")
        assertEquals(0, shop.items[0]!!.currentAmount, "shop stock must be untouched")
    }

    @Test
    fun `positive compensation sale succeeds normally`() {
        val inventory = newInventory(SELL_ITEM, amount = 5)
        val player = newPlayer(inventory)
        val shop = newShop(SELL_ITEM, buyPrice = 7)

        currency.buyFromPlayer(player, shop, slot = 0, amt = 2)

        assertEquals(3, inventory.getItemCount(SELL_ITEM), "exactly the sold amount must be removed")
        assertEquals(14, inventory.getItemCount(COINS), "compensation must be price * amount sold")
        assertEquals(2, shop.items[0]!!.currentAmount, "shop stock must increase by the amount bought")
    }

    @Test
    fun `getBuyPrice truncates to zero for cheap items at high stock`() {
        val (cheapItem, cost) = cheapTradeableItem
        assertTrue(cost in 1..9, "fixture requires a cache item with cost 1..9, found cost=$cost for item=$cheapItem")

        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions

        val price = currency.getBuyPrice(stock = 10, world = world, item = cheapItem)

        assertEquals(0, price, "getBuyPrice must truncate to 0 for this cost/stock combination")
    }

    private fun newInventory(
        item: Int,
        amount: Int,
    ): ItemContainer {
        val inventory = ItemContainer(definitions, INVENTORY_KEY)
        inventory.add(item = item, amount = amount, assureFullInsertion = true)
        return inventory
    }

    private fun newShop(
        item: Int,
        buyPrice: Int,
    ): Shop {
        val items = arrayOfNulls<ShopItem?>(4)
        items[0] = ShopItem(item = item, amount = 0, buyPrice = buyPrice)
        return Shop(
            name = "Test shop",
            stockType = StockType.NORMAL,
            purchasePolicy = PurchasePolicy.BUY_ALL,
            currency = currency,
            items = items,
            sampleItems = arrayOfNulls(4),
            containsSamples = false,
        )
    }

    private fun newPlayer(inventory: ItemContainer): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.inventory } returns inventory
        return player
    }

    companion object {
        private const val SELL_ITEM = 4151
        private const val COINS = 995

        private val definitions = DefinitionSet()
        private lateinit var store: CacheLibrary
        private lateinit var cheapTradeableItem: Pair<Int, Int>

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            assertNotEquals(definitions.getCount(ItemDef::class.java), 0)

            cheapTradeableItem =
                definitions
                    .getAllKeys(ItemDef::class.java)
                    .asSequence()
                    .mapNotNull { id ->
                        val cost = definitions.get(ItemDef::class.java, id).cost
                        if (cost in 1..9) id to cost else null
                    }.firstOrNull() ?: (-1 to -1)
        }
    }
}

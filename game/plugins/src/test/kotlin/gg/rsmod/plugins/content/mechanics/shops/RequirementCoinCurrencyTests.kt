package gg.rsmod.plugins.content.mechanics.shops

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
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

/**
 * [RequirementCoinCurrency] (2026-09-22): the shop route Ava's devices and Max's cape now sell through. A refused rule
 * buys nothing, materials are charged per unit and cap the amount, and bundled items come with every unit.
 */
class RequirementCoinCurrencyTests {
    @Test
    fun `a refused requirement buys nothing and keeps the coins`() {
        val inventory = inventory(COINS to 10_000)
        val currency = RequirementCoinCurrency(mapOf(ACCUMULATOR to PurchaseRule(check = { "no" })))
        currency.sellToPlayer(player(inventory), shop(currency, ACCUMULATOR), slot = 0, amt = 1)
        assertEquals(0, inventory.getItemCount(ACCUMULATOR))
        assertEquals(10_000, inventory.getItemCount(COINS))
    }

    @Test
    fun `materials are charged per unit and cap the amount bought`() {
        val inventory = inventory(COINS to 10_000, STEEL_ARROW to 160)
        val currency = RequirementCoinCurrency(mapOf(ACCUMULATOR to PurchaseRule(materials = listOf(STEEL_ARROW to 75))))
        currency.sellToPlayer(player(inventory), shop(currency, ACCUMULATOR), slot = 0, amt = 5)
        assertEquals(2, inventory.getItemCount(ACCUMULATOR), "160 arrows pay for two accumulators")
        assertEquals(10, inventory.getItemCount(STEEL_ARROW))
        assertEquals(10_000 - 2 * PRICE, inventory.getItemCount(COINS))
    }

    @Test
    fun `without the materials nothing is bought`() {
        val inventory = inventory(COINS to 10_000, STEEL_ARROW to 74)
        val currency = RequirementCoinCurrency(mapOf(ACCUMULATOR to PurchaseRule(materials = listOf(STEEL_ARROW to 75))))
        currency.sellToPlayer(player(inventory), shop(currency, ACCUMULATOR), slot = 0, amt = 1)
        assertEquals(0, inventory.getItemCount(ACCUMULATOR))
        assertEquals(74, inventory.getItemCount(STEEL_ARROW))
        assertEquals(10_000, inventory.getItemCount(COINS))
    }

    @Test
    fun `bundled items come with the purchase and the per-click cap holds`() {
        val inventory = inventory(COINS to 10_000)
        val currency = RequirementCoinCurrency(mapOf(CAPE to PurchaseRule(bonusItems = listOf(HOOD), maxPerPurchase = 1)))
        currency.sellToPlayer(player(inventory), shop(currency, CAPE), slot = 0, amt = 10)
        assertEquals(1, inventory.getItemCount(CAPE))
        assertEquals(1, inventory.getItemCount(HOOD))
        assertEquals(10_000 - PRICE, inventory.getItemCount(COINS))
    }

    private fun inventory(vararg items: Pair<Int, Int>): ItemContainer =
        ItemContainer(definitions, INVENTORY_KEY).also { inv -> items.forEach { (id, n) -> inv.add(id, n, assureFullInsertion = true) } }

    private fun shop(
        currency: RequirementCoinCurrency,
        item: Int,
    ): Shop {
        val items = arrayOfNulls<ShopItem?>(4)
        items[0] = ShopItem(item = item, amount = 10, sellPrice = PRICE)
        return Shop("Test", StockType.NORMAL, PurchasePolicy.BUY_NONE, currency, items, arrayOfNulls(4), false)
    }

    private fun player(inventory: ItemContainer): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.inventory } returns inventory
        return player
    }

    companion object {
        private const val COINS = 995
        private const val STEEL_ARROW = 886
        private const val ACCUMULATOR = 10499
        private const val CAPE = 20767
        private const val HOOD = 20768
        private const val PRICE = 999

        private val definitions = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            definitions.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}

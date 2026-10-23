package gg.rsmod.plugins.content.mechanics.shops

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopCurrency
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import gg.rsmod.game.tools.importer.ShopWindowScriptTool
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N42 / N114 (owner 2026-10-23: "het label = de echte serverprijs, alle winkels met eigen prijzen"): the price the shop window shows
 * under a cell ([ShopCurrency.priceOf], sent in [Shop.PRICE_CONTAINER]) is exactly what buying one of that row charges, for every
 * currency: coins, tokkul, the GE-priced Quartermaster, requirement shops (Max cape) and point shops (Breach Trader). And the
 * window protocol around it: Buy X (N62) and the 7-children rows (N221).
 */
class ShopPriceLabelTests {
    private val world: World = mockk<World>(relaxed = true).also { every { it.definitions } returns DEFINITIONS }

    private fun shop(
        currency: ShopCurrency,
        item: ShopItem,
    ): Shop {
        val items = arrayOfNulls<ShopItem?>(4)
        items[0] = item
        return Shop("Label test", StockType.NORMAL, PurchasePolicy.BUY_NONE, currency, items, arrayOfNulls(4), false)
    }

    private fun player(inventory: ItemContainer): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(DEFINITIONS, 800, gg.rsmod.game.model.container.ContainerStackType.STACK)
        return player
    }

    /** Coins (or tokkul) taken by buying one of row 0. */
    private fun charged(
        currency: ItemCurrency,
        item: ShopItem,
    ): Int {
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        inventory.add(currency.currencyItem, Int.MAX_VALUE / 2)
        currency.sellToPlayer(player(inventory), shop(currency, item), slot = 0, amt = 1)
        return Int.MAX_VALUE / 2 - inventory.getItemCount(currency.currencyItem)
    }

    @Test
    fun `the label of every item currency is what one purchase charges`() {
        val currencies =
            listOf(
                CoinCurrency(),
                TokkulCurrency(),
                GeCoinCurrency(),
                RequirementCoinCurrency(emptyMap()),
            )
        // An authored price above the cache value (Max cape 99,000 / Breach rows), one below the high-alchemy floor, the cache value.
        val rows =
            listOf(
                ShopItem(MAX_CAPE, 10, sellPrice = 99_000),
                ShopItem(WHIP, 10, sellPrice = 5),
                ShopItem(WHIP, 10),
                ShopItem(LOBSTER, 10, sellPrice = 150),
            )
        currencies.forEach { currency ->
            rows.forEach { row ->
                val label = currency.priceOf(world, row)
                assertTrue(label > 0, "${currency::class.simpleName} ${row.item}: priced")
                assertEquals(label, charged(currency, row), "${currency::class.simpleName} item ${row.item} sellPrice ${row.sellPrice}: label == charge")
            }
        }
        assertEquals(99_000, CoinCurrency().priceOf(world, rows[0]), "the Max cape label is its 99,000, not the cache's skillcape constant")
    }

    @Test
    fun `a point shop label is the points one purchase takes`() {
        val key = AttributeKey<Int>()
        val currency = PointCurrency("breach point", "breach points", key)
        val row = ShopItem(LOBSTER, 10, sellPrice = 5_000)
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val buyer = player(inventory)
        val attrs = AttributeMap()
        every { buyer.attr } returns attrs
        attrs[key] = 1_500_000
        currency.sellToPlayer(buyer, shop(currency, row), slot = 0, amt = 1)
        assertEquals(5_000, currency.priceOf(world, row))
        assertEquals(1_495_000, attrs[key], "5,000 points taken - the Breach Trader's slot 0 showed 50")
        assertEquals(-1, currency.currencyItem, "a point shop sends -1: no coin icon")
    }

    @Test
    fun `the shop window protocol - price inventory, currency varp, 7 children per row, Buy X`() {
        val player = File("../src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        val update = player.substringAfter("private fun updateShop()").substringBefore("\n    /**")
        assertTrue("containerKey = gg.rsmod.game.model.shop.Shop.PRICE_CONTAINER" in update && "shop.currency.priceOf(world, it)" in update, "updateShop sends the prices")
        val ext = File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText()
        val open = ext.substringAfter("fun Player.openShop(").substringBefore("\nfun ")
        assertTrue("setVarp(Varps.SHOP_CURRENCY, currentShop.currency.currencyItem)\n" in open, "the currency varp is always set, -1 included")
        assertTrue("items.size * gg.rsmod.game.tools.importer.ShopWindowScriptTool.CHILDREN_PER_ROW" in open)
        assertTrue("setting = gg.rsmod.game.model.shop.Shop.STOCK_EVENTS" in open, "openShop enables Buy X")
        val shopModel = File("../src/main/kotlin/gg/rsmod/game/model/shop/Shop.kt").readText()
        val refresh = shopModel.substringAfter("fun refresh(").substringBefore("fun cycle(")
        assertTrue("setting = STOCK_EVENTS" in refresh && "CHILDREN_PER_ROW" in refresh, "a stock refresh keeps Buy X and the 7-child stride")
        val events = Shop.STOCK_EVENTS
        (1..7).forEach { op -> assertTrue(events and (1 shl op) != 0, "op $op enabled") }
        assertTrue(events and (1 shl 10) != 0, "Examine enabled")
        val shops = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/shops/shops.plugin.kts").readText()
        assertTrue("player.getInteractingSlot() / gg.rsmod.game.tools.importer.ShopWindowScriptTool.CHILDREN_PER_ROW" in shops)
        assertTrue("val BUY_X_OPCODE = 18" in shops && "BUY_X_OPCODE -> buyX(" in shops, "op 7 (opcode 18, like Sell 50 of the inventory side) asks the amount")
        assertEquals(7, ShopWindowScriptTool.CHILDREN_PER_ROW)
    }

    companion object {
        private const val MAX_CAPE = 20747
        private const val WHIP = 4151
        private const val LOBSTER = 379
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            check(DEFINITIONS.get(ItemDef::class.java, WHIP).name == "Abyssal whip")
            check(Item(MAX_CAPE).getDef(DEFINITIONS).name.startsWith("Max cape")) { "item $MAX_CAPE is ${Item(MAX_CAPE).getDef(DEFINITIONS).name}" }
        }
    }
}

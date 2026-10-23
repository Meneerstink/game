package gg.rsmod.plugins.content.areas.draynor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.home.QuartermasterSupplies
import gg.rsmod.plugins.content.mechanics.exchange.CrystalShardPrice
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import gg.rsmod.plugins.content.mechanics.exchange.OsrsPriceBook
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N35 (owner VRAGEN 9 B): Crystal shards are sold in Ava's Odds and Ends at the Enhanced crystal teleport seed price / 150.
 * The row is built by [CrystalShardPrice.shopItem], the exact call ava.plugin.kts makes; the shop route is the real
 * [CoinCurrency] buy and sell code on the real cache definitions.
 */
class AvaShardShopTests {
    private val world: World = mockk<World>(relaxed = true).also { every { it.definitions } returns DEFINITIONS }

    private fun book(seedPrice: Int?) =
        OsrsPriceBook(
            hourly = if (seedPrice == null) emptyMap() else mapOf(CrystalShardPrice.SEED_OSRS_ID to seedPrice),
            idMap = emptyMap(),
            osrsNames = emptyMap(),
            guide = OsrsGuidePrices.Table(emptyMap(), "fixture"),
        )

    /** Ava's shop as ava.plugin.kts builds it: the five arrow rows, then the shard row in slot 5. */
    private fun avaShop(row: ShopItem): Shop {
        val items = arrayOfNulls<ShopItem?>(Shop.CLIENT_VISIBLE_SLOTS)
        items[0] = ShopItem(Items.FEATHER, 1000)
        items[1] = ShopItem(Items.IRON_ARROW, 40)
        items[2] = ShopItem(Items.STEEL_ARROW, 10)
        items[3] = ShopItem(Items.IRON_ARROWTIPS, 30)
        items[4] = ShopItem(Items.STEEL_ARROWTIPS, 20)
        items[5] = row
        return Shop("Ava's Odds and Ends", StockType.NORMAL, PurchasePolicy.BUY_STOCK, CoinCurrency(), items, arrayOfNulls(6), false)
    }

    private fun player(inventory: ItemContainer): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(DEFINITIONS, 800, gg.rsmod.game.model.container.ContainerStackType.STACK)
        return player
    }

    private fun inventoryWith(
        coins: Int,
        junk: Int = 0,
    ) = ItemContainer(DEFINITIONS, INVENTORY_KEY).also { inv ->
        repeat(junk) { inv[it] = Item(Items.LOGS) }
        if (coins > 0) inv[junk] = Item(Items.COINS_995, coins)
    }

    @Test
    fun `T11 the shard row is priced from the seed, never 10000 or 1, and buy 1 5 10 50 500 charge exactly`() {
        val row = CrystalShardPrice.shopItem(book(3_000_000))
        val price = CrystalShardPrice.of(book(3_000_000))!!
        assertEquals(20_000, price)
        assertEquals(Items.CRYSTAL_SHARD, row.item)
        assertEquals(price, row.sellPrice)
        assertEquals(0, row.buyPrice)
        assertEquals(CrystalShardPrice.STOCK, row.amount)
        assertEquals(QuartermasterSupplies.resupplyAmount(CrystalShardPrice.STOCK), row.resupplyAmount)

        val shop = avaShop(row)
        val currency = CoinCurrency()
        assertEquals(price, currency.sellPriceOf(world, shop.items[5]!!), "the shop charges the authored price (an untradeable has no alch floor)")
        var coins = 566 * price
        val inventory = inventoryWith(coins = coins)
        var stock = CrystalShardPrice.STOCK
        var held = 0
        listOf(1, 5, 10, 50, 500).forEach { n ->
            currency.sellToPlayer(player(inventory), shop, slot = 5, amt = n)
            coins -= n * price
            stock -= n
            held += n
            assertEquals(held, inventory.getItemCount(Items.CRYSTAL_SHARD), "shards after buying $n")
            assertEquals(coins, inventory.getItemCount(Items.COINS_995), "coins after buying $n")
            assertEquals(stock, shop.items[5]!!.currentAmount, "stock after buying $n")
            assertEquals(1, (0 until inventory.capacity).count { inventory[it]?.id == Items.CRYSTAL_SHARD }, "one stacked slot")
        }
    }

    @Test
    fun `T12 not enough coins for 500 buys floor of coins over price, never a free or part-priced shard`() {
        val price = CrystalShardPrice.of(book(3_000_000))!!
        val shop = avaShop(CrystalShardPrice.shopItem(book(3_000_000)))
        val inventory = inventoryWith(coins = 7 * price + price - 1)
        CoinCurrency().sellToPlayer(player(inventory), shop, slot = 5, amt = 500)
        assertEquals(7, inventory.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(price - 1, inventory.getItemCount(Items.COINS_995), "only whole shards are paid for")

        val poor = inventoryWith(coins = price - 1)
        CoinCurrency().sellToPlayer(player(poor), shop, slot = 5, amt = 1)
        assertEquals(0, poor.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(price - 1, poor.getItemCount(Items.COINS_995))
    }

    @Test
    fun `a full inventory without a shard stack takes no coins, with a shard stack it still stacks`() {
        val price = CrystalShardPrice.of(book(3_000_000))!!
        val shop = avaShop(CrystalShardPrice.shopItem(book(3_000_000)))
        val coins = 10 * price
        val full = inventoryWith(coins = coins, junk = 27) // 27 logs + the coin stack = 28 slots
        assertEquals(0, full.freeSlotCount)
        CoinCurrency().sellToPlayer(player(full), shop, slot = 5, amt = 5)
        assertEquals(0, full.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(coins, full.getItemCount(Items.COINS_995), "no coins taken")
        assertEquals(CrystalShardPrice.STOCK, shop.items[5]!!.currentAmount)

        val stacked = inventoryWith(coins = coins, junk = 26)
        stacked[27] = Item(Items.CRYSTAL_SHARD, 100)
        assertEquals(0, stacked.freeSlotCount)
        CoinCurrency().sellToPlayer(player(stacked), shop, slot = 5, amt = 5)
        assertEquals(105, stacked.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(coins - 5 * price, stacked.getItemCount(Items.COINS_995))
    }

    @Test
    fun `T13 with no valid seed price the row stays but is not buyable - nothing moves`() {
        val row = CrystalShardPrice.shopItem(book(null))
        assertEquals(0, row.sellPrice, "explicit 0, not null (null would fall back to the cache value 10,000)")
        assertEquals(0, row.buyPrice)
        val shop = avaShop(row)
        val currency = CoinCurrency()
        assertEquals(0, currency.sellPriceOf(world, shop.items[5]!!))
        val inventory = inventoryWith(coins = 5_000_000)
        listOf(1, 500).forEach { n ->
            currency.sellToPlayer(player(inventory), shop, slot = 5, amt = n)
            assertEquals(0, inventory.getItemCount(Items.CRYSTAL_SHARD), "no shard for $n")
            assertEquals(5_000_000, inventory.getItemCount(Items.COINS_995), "coins unchanged for $n")
            assertEquals(CrystalShardPrice.STOCK, shop.items[5]!!.currentAmount, "stock unchanged for $n")
        }
    }

    @Test
    fun `T14 the shop never buys a shard back, valid price or not`() {
        listOf<Int?>(3_000_000, null).forEach { seed ->
            val row = CrystalShardPrice.shopItem(book(seed))
            val shop = avaShop(row)
            val currency = CoinCurrency()
            assertTrue(currency.buyPriceOf(world, row, Items.CRYSTAL_SHARD, row.currentAmount) <= 0, "buy-back price, seed=$seed")

            val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
            inventory[0] = Item(Items.CRYSTAL_SHARD, 200)
            currency.buyFromPlayer(player(inventory), shop, slot = 0, amt = 200)
            assertEquals(200, inventory.getItemCount(Items.CRYSTAL_SHARD), "shards stay, seed=$seed")
            assertEquals(0, inventory.getItemCount(Items.COINS_995), "no coins paid, seed=$seed")
            assertEquals(CrystalShardPrice.STOCK, shop.items[5]!!.currentAmount, "stock unchanged, seed=$seed")
        }
    }

    @Test
    fun `the checked-in price book gives a buyable shard row with the current price`() {
        val live = OsrsGuidePrices.book
        val row = CrystalShardPrice.shopItem(live)
        val price = CrystalShardPrice.of(live)
        assertNotNull(price, "the checked-in book has a valid Enhanced crystal teleport seed price")
        assertEquals(price, row.sellPrice)
        assertTrue(row.sellPrice!! > 0 && row.sellPrice != 10_000)
    }

    @Test
    fun `ava_plugin adds the shard row to Buy devices (N224) through CrystalShardPrice shopItem`() {
        val root = listOf(File("src/main/kotlin"), File("plugins/src/main/kotlin")).first { it.isDirectory }
        val source = root.resolve("gg/rsmod/plugins/content/areas/draynor/ava.plugin.kts").readText()
        val shop = source.substringAfter("    AVAS_DEVICES,").substringBefore("on_npc_option")
        assertTrue("items[3] = CrystalShardPrice.shopItem(OsrsGuidePrices.book)" in shop, "Ava's Devices must stock the shard row in slot 3")
        assertNull(Regex("""items\[\d+]\s*=\s*ShopItem\(Items\.CRYSTAL_SHARD""").find(source), "the row must come from CrystalShardPrice (explicit price, buyPrice 0)")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}

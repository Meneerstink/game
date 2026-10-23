package gg.rsmod.plugins.content.areas.home

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-03 (bug 205926, buglist E4/E5): Quartermaster noted supplies, no Prayer potion, every row visible.
 * Owner 2026-10-04 (bug 024055): ONE shop that Trade opens directly - no three-shop chooser.
 * Owner 2026-10-23: N222 one page (the gear too), N220 the window shows the items themselves (a purchase hands out the note),
 * N229 the price of every row is the Grand Exchange's OSRS price.
 */
class QuartermasterSuppliesTests {
    private fun def(id: Int) = DEFINITIONS.get(ItemDef::class.java, id)

    /** The one shop, exactly as home_transport.plugin.kts builds it. */
    private val shop = QuartermasterSupplies.rows(DEFINITIONS)

    @Test
    fun `no Prayer potion in any Quartermaster shop`() {
        val prayer = setOf(Items.PRAYER_POTION_4, Items.PRAYER_POTION_3, Items.PRAYER_POTION_2, Items.PRAYER_POTION_1)
        val offenders = shop.filter { it.item in prayer || def(it.item).name.startsWith("Prayer potion") }.map { it.item }
        assertTrue(offenders.isEmpty(), "Prayer potion still stocked: $offenders")
    }

    @Test
    fun `N222 - every row, the gear included, exists, is priced and fits ONE shop window`() {
        val all = QuartermasterSupplies.stock
        assertTrue(shop.size <= Shop.CLIENT_VISIBLE_SLOTS, "the shop has ${shop.size} rows, the window draws ${Shop.CLIENT_VISIBLE_SLOTS}")
        shop.forEach { assertTrue(it.price > 0, "${it.item} '${def(it.item).name}' has no price") }
        assertEquals(all.size, shop.size, "a stock row is missing from this cache")
        assertEquals(all.size, all.map { it.first }.distinct().size, "a row is stocked twice")
        assertTrue(QuartermasterSupplies.gear.all { (id, _) -> shop.any { it.item == id } }, "the gear is on the one page")
        assertTrue(QuartermasterSupplies.droppedForWindow.none { id -> shop.any { it.item == id } }, "a dropped bolt row is still stocked")
    }

    @Test
    fun `N220 - the window shows every item itself, a purchase of a consumable hands out its banknote`() {
        val consumableIds = QuartermasterSupplies.consumables.map { it.first }.toSet()
        shop.forEach { row -> assertTrue(!def(row.item).noted, "${def(row.item).name} (${row.item}) is shown as a banknote") }
        val report = mutableListOf<String>()
        shop.forEach { row ->
            val base = def(row.item)
            when {
                row.item !in consumableIds -> assertEquals(row.item, row.delivered, "${base.name} is not a consumable and is handed out as itself")
                base.stacks -> assertEquals(row.item, row.delivered, "${base.name} stacks already and is handed out as itself")
                row.delivered == row.item -> report += "${base.name} (${base.id}) has no note"
                else -> assertTrue(def(row.delivered).noted && def(row.delivered).noteLinkId == row.item, "${row.delivered} is not the note of ${base.name}")
            }
        }
        println("QuartermasterSuppliesTests: handed out unnoted (no cache note): $report")
        // Every food and potion row has a banknote in this cache and is handed out as that note.
        QuartermasterSupplies.foodAndPotions.forEach { (id, _) ->
            val row = shop.single { it.item == id }
            assertTrue(row.delivered != row.item && def(row.delivered).noted, "${def(id).name} ($id) is not handed out noted")
        }
        // Any consumable still handed out unnoted truly has no banknote anywhere in the cache (not a missed link).
        val notesOf =
            DEFINITIONS.getAllKeys(ItemDef::class.java)
                .mapNotNull { DEFINITIONS.getNullable(ItemDef::class.java, it) }
                .filter { it.noted }
                .map { it.noteLinkId }
                .toSet()
        shop.filter { it.item in consumableIds && it.delivered == it.item && !def(it.item).stacks }.forEach { row ->
            assertTrue(row.item !in notesOf, "${def(row.item).name} (${row.item}) has a banknote in the cache but is handed out unnoted")
        }
    }

    @Test
    fun `N229 - every row costs the Grand Exchange price, which is the OSRS price wherever OSRS has the item`() {
        val currency = gg.rsmod.plugins.content.mechanics.shops.GeCoinCurrency()
        val withoutOsrs = mutableListOf<String>()
        shop.forEach { row ->
            val shopItem = ShopItem(row.item, row.amount, sellPrice = row.price, deliverNoted = row.delivered != row.item)
            val ge = gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit(world, row.item)
            assertEquals(ge, currency.priceOf(world, shopItem), "${def(row.item).name}: the label")
            assertEquals(ge, currency.sellPriceOf(world, shopItem), "${def(row.item).name}: the charge")
            val osrs = gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices.osrsPrice(def(row.item))
            if (osrs != null) assertEquals(osrs, ge, "${def(row.item).name}: OSRS price") else withoutOsrs += def(row.item).name
        }
        println("QuartermasterSuppliesTests: rows without an OSRS GE price (cache value): $withoutOsrs")
    }

    @Test
    fun `A31 - glory(6) and (5) exist with their notes and teleport options, glory(6) is sold noted at its OSRS price`() {
        val glory6 = def(Items.AMULET_OF_GLORY_6)
        val glory5 = def(Items.AMULET_OF_GLORY_5)
        assertEquals("Amulet of glory(6)", glory6.name)
        assertEquals("Amulet of glory(5)", glory5.name)
        listOf(glory6 to Items.AMULET_OF_GLORY_6_NOTED, glory5 to Items.AMULET_OF_GLORY_5_NOTED).forEach { (item, note) ->
            assertEquals(note, QuartermasterSupplies.notedId(DEFINITIONS, item), "${item.name} has its banknote")
            assertTrue("Rub" in item.inventoryMenu, "${item.name} inventory ${item.inventoryMenu.toList()}")
            // The same equipment options as glory(4): the plugin binds Edgeville / Karamja / Draynor Village / Al Kharid by name.
            assertEquals(def(Items.AMULET_OF_GLORY_4).equipmentMenu.filterNotNull(), item.equipmentMenu.filterNotNull(), item.name)
        }
        val row = shop.single { it.item == Items.AMULET_OF_GLORY_6 }
        assertEquals(Items.AMULET_OF_GLORY_6_NOTED, row.delivered, "shown as the amulet, handed out noted")
        assertEquals(10_139, row.price, "OSRS guide price of Amulet of glory(6)")
        assertTrue(shop.none { it.item == Items.AMULET_OF_GLORY_4 }, "glory(4) made way for glory(6) in the 40-row window")
    }

    @Test
    fun `N63 and N68 - Blood, Wrath and Soul runes are sold, the three super potions are gone`() {
        val sold = shop.map { it.item }.toSet()
        listOf(Items.BLOOD_RUNE, Items.WRATH_RUNE, Items.SOUL_RUNE).forEach { assertTrue(it in sold, "${def(it).name} is sold") }
        listOf(Items.SUPER_ATTACK_4, Items.SUPER_STRENGTH_4, Items.SUPER_DEFENCE_4).forEach { assertTrue(it !in sold, "${def(it).name} is not sold") }
        assertEquals("Wrath rune", def(Items.WRATH_RUNE).name)
        assertTrue(Items.SUPER_COMBAT_POTION_4 in sold, "Super combat stays")
    }

    @Test
    fun `the runes, sacks, arrows and bolts stack by themselves`() {
        val ammunitionIds = QuartermasterSupplies.ammunition.map { it.first }.toSet()
        shop.filter { it.item in ammunitionIds }.forEach { assertTrue(def(it.item).stackable, "${def(it.item).name} does not stack") }
    }

    @Test
    fun `buying 500 brews hands out 500 noted brews in one slot and charges exactly`() {
        val row = shop.first { it.item == Items.SARADOMIN_BREW_4 }
        val items = arrayOfNulls<ShopItem?>(Shop.CLIENT_VISIBLE_SLOTS)
        items[0] = ShopItem(row.item, row.amount, sellPrice = row.price, deliverNoted = row.delivered != row.item)
        val shop = Shop(QuartermasterSupplies.SHOP, StockType.NORMAL, PurchasePolicy.BUY_NONE, CoinCurrency(), items, arrayOfNulls(6), false)
        val currency = CoinCurrency()
        val price = currency.sellPriceOf(world, items[0]!!)

        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        repeat(26) { inventory[it] = Item(Items.SARADOMIN_BREW_4) }
        inventory[26] = Item(Items.COINS_995, 600 * price)
        currency.sellToPlayer(player(inventory), shop, slot = 0, amt = 500)

        assertTrue(def(row.delivered).noted, "a brew row hands out the banknote")
        assertEquals(500, inventory.getItemCount(row.delivered), "500 noted brews in the last free slot")
        assertEquals(26, inventory.getItemCount(row.item), "no unnoted brew was added")
        assertEquals(100 * price, inventory.getItemCount(Items.COINS_995))
        assertEquals(row.amount - 500, shop.items[0]!!.currentAmount)

        // A second buy stacks onto the same note even with no free slot.
        currency.sellToPlayer(player(inventory), shop, slot = 0, amt = 50)
        assertEquals(550, inventory.getItemCount(row.delivered))
        assertEquals(50 * price, inventory.getItemCount(Items.COINS_995))
    }

    @Test
    fun `N192 and N62 - no rune arrows, opal dragon bolts (e) stocked, 10,000 of every row`() {
        assertTrue(shop.none { it.item == Items.RUNE_ARROW }, "rune arrows are gone")
        assertTrue(shop.any { it.item == Items.OPAL_DRAGON_BOLTS_E }, "opal dragon bolts (e) are sold")
        (QuartermasterSupplies.stock + QuartermasterSupplies.gear).forEach { (id, amount) -> assertEquals(QuartermasterSupplies.ROW_STOCK, amount, "${def(id).name}") }
    }

    @Test
    fun `N185 N186 N200 - the one shop sells the RFD gloves, the god capes and Ava's devices, priced`() {
        val gear = QuartermasterSupplies.rows(DEFINITIONS, QuartermasterSupplies.gear)
        assertEquals(QuartermasterSupplies.gear.size, gear.size, "a gear row is missing from this cache")
        gear.forEach { assertTrue(it.price > 0, "${def(it.item).name} has no price") }
        val ids = shop.map { it.item }.toSet()
        listOf(7462, 7459, 7458, Items.SARADOMIN_CAPE, Items.GUTHIX_CAPE, Items.ZAMORAK_CAPE, Items.AVAS_ACCUMULATOR, Items.AVAS_ATTRACTOR)
            .forEach { assertTrue(it in ids, "row $it") }
    }

    @Test
    fun `N191 - the Quartermaster charges the GE price and high alchemy never pays more than it`() {
        val halibut = def(Items.HALIBUT)
        val ge = gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices.seed(halibut)
        val currency = gg.rsmod.plugins.content.mechanics.shops.GeCoinCurrency()
        assertEquals(ge, currency.sellPriceOf(world, ShopItem(Items.HALIBUT, 1)), "the GE price, not the alchemy floor")
        val highAlch = gg.rsmod.plugins.content.magic.alchemy.Alchemy.coins(halibut, true, ge)
        assertTrue(highAlch <= ge, "high alchemy $highAlch <= GE price $ge")
        assertEquals((halibut.cost * 0.6).toInt(), gg.rsmod.plugins.content.magic.alchemy.Alchemy.coins(halibut, true), "without a GE price: the cache rule")
    }

    private val world: World = mockk<World>(relaxed = true).also { every { it.definitions } returns DEFINITIONS }

    private fun player(inventory: ItemContainer): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(DEFINITIONS, 800, gg.rsmod.game.model.container.ContainerStackType.STACK)
        return player
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

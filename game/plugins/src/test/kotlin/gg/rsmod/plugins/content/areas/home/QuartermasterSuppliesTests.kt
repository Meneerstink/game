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
 */
class QuartermasterSuppliesTests {
    private fun def(id: Int) = DEFINITIONS.get(ItemDef::class.java, id)

    /** The one shop, exactly as home_transport.plugin.kts builds it. */
    private val shop = QuartermasterSupplies.rows(DEFINITIONS, QuartermasterSupplies.stock, noted = true)

    @Test
    fun `no Prayer potion in any Quartermaster shop`() {
        val prayer = setOf(Items.PRAYER_POTION_4, Items.PRAYER_POTION_3, Items.PRAYER_POTION_2, Items.PRAYER_POTION_1)
        val offenders = shop.filter { it.unnoted in prayer || def(it.unnoted).name.startsWith("Prayer potion") }.map { it.unnoted }
        assertTrue(offenders.isEmpty(), "Prayer potion still stocked: $offenders")
    }

    @Test
    fun `every row exists, is priced and fits the 40-slot shop window`() {
        val all = QuartermasterSupplies.stock
        assertTrue(shop.size <= Shop.CLIENT_VISIBLE_SLOTS, "the shop has ${shop.size} rows, the window draws ${Shop.CLIENT_VISIBLE_SLOTS}")
        shop.forEach { assertTrue(it.price > 0, "${it.unnoted} '${def(it.unnoted).name}' has no price") }
        assertEquals(all.size, shop.size, "a stock row is missing from this cache")
        assertEquals(all.size, all.map { it.first }.distinct().size, "a row is stocked twice")
        assertTrue(QuartermasterSupplies.droppedForWindow.none { id -> shop.any { it.unnoted == id } }, "a dropped bolt row is still stocked")
    }

    @Test
    fun `the noted shop sells the banknote of every consumable that has one, at the item's own price`() {
        val loose = QuartermasterSupplies.rows(DEFINITIONS, QuartermasterSupplies.consumables, noted = false)
        val consumableIds = QuartermasterSupplies.consumables.map { it.first }.toSet()
        val noted = shop.filter { it.unnoted in consumableIds }
        assertEquals(loose.map { it.unnoted }, noted.map { it.unnoted })
        val report = mutableListOf<String>()
        loose.zip(noted).forEach { (l, n) ->
            assertEquals(l.price, n.price, "note price of ${l.unnoted}")
            assertEquals(l.amount, n.amount)
            val base = def(l.unnoted)
            if (base.stacks) {
                assertEquals(l.unnoted, n.item, "${base.name} stacks already and is sold as itself")
            } else {
                val note = def(n.item)
                if (n.item == l.unnoted) report += "${base.name} (${base.id}) has no note" else {
                    assertTrue(note.noted && note.noteLinkId == l.unnoted, "${n.item} is not the note of ${base.name}")
                }
            }
        }
        println("QuartermasterSuppliesTests: sold unnoted in the noted shop (no cache note): $report")
        // Every food and potion row has a banknote in this cache and is sold as that note.
        val foodAndPotions = QuartermasterSupplies.foodAndPotions.map { it.first }.toSet()
        noted.filter { it.unnoted in foodAndPotions }.forEach { row ->
            assertTrue(row.item != row.unnoted && def(row.item).noted, "${def(row.unnoted).name} (${row.unnoted}) is not sold noted")
        }
        // Any row the noted shop still hands out unnoted truly has no banknote anywhere in the cache (not a missed link).
        val notesOf =
            DEFINITIONS.getAllKeys(ItemDef::class.java)
                .mapNotNull { DEFINITIONS.getNullable(ItemDef::class.java, it) }
                .filter { it.noted }
                .map { it.noteLinkId }
                .toSet()
        noted.filter { it.item == it.unnoted && !def(it.unnoted).stacks }.forEach { row ->
            assertTrue(row.unnoted !in notesOf, "${def(row.unnoted).name} (${row.unnoted}) has a banknote in the cache but is sold unnoted")
        }
    }

    @Test
    fun `the runes, sacks, arrows and bolts stack by themselves`() {
        val ammunitionIds = QuartermasterSupplies.ammunition.map { it.first }.toSet()
        shop.filter { it.unnoted in ammunitionIds }.forEach { assertTrue(def(it.item).stackable, "${def(it.item).name} does not stack") }
    }

    @Test
    fun `buying 500 noted brews takes one slot and charges exactly`() {
        val row = shop.first { it.unnoted == Items.SARADOMIN_BREW_4 }
        val items = arrayOfNulls<ShopItem?>(Shop.CLIENT_VISIBLE_SLOTS)
        items[0] = ShopItem(row.item, row.amount, sellPrice = row.price)
        val shop = Shop(QuartermasterSupplies.SHOP, StockType.NORMAL, PurchasePolicy.BUY_NONE, CoinCurrency(), items, arrayOfNulls(6), false)
        val currency = CoinCurrency()
        val price = currency.sellPriceOf(world, items[0]!!)

        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        repeat(26) { inventory[it] = Item(Items.SARADOMIN_BREW_4) }
        inventory[26] = Item(Items.COINS_995, 600 * price)
        currency.sellToPlayer(player(inventory), shop, slot = 0, amt = 500)

        assertEquals(500, inventory.getItemCount(row.item), "500 noted brews in the last free slot")
        assertEquals(100 * price, inventory.getItemCount(Items.COINS_995))
        assertEquals(row.amount - 500, shop.items[0]!!.currentAmount)

        // A second buy stacks onto the same note even with no free slot.
        currency.sellToPlayer(player(inventory), shop, slot = 0, amt = 50)
        assertEquals(550, inventory.getItemCount(row.item))
        assertEquals(50 * price, inventory.getItemCount(Items.COINS_995))
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

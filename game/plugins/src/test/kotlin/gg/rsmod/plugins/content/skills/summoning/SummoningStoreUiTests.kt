package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.game.model.shop.StockType
import gg.rsmod.game.tools.importer.StoreInterfaceImportTool
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A16 (owner 2026-10-11: "Pikkupstix-shop: 1 scherm, tabs Pouches / Scrolls / Supplies"): Trade opens one window with three tabs,
 * every pouch and every scroll is in its tab exactly once, and each row buys from the coin shop that sells it.
 */
class SummoningStoreUiTests {
    /** The coin shops as summoning_supply_shops.plugin.kts creates them (rows only; prices come from the shop's currency). */
    private fun shop(name: String, items: List<Int>): Shop =
        Shop(
            name,
            StockType.NORMAL,
            PurchasePolicy.BUY_NONE,
            CoinCurrency(),
            Array(Shop.CLIENT_VISIBLE_SLOTS) { i -> items.getOrNull(i)?.let { ShopItem(it, 1_000) } },
            arrayOfNulls(Shop.CLIENT_VISIBLE_SLOTS),
            false,
        )

    private val shops: Map<String, Shop> by lazy {
        val supplies = listOf(12155, 12183) // spirit shards, pouch: any two rows stand for the supplies shop
        mapOf(SummoningShopStock.SUPPLIES_SHOP to shop(SummoningShopStock.SUPPLIES_SHOP, supplies)) +
            SummoningShopStock.pages.associate { it.title to shop(it.title, it.stock.map { (item, _) -> item }) }
    }

    @Test
    fun `three tabs, Supplies first, in the store's category buttons`() {
        assertEquals(listOf("Supplies", "Pouches", "Scrolls"), SummoningStoreUi.Tab.values().map { it.label })
        assertTrue(SummoningStoreUi.Tab.values().size <= StoreInterfaceImportTool.SECTION_COUNT, "the window has a button per tab")
        assertEquals(listOf(SummoningShopStock.SUPPLIES_SHOP), SummoningStoreUi.shops(SummoningStoreUi.Tab.SUPPLIES))
    }

    @Test
    fun `every pouch and every scroll is in its tab exactly once, lowest level first`() {
        val pouches = SummoningStoreUi.rows({ shops[it] }, SummoningStoreUi.Tab.POUCHES).map { it.item }
        val scrolls = SummoningStoreUi.rows({ shops[it] }, SummoningStoreUi.Tab.SCROLLS).map { it.item }
        assertEquals(SummoningShopStock.pouches.map { it.first }, pouches)
        assertEquals(SummoningShopStock.scrolls.map { it.first }, scrolls)
        assertTrue(pouches.intersect(scrolls.toSet()).isEmpty())
        // More than one window page: the store's Prev / Next walk through a tab (owner 10-06 Q2 = A).
        assertTrue(pouches.size > StoreInterfaceImportTool.CURRENT.slotCount, "pouches need the page buttons")
    }

    @Test
    fun `each row points at the coin shop and slot that sells it`() {
        for (tab in SummoningStoreUi.Tab.values()) {
            for (row in SummoningStoreUi.rows({ shops[it] }, tab)) {
                assertEquals(row.item, shops.getValue(row.shop).items[row.slot]?.item, "$tab ${row.shop}:${row.slot}")
            }
        }
    }

    @Test
    fun `Pikkupstix Trade opens the one window and the store routes its clicks while it is open`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/summoning_supply_shops.plugin.kts").readText()
        val trade = plugin.substringAfter("on_npc_option(Npcs.PIKKUPSTIX, \"trade\")").substringBefore("}")
        assertTrue("SummoningStoreUi.open(player)" in trade, trade)
        val store = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/store.plugin.kts").readText()
        assertTrue("SummoningStoreUi.active(player)" in store && "SummoningStoreUi.click(" in store)
    }
}

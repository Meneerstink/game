package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.ext.MAX_DIALOG_OPTIONS
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner 2026-10-03 (bug 210035): Pikkupstix sells every familiar pouch and every scroll, in shop windows that fit. */
class SummoningShopStockTests {
    private fun def(id: Int) = DEFINITIONS.get(ItemDef::class.java, id)

    private val stocked = SummoningShopStock.pages.flatMap { page -> page.stock.map { it.first } }

    @Test
    fun `every pouch and every scroll of the summoning data is stocked exactly once`() {
        val pouches = SummoningPouchData.values.map { it.pouch }.toSet()
        val scrolls = SummoningScrollData.values.map { it.scroll }.toSet()
        assertEquals(pouches + scrolls, stocked.toSet(), "pouch/scroll set")
        assertEquals(stocked.size, stocked.distinct().size, "an item is stocked twice")
        println("SummoningShopStockTests: ${pouches.size} pouches, ${scrolls.size} scrolls in " + SummoningShopStock.pages.map { "${it.title}=${it.stock.size}" })
    }

    @Test
    fun `every summonable pouch in the 667 cache is stocked`() {
        // Independent of our data: every cache item named "... pouch" with a Summon option. Known non-familiar pouches excluded:
        // Wolf Whistle's quest pouch, the Daemonheim-only Dungeoneering pouches, and the two familiars this server has no data for.
        val notFamiliars = setOf(12526, 19622, 21444) + (17935..17994)
        val missing =
            (0 until DEFINITIONS.getCount(ItemDef::class.java)).mapNotNull { DEFINITIONS.getNullable(ItemDef::class.java, it) }
                .filter { !it.noted && it.name.lowercase().endsWith("pouch") && it.inventoryMenu.any { op -> op.equals("Summon", ignoreCase = true) } }
                .filter { it.id !in notFamiliars && it.id !in stocked }
                .map { "${it.id} '${it.name}'" }
        assertTrue(missing.isEmpty(), "summonable pouches not stocked: $missing")
    }

    @Test
    fun `every page fits the shop window and the trade dialogue`() {
        SummoningShopStock.pages.forEach { page ->
            assertTrue(page.stock.size <= Shop.CLIENT_VISIBLE_SLOTS, "${page.title}: ${page.stock.size} rows")
            assertTrue(page.stock.isNotEmpty())
        }
        assertEquals(SummoningShopStock.pages.size, SummoningShopStock.pages.map { it.title }.distinct().size, "shop titles must be unique")
        // "Summoning supplies." plus one line per page.
        assertTrue(SummoningShopStock.pages.size + 1 <= MAX_DIALOG_OPTIONS)
    }

    @Test
    fun `every stocked pouch and scroll is a real cache item with a price`() {
        stocked.forEach { id ->
            val d = def(id)
            assertTrue(d.name.endsWith("pouch") || d.name.endsWith("scroll"), "$id '${d.name}'")
            assertTrue(!d.noted)
            assertTrue(SummoningShopStock.price(DEFINITIONS, id) > 0, "$id '${d.name}' has no price")
        }
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

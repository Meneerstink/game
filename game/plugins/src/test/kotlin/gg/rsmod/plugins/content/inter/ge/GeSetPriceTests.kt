package gg.rsmod.plugins.content.inter.ge

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.plugins.content.mechanics.exchange.GePrice
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N182 (owner 2026-10-22: "GE item sets: prijzen kloppen niet (bv. Mystic robe set 20 gp)"): a set is worth the sum of its parts.
 * N183: Exchange on a set in the sets window unpacks it when the player holds the set but not its parts.
 */
class GeSetPriceTests {
    @Test
    fun `a set costs the sum of its components`() {
        val mystic = GeItemSet.values().first { it.id == 11872 }
        val parts = mystic.items.sumOf { GePrice.unit(DEFINITIONS, null, it) }
        val set = GePrice.unit(DEFINITIONS, null, mystic.id)
        assertEquals(parts, set, "Mystic robes set = its five parts")
        assertTrue(set > 1_000, "not the cache's 20 gp: $set")
        // A set that OSRS trades under the same id keeps its own OSRS price; every other set is the sum of its parts.
        GeItemSet.values().filter { gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices.osrsPrice(DEFINITIONS.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.id)) == null }.forEach { s ->
            assertEquals(s.items.sumOf { GePrice.unit(DEFINITIONS, null, it) }, GePrice.unit(DEFINITIONS, null, s.id), s.name)
        }
    }

    /**
     * N445 (owner 2026-10-30, "Barrows armour sets kosten 20 gp op de GE"): the GE offer box and the house quote read the raw seed
     * (cache value 20 gp) instead of [GePrice]; every set's buy/sell start price is now at least / at most the sum of its parts.
     */
    @Test
    fun `the GE offer box and the house price a set at the sum of its components`() {
        val dir = kotlin.io.path.createTempDirectory("n445").toFile()
        val exchange = gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService(File(dir, "offers.json")).also { it.useDefinitions(DEFINITIONS); it.load() }
        val itemDef = gg.rsmod.game.fs.def.ItemDef::class.java
        val guthan = GeItemSet.values().first { it == GeItemSet.GUTHAN }
        val barrowsParts = guthan.items.sumOf { GePrice.unit(DEFINITIONS, null, it) }
        assertTrue(barrowsParts > 100_000, "Guthan's armour set parts are worth more than 100k: $barrowsParts")
        GeItemSet.values().forEach { s ->
            val def = DEFINITIONS.get(itemDef, s.id)
            val worth = GePrice.unit(DEFINITIONS, null, s.id)
            val buy = gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.offerGuide(exchange, def, gg.rsmod.plugins.content.mechanics.exchange.OfferType.BUY)
            assertTrue(buy >= worth, "${s.name}: buy offer starts at $buy, the set is worth $worth")
            exchange.houseQuote(s.id)?.let { q ->
                assertTrue(q.ask >= worth, "${s.name}: house ask ${q.ask} < worth $worth")
                assertEquals(minOf(worth, q.bid), q.bid, "${s.name}: house bid ${q.bid} above worth $worth")
                assertTrue(q.bid > def.cost || worth <= def.cost, "${s.name}: house bid ${q.bid} is still the cache value ${def.cost}")
            }
        }
        dir.deleteRecursively()
    }

    @Test
    fun `Exchange in the sets window unpacks a held set without its parts`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/inter/ge/Sets.plugin.kts").readText()
        assertTrue("set.items.any { !player.inventory.contains(it) } && player.inventory.contains(set.id)" in source)
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

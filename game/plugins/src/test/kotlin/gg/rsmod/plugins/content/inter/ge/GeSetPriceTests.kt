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

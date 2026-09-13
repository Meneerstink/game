package gg.rsmod.plugins.content.mechanics.travel

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import org.junit.BeforeClass
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 Q-017: the travel networks had no focused test. Every bound transport npc/object must carry the bound option in
 * the 667 cache, and every spirit tree object placed on the 667 map must be within the plugin's 30-tile station radius
 * of one of its 5 stations (otherwise the "origin" station cannot be resolved there).
 */
class TravelNetworkCacheTests {
    @Test
    fun `every bound transport carries its cache option`() {
        val bad = mutableListOf<String>()
        fun npcHas(id: Int, option: String) {
            val options = DEFINITIONS.get(NpcDef::class.java, id).options.filterNotNull().map { it.lowercase() }
            if (option.lowercase() !in options) bad += "npc $id lacks '$option' $options"
        }
        fun objHas(id: Int, option: String) {
            val options = DEFINITIONS.get(ObjectDef::class.java, id).options.filterNotNull().map { it.lowercase() }
            if (option.lowercase() !in options) bad += "obj $id lacks '$option' $options"
        }
        SPIRIT_TREES.forEach { objHas(it, "Teleport") }
        GLIDER_PILOTS.forEach { npcHas(it, "Glider") }
        RUG_MERCHANTS.forEach { npcHas(it, "Travel") }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    @Test
    fun `every spirit tree on the 667 map resolves to a station`() {
        val cache = File("../../data/cache").path
        val xtea = File("../../data").walkTopDown().maxDepth(3).first { it.isFile && it.name.contains("xtea", ignoreCase = true) }.path
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            ObjectPlacementProbeTool.main(arrayOf(cache, xtea, "id") + SPIRIT_TREES.map { it.toString() })
        } finally {
            System.setOut(original)
        }
        val placed = Regex("""PLACEMENT id=(\d+) .* tile=(\d+),(\d+),(\d+)""").findAll(buffer.toString()).map {
            Triple(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt()) to it.groupValues[1].toInt()
        }.toList()
        println("TRAVEL_SPIRIT_TREES placed=${placed.map { "${it.second}@${it.first}" }}")
        val unresolved = placed.filter { (tile, _) ->
            STATIONS.none { (x, z) -> tile.third == 0 && maxOf(abs(tile.first - x), abs(tile.second - z)) <= 30 }
        }.map { "${it.second}@${it.first}" }
        println("TRAVEL_SPIRIT_TREES unresolved=$unresolved")
        assertTrue(placed.isNotEmpty(), "no spirit tree placements decoded")
        assertTrue(unresolved.isEmpty(), "spirit trees with no station within 30 tiles: $unresolved")
    }

    companion object {
        // Rosters copied from the plugins (spirit_tree / gnome_glider / magic_carpet .plugin.kts).
        private val SPIRIT_TREES = listOf(Objs.SPIRIT_TREE_1293, Objs.SPIRIT_TREE_1294, Objs.SPIRIT_TREE_1295, Objs.SPIRIT_TREE_1317)
        private val GLIDER_PILOTS = listOf(
            Npcs.GNORMADIUM_AVLAFRIM, Npcs.CAPTAIN_DALBUR, Npcs.CAPTAIN_BLEEMADGE, Npcs.CAPTAIN_ERRDO, Npcs.CAPTAIN_KLEMFOODLE, Npcs.CAPTAIN_BELMONDO,
        )
        private val RUG_MERCHANTS = listOf(
            Npcs.RUG_MERCHANT, Npcs.RUG_MERCHANT_2292, Npcs.RUG_MERCHANT_2293, Npcs.RUG_MERCHANT_2294, Npcs.RUG_MERCHANT_2298, Npcs.RUG_MERCHANT_3020,
        )
        private val STATIONS = listOf(2542 to 3169, 2462 to 3444, 2557 to 3259, 3185 to 3511, 2416 to 2851)
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}

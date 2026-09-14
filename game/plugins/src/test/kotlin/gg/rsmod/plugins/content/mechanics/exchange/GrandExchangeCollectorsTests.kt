package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RCV-012.B16 roster: every bank npc in the production cache (definition carries `Bank` and `Collect`) opens the collection box, the
 * three other `Collect`-like npcs do not, and bank booths / counters open the box instead of the old instant payout.
 */
class GrandExchangeCollectorsTests {
    @Test
    fun `every bank npc in the cache is a collection box collector`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        val npcs =
            try {
                val definitions = DefinitionSet()
                definitions.load(library, NpcDef::class.java)
                @Suppress("UNCHECKED_CAST")
                definitions.getAll(NpcDef::class.java) as Map<Int, NpcDef>
            } finally {
                library.close()
            }
        val collectors = npcs.filter { (_, def) -> GrandExchangeCollectors.isBankCollectNpc(def.options) }.keys
        val expected =
            setOf(
                44, 45, 166, 494, 495, 496, 497, 498, 499, 553, 902, 909, 958, 1036, 1360, 1702, 2163, 2164, 2271, 2354, 2355, 2619, 2718,
                2759, 3046, 3198, 3199, 3293, 3416, 3418, 3824, 4296, 4456, 4457, 4458, 4459, 4519, 4907, 5257, 5258, 5259, 5260, 5383, 5488,
                5776, 5777, 5898, 6200, 6362, 7049, 7050, 7605, 8948, 9710, 11299, 14163,
            )
        assertEquals(expected, collectors)
        // Ghost disciple (ectotokens), Advisor Ghrim (Miscellania) and Head Guard ("Collect-Bank") are not bank collectors.
        listOf(1686, 3120, 13932).forEach { assertTrue(it !in collectors, "npc $it") }
    }

    @Test
    fun `bank booths and bank npcs open the collection box`() {
        val booths = File("src/main/kotlin/gg/rsmod/plugins/content/objs/bank_locs/bank_booths.plugin.kts").readText()
        assertTrue("GrandExchangeInterface.openCollectionBox(player)" in booths)
        assertTrue("GrandExchangeCollection.collect(" !in booths, "the instant payout stays on ::ge_collect only")
        val bankers = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/bankers/banker_collect.plugin.kts").readText()
        assertTrue("GrandExchangeCollectors.isBankCollectNpc(def.options)" in bankers)
        assertTrue("on_npc_option(npc, option = \"Collect\", lineOfSightDistance = 2)" in bankers)
        assertTrue("GrandExchangeInterface.openCollectionBox(player)" in bankers)
    }
}

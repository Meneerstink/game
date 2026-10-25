package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.content.items.osrs.OrnamentKitRules
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * K01 deel 5 (N327 N369 N371, owner 2026-10-25 V2/V11/V12): every ornament kit in the game is untradeable - no Grand Exchange, no
 * trade - and so is the item with the kit on it; every kit comes from one of the three stores and nowhere else.
 */
class KitTradeabilityTests {
    private val tradeable: Map<Int, Boolean> by lazy {
        val map = HashMap<Int, Boolean>()
        var id = -1
        File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") -> map[id] = line.removePrefix("  tradeable: ").trim() == "true"
            }
        }
        map
    }

    @Test
    fun `every kit and every item with a kit on it is untradeable`() {
        val wrong = (OrnamentKitRules.KITS + OrnamentKitRules.KITTED).filter { tradeable[it] != false }.sorted()
        assertTrue(wrong.isEmpty(), "tradeable in items.yml: $wrong")
    }

    @Test
    fun `no casket, chest or drop table gives a kit or a kitted item`() {
        // V12: every kit comes from the stores only. The reward tables name items by number (EliteCasketTable, casket.plugin.kts,
        // the Deadman chest, the npc drop tables), so the ids themselves are searched.
        val ids = OrnamentKitRules.KITS + OrnamentKitRules.KITTED
        val sources =
            listOf(
                "src/main/kotlin/gg/rsmod/plugins/content/items/EliteCasketTable.kt",
                "src/main/kotlin/gg/rsmod/plugins/content/items/casket.plugin.kts",
                "src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/chest/DeadmanChestWorld.kt",
                "../../data/cfg/npcs/drop-tables.json",
                "../../data/cfg/npcs/godwars-drops.json",
            )
        val found =
            sources.flatMap { path ->
                val text = File(path).readText()
                ids.filter { Regex("(?<![\\w\\d])$it(?![\\d])").containsMatchIn(text) }.map { "$path: $it" }
            }
        assertTrue(found.isEmpty(), "kits outside the stores: $found")
    }

    @Test
    fun `every kit is sold in one of the three stores`() {
        val sold = StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.KIT }.map { it.purchaseItem }.toSet()
        val missing = (OrnamentKitRules.KITS - sold).sorted()
        assertTrue(missing.isEmpty(), "kits in no store: $missing")
    }
}

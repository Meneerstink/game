package gg.rsmod.plugins.content.items

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guard for the shared data-driven item handlers (2026-09-24): `data/cfg/item_empty.tsv` (Empty) and `data/cfg/consumables.tsv`
 * (Eat / Drink). Every row must name a real, unnoted item that offers that option in the 667 cache, and every leftover must exist.
 */
class ConsumableTablesTests {
    private fun rows(file: String): List<List<String>> =
        File("../../data/cfg/$file").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trimEnd('\r').split('\t') }

    private fun def(id: Int): ItemDef? = DEFINITIONS.getNullable(ItemDef::class.java, id)

    private fun offers(id: Int, option: String): Boolean = def(id)?.inventoryMenu?.any { it.equals(option, ignoreCase = true) } == true

    @Test
    fun `every Empty row is a cache item that offers Empty and empties into a real item`() {
        val bad = rows("item_empty.tsv").filter { r -> !offers(r[0].toInt(), "Empty") || def(r[1].toInt()) == null }
        assertTrue(rows("item_empty.tsv").size > 250)
        assertTrue(bad.isEmpty(), "bad rows: ${bad.take(10)}")
    }

    @Test
    fun `every consumable row offers its verb, heals sensibly and leaves a real item`() {
        val table = rows("consumables.tsv")
        assertTrue(table.size > 150)
        val bad =
            table.filter { r ->
                val id = r[0].toInt()
                val verb = if (r[1] == "drink") "Drink" else "Eat"
                val (min, max) = r[2].toInt() to r[3].toInt()
                val leftover = r[4].toInt()
                !offers(id, verb) || def(id)?.noted == true || min < 0 || max < min || max > 300 || (leftover != -1 && def(leftover) == null)
            }
        assertTrue(bad.isEmpty(), "bad rows: ${bad.take(10)}")
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

package gg.rsmod.plugins.content.inter.ge

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.ItemDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 C3-b: the Grand Exchange item sets against the 667 cache. Enum 1087 is the client's own set roster (index ->
 * set item), enum 1088 the components text per set item. Every failing entry is named.
 */
class GrandExchangeSetsCacheTests {
    private val roster: Set<Int> by lazy {
        DEFINITIONS.get(EnumDef::class.java, 1087).values.values.map { (it as Number).toInt() }.toSet()
    }

    @Test
    fun `every table set is a 667 cache set and every cache set is in the table`() {
        val table = GeItemSet.values().associateBy { it.id }
        val notInCache = GeItemSet.values().filter { it.id !in roster }.map { "${it.name}(${it.id})" }
        val missing = roster.filter { it !in table }.sorted().map { "$it(${DEFINITIONS.getNullable(ItemDef::class.java, it)?.name})" }
        println("GE_SETS notInCache=$notInCache")
        println("GE_SETS missingFromTable=$missing")
        assertTrue(notInCache.isEmpty() && missing.isEmpty(), "notInCache=$notInCache missingFromTable=$missing")
    }

    @Test
    fun `every set component is a real unnoted item, not repeated, and every set has cache components text`() {
        val descriptions = DEFINITIONS.get(EnumDef::class.java, 1088).values
        val bad = mutableListOf<String>()
        GeItemSet.values().forEach { set ->
            if (set.items.size != set.items.toSet().size) bad += "${set.name}: repeated component ${set.items.toList()}"
            set.items.forEach { id ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, id)
                if (def == null || def.name.isBlank() || def.noted) bad += "${set.name}: component $id invalid (${def?.name})"
            }
            if (descriptions[set.id] !is String) bad += "${set.name}: no enum 1088 text"
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
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

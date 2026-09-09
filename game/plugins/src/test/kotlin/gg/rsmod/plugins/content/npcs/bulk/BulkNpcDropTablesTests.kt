package gg.rsmod.plugins.content.npcs.bulk

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.drops.DropTableBuilder
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Loads the real `data/cfg/npcs/drop-tables.json` against the real rev-667 cache and builds every
 * table through the real [DropTableBuilder], so a slot-count or item error anywhere in the roster
 * fails here instead of at a player's kill.
 */
class BulkNpcDropTablesTests {
    @Test
    fun `the whole generated drop table set builds against the real cache`() {
        val result = BulkNpcDropTables.load(definitions, TABLE)

        assertEquals(0, result.skippedUnknownNpc, "every row must refer to an npc id present in the 667 cache")
        assertEquals(0, result.droppedUnknownItem, "every item id must exist in the 667 cache")
        assertTrue(result.tables.size > 1500, "expected well over a thousand npc tables, got ${result.tables.size}")

        // Build every table with the real DSL: TableBuilder logs an error on a slot mismatch rather
        // than throwing, so count the nothing/obj slots directly through a built table.
        val player = mockk<Player>(relaxed = true)
        result.tables.forEach { (npc, table) ->
            val built = DropTableBuilder(player, SecureRandom()).apply(table).build()
            assertTrue(built.isNotEmpty(), "npc $npc produced no tables")
            built.filter { it.name != "guaranteed" }.forEach { t ->
                assertEquals(
                    BulkNpcDropTables.TOTAL,
                    t.entries.last().index,
                    "npc $npc table ${t.name} must use exactly ${BulkNpcDropTables.TOTAL} slots",
                )
            }
        }
    }

    @Test
    fun `a sourced table carries the documented 2007-era drops`() {
        // Fire giant (110): 100% Big bones (532), main-table Rune scimitar (1333) and Fire battlestaff (1393).
        val table = BulkNpcDropTables.load(definitions, TABLE).tables.getValue(110)
        val built = DropTableBuilder(mockk<Player>(relaxed = true), SecureRandom()).apply(table).build()
        val guaranteed = built.first { it.name == "guaranteed" }
        assertTrue(guaranteed.entries.any { (it.drop as? gg.rsmod.plugins.content.drops.DropEntry.ItemDrop)?.item?.id == 532 })
        val mainItems =
            built.first { it.name == "main" }.entries.mapNotNull {
                when (val d = it.drop) {
                    is gg.rsmod.plugins.content.drops.DropEntry.ItemDrop -> d.item.id
                    is gg.rsmod.plugins.content.drops.DropEntry.ItemRangeDrop -> d.item.id
                    else -> null
                }
            }
        assertTrue(1333 in mainItems, "Rune scimitar missing from the Fire giant main table")
        assertTrue(1393 in mainItems, "Fire battlestaff missing from the Fire giant main table")
    }

    @Test
    fun `noted drops resolve to the 667 noted item id`() {
        // Coins (995) cannot be noted; Rune scimitar (1333) notes to 1334 in this cache.
        val rows =
            listOf(
                BulkNpcDropTables.Row(id = 110, name = "t", main = listOf(BulkNpcDropTables.Drop(item = 1333, min = 1, max = 1, noted = true, slots = 10))),
                BulkNpcDropTables.Row(id = 111, name = "t", main = listOf(BulkNpcDropTables.Drop(item = 995, min = 1, max = 1, noted = true, slots = 10))),
            )
        val result = BulkNpcDropTables.build(rows, definitions)
        assertEquals(1, result.droppedUnnotable)
        assertTrue(110 in result.tables)
        assertTrue(111 !in result.tables, "a table whose only drop cannot be resolved is not registered")
        val built = DropTableBuilder(mockk<Player>(relaxed = true), SecureRandom()).apply(result.tables.getValue(110)).build()
        val item = (built.first { it.name == "main" }.entries.first().drop as gg.rsmod.plugins.content.drops.DropEntry.ItemDrop).item
        assertEquals(1334, item.id)
        assertTrue(definitions.get(ItemDef::class.java, 1334).noted)
    }

    companion object {
        private val TABLE = Paths.get("..", "..", "data", "cfg", "npcs", "drop-tables.json").toFile()

        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            assertNotEquals(0, definitions.getCount(NpcDef::class.java))
            assertNotEquals(0, definitions.getCount(ItemDef::class.java))
            assertTrue(TABLE.exists(), "missing ${TABLE.absolutePath}")
        }
    }
}

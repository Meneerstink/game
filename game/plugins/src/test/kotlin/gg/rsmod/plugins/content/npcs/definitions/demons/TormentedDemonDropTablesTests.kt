package gg.rsmod.plugins.content.npcs.definitions.demons

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.VoidDropTables
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RCV-012 B7 decision 6: all 21 tormented demon ids drop Void's guthix_temple tables, 2011 filter = the 667 cache. */
class TormentedDemonDropTablesTests {
    private val voidData = Paths.get("..", "..", "..", "..", "Donors", "void", "data").toFile()
    private val tomlFile = File(voidData, "area/misthalin/lumbridge/swamp/chasm_of_tears/guthix_temple/guthix_temple.drops.toml")

    private fun parseToml(file: File): Map<String, Pair<Map<String, String>, List<Map<String, String>>>> {
        val out = LinkedHashMap<String, Pair<Map<String, String>, List<Map<String, String>>>>()
        val entry = Regex("""(\w+)\s*=\s*("([^"]*)"|[\w.+-]+)""")
        var name: String? = null
        var props = LinkedHashMap<String, String>()
        var drops = ArrayList<Map<String, String>>()
        fun flush() = name?.let { out[it] = props to drops }
        file.forEachLine { line ->
            val section = Regex("""^\[([^\]]+)]""").find(line)
            when {
                section != null -> { flush(); name = section.groupValues[1]; props = LinkedHashMap(); drops = ArrayList() }
                line.contains("{") && line.contains("}") ->
                    drops += entry.findAll(line.substringAfter("{").substringBeforeLast("}")).associate { m ->
                        m.groupValues[1] to (if (m.groupValues[2].startsWith("\"")) m.groupValues[3] else m.groupValues[2])
                    }
                Regex("""^(type|roll) = """).containsMatchIn(line) -> {
                    val m = entry.find(line)!!
                    props[m.groupValues[1]] = m.groupValues[3].ifEmpty { m.groupValues[2] }
                }
            }
        }
        flush()
        return out
    }

    private fun voidItemIds(): Map<String, Int> {
        val ids = HashMap<String, Int>()
        voidData.walkTopDown().filter { it.name.endsWith(".items.toml") }.forEach { file ->
            var key: String? = null
            file.forEachLine { line ->
                val section = Regex("""^\[([^\].]+)]""").find(line)
                if (section != null) key = section.groupValues[1] else if (key != null && line.startsWith("id = ")) ids.putIfAbsent(key!!, line.removePrefix("id = ").trim().toInt())
            }
        }
        return ids
    }

    @Test
    fun `json equals the Void toml minus the parked clue scrolls`() {
        assertTrue(tomlFile.isFile, "Void guthix_temple drops not found at ${tomlFile.absolutePath}")
        val toml = parseToml(tomlFile)
        val ids = voidItemIds()
        assertEquals(toml.keys, DOC.tables.keys)
        assertEquals(setOf("hard_clue_scroll", "elite_clue_scroll"), DOC.excluded.map { it.entry }.toSet())
        DOC.excluded.forEach { assertEquals("PARKED: Treasure Trails (Q-057)", it.reason) }
        DOC.tables.forEach { (name, table) ->
            val (props, drops) = toml.getValue(name)
            assertEquals(props["type"]?.lowercase() ?: "first", table.type, name)
            assertEquals(props["roll"]?.toInt() ?: 1, table.roll, name)
            val kept = drops.filter { (it["table"] ?: it["id"]) !in DOC.excluded.filter { e -> e.table == name }.map { e -> e.entry } }
            assertEquals(kept.size, table.drops.size, "$name drop count")
            kept.zip(table.drops).forEachIndexed { i, (d, j) ->
                val where = "$name[$i]"
                if (d["table"] != null) {
                    assertEquals(d["table"], j.table, where)
                    assertEquals(d["chance"]?.toInt() ?: -1, j.chance, where)
                } else {
                    assertEquals(d["id"], j.key, where)
                    assertEquals(ids[d["id"]], j.item, "$where item id")
                    val min = d["amount"]?.toInt() ?: d["min"]?.toInt() ?: 1
                    val max = d["amount"]?.toInt() ?: d["max"]?.toInt() ?: 1
                    assertEquals(min to max, j.min to j.max, where)
                    assertEquals(d["chance"]?.toInt() ?: 1, j.chance, where)
                }
            }
        }
        assertEquals(TormentedDemonDrops.NPC_IDS.map { it.toString() }.toSet(), DOC.npcs.keys)
    }

    @Test
    fun `every item exists in the 667 cache as the same unnoted or noted item`() {
        val wrong =
            DOC.tables.values.flatMap { it.drops }.filter { it.table == null }.mapNotNull { d ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, d.item) ?: return@mapNotNull "${d.key} ${d.item} absent from the 667 cache"
                val noted = d.key!!.endsWith("_noted")
                if (def.noted != noted) return@mapNotNull "${d.key} ${d.item} noted=${def.noted}"
                val name = if (def.noted) DEFINITIONS.get(ItemDef::class.java, def.noteLinkId).name else def.name
                val key = d.key!!.removeSuffix("_noted").replace(Regex("[^a-z0-9]"), "")
                val words = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && !it.all(Char::isDigit) }
                val ok = words.all { w -> var i = 0; for (c in key) if (i < w.length && w[i] == c) i++; i == w.length }
                if (ok) null else "${d.key} -> '$name'"
            }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    private fun voidExpectation(name: String, rollOverride: Int?, weight: Double, out: MutableMap<Int, Double>) {
        val table = DOC.tables.getValue(name)
        table.drops.forEach { d ->
            val w = if (table.type == "all") weight else weight * d.chance / (rollOverride ?: table.roll)
            val ref = d.table
            if (ref != null) voidExpectation(ref, d.roll, w, out) else out.merge(d.item, w, Double::plus)
        }
    }

    private fun builtExpectation(builder: DropTableBuilder.() -> Unit): Map<Int, Double> {
        val out = HashMap<Int, Double>()
        fun walk(table: DropTable, weight: Double) {
            val total = table.entries.last().index.toDouble()
            var previous = 0
            table.entries.forEach { e ->
                val w = weight * (e.index - previous) / total
                previous = e.index
                when (val drop = e.drop) {
                    is DropEntry.ItemDrop -> out.merge(drop.item.id, w, Double::plus)
                    is DropEntry.ItemRangeDrop -> out.merge(drop.item.id, w, Double::plus)
                    is DropEntry.TableDrop -> walk(drop.table, w)
                    is DropEntry.MultiDrop -> drop.items.forEach { out.merge(it.id, w, Double::plus) }
                    DropEntry.NothingDrop -> {}
                }
            }
        }
        DropTableBuilder(mockk<Player>(relaxed = true), SecureRandom()).apply(builder).build().forEach { table ->
            if (table.name == "guaranteed") {
                table.entries.forEach { (it.drop as? DropEntry.ItemDrop)?.let { d -> out.merge(d.item.id, 1.0, Double::plus) } }
            } else {
                walk(table, 1.0)
            }
        }
        return out
    }

    @Test
    fun `every demon id reproduces Void's probabilities and is registered`() {
        TormentedDemonDrops.tables(DOC).forEach { (npc, builder) ->
            val expected = HashMap<Int, Double>()
            voidExpectation(DOC.npcs.getValue(npc.toString()), null, 1.0, expected)
            val built = builtExpectation(builder)
            assertEquals(expected.keys, built.keys, "npc $npc item set")
            expected.forEach { (item, p) -> assertTrue(abs(p - built.getValue(item)) < 1e-12, "npc $npc item $item expected $p got ${built[item]}") }
        }
        val demon = builtExpectation(TormentedDemonDrops.tables(DOC).getValue(8349))
        assertEquals(1.0, demon.getValue(592), "ashes always (Void)")
        assertTrue(abs(demon.getValue(14484) - 2.0 / 512) < 1e-12, "dragon claws 2/512")
        TormentedDemonDrops.register(DOC)
        TormentedDemonDrops.NPC_IDS.forEach { assertTrue(DropTableFactory.hasTable(it), "npc $it registered") }
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/demons/tormented_demon.plugin.kts").readText()
        assertTrue(!plugin.contains("DRACONIC_VISAGE") && !plugin.contains("BIG_BONES"), "invented table removed")
    }

    @Test
    fun `print tormented demon animation lengths`() {
        // Evidence for the change/death animation conflict (Void: change 10917, death 10924; Novite 667: death 10917).
        listOf(10917, 10918, 10919, 10922, 10923, 10924).forEach { id ->
            println("TD_ANIM $id cycleLength=${DEFINITIONS.getNullable(AnimDef::class.java, id)?.cycleLength ?: "absent"}")
        }
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary
        private lateinit var DOC: VoidDropTables.Document

        @BeforeClass
        @JvmStatic
        fun load() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
            DOC = VoidDropTables.load(Paths.get("..", "..", "data", "cfg", "npcs", "tormented-demon-drops.json").toFile())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}

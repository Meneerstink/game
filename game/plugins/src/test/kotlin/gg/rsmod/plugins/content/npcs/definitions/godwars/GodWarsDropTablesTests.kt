package gg.rsmod.plugins.content.npcs.definitions.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Npcs
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

/** RCV-011 Q-043-d: GWD generals + bodyguards drop the owner-chosen Void tables, proven over all 16 npcs. */
class GodWarsDropTablesTests {
    private val voidData = Paths.get("..", "..", "..", "..", "Donors", "void", "data").toFile()
    private val gwd = File(voidData, "area/troll_country/god_wars_dungeon")

    private data class TomlTable(val props: Map<String, String>, val drops: List<Map<String, String>>)

    private fun parseToml(files: List<File>): Map<String, TomlTable> {
        val out = LinkedHashMap<String, TomlTable>()
        var name: String? = null
        var props = LinkedHashMap<String, String>()
        var drops = ArrayList<Map<String, String>>()
        val entry = Regex("""(\w+)\s*=\s*("([^"]*)"|[\w.+-]+)""")
        fun flush() {
            name?.let { out[it] = TomlTable(props, drops) }
        }
        files.forEach { file ->
            file.forEachLine { line ->
                if (line.trimStart().startsWith("#")) return@forEachLine
                val section = Regex("""^\[([^\]]+)]""").find(line)
                when {
                    section != null -> {
                        flush()
                        name = section.groupValues[1]
                        props = LinkedHashMap()
                        drops = ArrayList()
                    }
                    line.contains("{") && line.contains("}") ->
                        drops += entry.findAll(line.substringAfter("{").substringBeforeLast("}")).associate { m ->
                            m.groupValues[1] to (if (m.groupValues[3].isNotEmpty() || m.groupValues[2].startsWith("\"")) m.groupValues[3] else m.groupValues[2])
                        }
                    Regex("""^(type|roll) = """).containsMatchIn(line) -> {
                        val m = entry.find(line)!!
                        props[m.groupValues[1]] = m.groupValues[3].ifEmpty { m.groupValues[2] }
                    }
                }
            }
            flush()
            name = null
        }
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
    fun `json tables equal the Void toml minus the recorded exclusions`() {
        assertTrue(gwd.isDirectory, "Void GWD data not found at ${gwd.absolutePath}")
        val toml =
            parseToml(
                listOf("armadyl/armadyl.drops.toml", "bandos/bandos.drops.toml", "saradomin/saradomin.drops.toml", "zamorak/zamorak.drops.toml").map { File(gwd, it) } +
                    File(voidData, "entity/npc/misc.drops.toml"),
            )
        val ids = voidItemIds()
        val allowedReasons = setOf("post-2011 (owner option)", "PARKED: Treasure Trails (Q-057)", "PARKED: Champions Challenge minigame", "OSRS-only row (owner 2026-09-13)")
        DOC.excluded.forEach { assertTrue(it.reason in allowedReasons, "exclusion reason '${it.reason}'") }
        assertEquals(
            setOf("elite_clue_scroll", "hard_clue_scroll", "long_bone", "curved_bone", "goblin_champions_scroll", "manta_ray", "crushed_nest_noted", "zamorakian_spear", "dragon_bolts_e"),
            DOC.excluded.map { it.entry }.toSet(),
        )
        // Owner 2026-09-13 "remove the osrs items": none of them is left in any table.
        val osrsOnly = setOf(391, 6694, 11716, 9244)
        assertTrue(DOC.tables.values.flatMap { it.drops }.none { it.table == null && it.item in osrsOnly }, "OSRS-only rows removed")

        DOC.tables.forEach { (name, table) ->
            val source = toml[name] ?: error("table $name not in Void")
            assertEquals(source.props["type"]?.lowercase() ?: "first", table.type, name)
            assertEquals(source.props["roll"]?.toInt() ?: 1, table.roll, name)
            val excludedHere = DOC.excluded.filter { it.table == name }.map { it.entry }.toMutableList()
            val kept =
                source.drops.filter { d ->
                    val entry = d["table"] ?: d["id"]
                    if (entry in excludedHere) {
                        excludedHere.remove(entry)
                        false
                    } else {
                        true
                    }
                }
            assertTrue(excludedHere.isEmpty(), "$name: recorded exclusions not present in Void: $excludedHere")
            assertEquals(kept.size, table.drops.size, "$name drop count")
            kept.zip(table.drops).forEachIndexed { index, (d, j) ->
                val where = "$name[$index]"
                if (d["table"] != null) {
                    assertEquals(d["table"], j.table, where)
                    assertEquals(d["chance"]?.toInt() ?: -1, j.chance, where)
                    assertEquals(d["roll"]?.toInt(), j.roll, where)
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

        // npc -> table: generals by name, bodyguards by their Void npcs.toml drop_table.
        val guards = HashMap<String, String>()
        listOf("armadyl/armadyl.npcs.toml", "bandos/bandos.npcs.toml", "saradomin/saradomin.npcs.toml", "zamorak/zamorak.npcs.toml").forEach { path ->
            var npc: String? = null
            File(gwd, path).forEachLine { line ->
                Regex("""^\[([^\].]+)]""").find(line)?.let { npc = it.groupValues[1] }
                Regex("""^drop_table = "(\w+)"""").find(line)?.let { guards[npc!!] = it.groupValues[1] + "_drop_table" }
            }
        }
        val names =
            mapOf(
                Npcs.GENERAL_GRAARDOR to "general_graardor", Npcs.KREEARRA to "kree_arra", Npcs.COMMANDER_ZILYANA to "commander_zilyana", Npcs.KRIL_TSUTSAROTH to "kril_tsutsaroth",
                Npcs.SERGEANT_STRONGSTACK to "sergeant_strongstack", Npcs.SERGEANT_STEELWILL to "sergeant_steelwill", Npcs.SERGEANT_GRIMSPIKE to "sergeant_grimspike",
                Npcs.FLIGHT_KILISA to "flight_kilisa", Npcs.WINGMAN_SKREE to "wingman_skree", Npcs.FLOCKLEADER_GEERIN to "flockleader_geerin",
                Npcs.STARLIGHT to "starlight", Npcs.GROWLER to "growler", Npcs.BREE to "bree",
                Npcs.TSTANON_KARLAK to "tstanon_karlak", Npcs.ZAKLN_GRITCH to "zakln_gritch", Npcs.BALFRUG_KREEYATH to "balfrug_kreeyath",
            )
        assertEquals(GodWarsDrops.NPC_IDS.map { it.toString() }.toSet(), DOC.npcs.keys)
        names.forEach { (id, voidName) ->
            assertEquals(guards[voidName] ?: "${voidName}_drop_table", DOC.npcs[id.toString()], "npc $id ($voidName)")
        }
    }

    @Test
    fun `every item is the same unnoted or noted 667 item`() {
        val wrong =
            DOC.tables.values.flatMap { it.drops }.filter { it.table == null }.mapNotNull { d ->
                val def = DEFINITIONS.getNullable(ItemDef::class.java, d.item) ?: return@mapNotNull "${d.key} ${d.item} absent"
                val itemKey = d.key!!
                val noted = itemKey.endsWith("_noted")
                if (def.noted != noted) return@mapNotNull "$itemKey ${d.item} noted=${def.noted}"
                val name = if (def.noted) DEFINITIONS.get(ItemDef::class.java, def.noteLinkId).name else def.name
                val key = itemKey.removeSuffix("_noted").replace(Regex("[^a-z0-9]"), "")
                val words = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && !it.all(Char::isDigit) }
                val ok = words.all { w -> var i = 0; for (c in key) if (i < w.length && w[i] == c) i++; i == w.length }
                if (ok) null else "${d.key} -> '$name'"
            }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    /** Expected count per item id per kill under Void's rules, read from the JSON. */
    private fun voidExpectation(
        name: String,
        rollOverride: Int?,
        weight: Double,
        out: MutableMap<Int, Double>,
    ) {
        val table = DOC.tables.getValue(name)
        if (table.type == "all") {
            table.drops.forEach { d ->
                val ref = d.table
                if (ref != null) voidExpectation(ref, d.roll, weight, out) else out.merge(d.item, weight, Double::plus)
            }
        } else {
            val roll = rollOverride ?: table.roll
            table.drops.forEach { d ->
                val w = weight * d.chance / roll
                val ref = d.table
                if (ref != null) voidExpectation(ref, d.roll, w, out) else out.merge(d.item, w, Double::plus)
            }
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
                assertEquals(table.entries.last().index, table.entries.last().index)
                walk(table, 1.0)
            }
        }
        return out
    }

    @Test
    fun `every npc's built table reproduces Void's probabilities exactly`() {
        GodWarsDrops.tables(DOC).forEach { (npc, builder) ->
            val expected = HashMap<Int, Double>()
            voidExpectation(DOC.npcs.getValue(npc.toString()), null, 1.0, expected)
            GodWarsDrops.LEGACY_FROZEN_KEY_PIECE[npc]?.let { expected.merge(it, GodWarsDrops.LEGACY_PIECE_SLOTS.toDouble() / GodWarsDrops.LEGACY_PIECE_TOTAL, Double::plus) }
            val built = builtExpectation(builder)
            assertEquals(expected.keys, built.keys, "npc $npc item set")
            expected.forEach { (item, p) -> assertTrue(abs(p - built.getValue(item)) < 1e-12, "npc $npc item $item expected $p got ${built[item]}") }
        }
        // Spot checks straight from the Void text.
        val graardor = builtExpectation(GodWarsDrops.tables(DOC).getValue(Npcs.GENERAL_GRAARDOR))
        assertTrue(abs(graardor.getValue(11724) - 2.0 / 762) < 1e-12, "bandos chestplate 2/762")
        assertTrue(abs(graardor.getValue(11704) - 1.0 / 762) < 1e-12, "bandos hilt 1/762")
        assertEquals(1.0, graardor.getValue(532), "big bones always")
    }

    @Test
    fun `placeholder tables are gone and only the GWD drop plugin binds these deaths`() {
        GodWarsDrops.register(DOC)
        GodWarsDrops.NPC_IDS.forEach { assertTrue(DropTableFactory.hasTable(it), "npc $it registered, so bulk rows are skipped") }
        val content = File("src/main/kotlin/gg/rsmod/plugins/content")
        listOf("general_graardor", "kreearra", "commander_zilyana", "kril_tsutsaroth").forEach {
            val text = File(content, "npcs/definitions/godwars/$it.plugin.kts").readText()
            assertTrue(!text.contains("table.register") && !text.contains("on_npc_death"), "$it still has its placeholder table")
        }
        val binders =
            content.walkTopDown().filter { it.isFile && it.name.endsWith(".kts") }.filter { file ->
                val text = file.readText()
                listOf("GENERAL_GRAARDOR", "KREEARRA", "COMMANDER_ZILYANA", "KRIL_TSUTSAROTH", "SERGEANT_", "WINGMAN_SKREE", "FLOCKLEADER_GEERIN", "FLIGHT_KILISA", "STARLIGHT", "GROWLER", "BREE", "BALFRUG_KREEYATH", "TSTANON_KARLAK", "ZAKLN_GRITCH")
                    .any { Regex("""on_npc_death\([^)]*\b$it""").containsMatchIn(text) }
            }.map { it.name }.toList()
        assertEquals(emptyList(), binders, "no other on_npc_death binding for the GWD roster")
        assertTrue(File(content, "npcs/definitions/godwars/godwars_drops.plugin.kts").readText().contains("GodWarsDrops.NPC_IDS.forEach"))
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
            DOC = VoidDropTables.load(Paths.get("..", "..", "data", "cfg", "npcs", "godwars-drops.json").toFile())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}

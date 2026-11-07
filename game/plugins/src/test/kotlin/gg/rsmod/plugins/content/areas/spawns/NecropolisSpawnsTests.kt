package gg.rsmod.plugins.content.areas.spawns

import gg.rsmod.plugins.api.cfg.Npcs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N462: only the four imported exterior squares; pending imports are documented in the dossier. */
class NecropolisSpawnsTests {
    private val regions = setOf(13354, 13355, 13610, 13611)
    private val directory = File("src/main/kotlin/gg/rsmod/plugins/content/areas/spawns")
    private val legacyIds = setOf(1993, 2803, 2804, 2805, 2806, 3675, 6773, 6774, 6776, 6777, 6778, 6779, 6786, 6787)
    private val calls = Regex("""\bspawn_npc\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)

    private fun sources(): List<Pair<Int, String>> {
        assertTrue(directory.isDirectory, "Region spawn source directory must exist")
        return regions.mapNotNull { region ->
            val file = File(directory, "spawns_$region.plugin.kts")
            if (!file.exists()) null else region to file.readText()
                .replace(Regex("""/\*.*?\*/|//[^\r\n]*""", RegexOption.DOT_MATCHES_ALL), "")
        }.also { assertTrue(it.isNotEmpty(), "Do not silently scan an empty source directory") }
    }

    @Test
    fun `the four OSRS squares contain no legacy monsters or lookalike substitutes`() {
        sources().forEach { (region, source) ->
            // Resolve symbols AND numeric ids, including calls inside coordinate loops.
            calls.findAll(source).forEach { match ->
                val value = Regex("""\bnpc\s*=\s*(Npcs\.\w+|\d+)\s*[,)]?""")
                    .find(match.groupValues[1])?.groupValues?.get(1)
                assertTrue(value != null, "$region: NPC id must be auditable")
                val id = value!!.toIntOrNull()
                    ?: Npcs::class.java.getField(value.removePrefix("Npcs.")).getInt(null)
                assertTrue(id !in legacyIds, "$region: legacy NPC $id needs the OSRS import, not a substitute")
            }
        }
    }

    @Test
    fun `every restored spawn has an explicit tile in its square and no tile is duplicated`() {
        val tiles = mutableSetOf<Triple<Int, Int, Int>>()
        sources().forEach { (region, source) ->
            calls.findAll(source).forEach { match ->
                fun field(name: String, default: Int? = null): Int {
                    val expression = Regex("""\b$name\s*=\s*([^,\r\n]+)""").find(match.groupValues[1])?.groupValues?.get(1)?.trim()
                    if (expression == null && default != null) return default
                    val value = expression?.toIntOrNull()
                    assertTrue(value != null, "$region: $name must be an explicit tile coordinate so no spawn is skipped")
                    return value!!
                }
                val x = field("x")
                val z = field("z")
                val plane = field("height", 0)
                assertEquals(region, ((x shr 6) shl 8) or (z shr 6), "Spawn escaped its region")
                assertTrue(plane in 0..3, "Invalid plane $plane")
                assertTrue(tiles.add(Triple(x, z, plane)), "Duplicate Necropolis spawn at $x,$z,$plane")
            }
        }
    }
}

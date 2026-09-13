package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.plugins.api.cfg.Npcs
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-011 Q-043-a: bodyguards belong to their general in all four chambers (Void + Novite), proven per chamber. */
class GodWarsBodyguardsTests {
    private val voidScripts = Paths.get("..", "..", "..", "..", "Donors", "void", "game", "src", "main", "kotlin", "content", "area", "troll_country", "god_wars_dungeon").toFile()

    private val voidNames =
        mapOf(
            "sergeant_strongstack" to Npcs.SERGEANT_STRONGSTACK, "sergeant_steelwill" to Npcs.SERGEANT_STEELWILL,
            "sergeant_grimspike" to Npcs.SERGEANT_GRIMSPIKE, "flight_kilisa" to Npcs.FLIGHT_KILISA,
            "wingman_skree" to Npcs.WINGMAN_SKREE, "flockleader_geerin" to Npcs.FLOCKLEADER_GEERIN,
            "starlight" to Npcs.STARLIGHT, "bree" to Npcs.BREE, "growler" to Npcs.GROWLER,
            "balfrug_kreeyath" to Npcs.BALFRUG_KREEYATH, "tstanon_karlak" to Npcs.TSTANON_KARLAK, "zakln_gritch" to Npcs.ZAKLN_GRITCH,
        )

    @Test
    fun `roster equals the bodyguards Void adds on each general's spawn`() {
        assertTrue(voidScripts.isDirectory, "Void GWD scripts not found at ${voidScripts.absolutePath}")
        val scripts =
            mapOf(
                Npcs.GENERAL_GRAARDOR to "bandos/GeneralGraardor.kt",
                Npcs.KREEARRA to "armadyl/KreeArra.kt",
                Npcs.COMMANDER_ZILYANA to "saradomin/CommanderZilyana.kt",
                Npcs.KRIL_TSUTSAROTH to "zamorak/KrilTsutsaroth.kt",
            )
        val add = Regex("""NPCs\.add\("(\w+)", Tile\((\d+), (\d+)(?:, (\d+))?\)\)""")
        assertEquals(scripts.keys, GodWarsBodyguards.BY_GENERAL.keys)
        scripts.forEach { (general, path) ->
            val text = File(voidScripts, path).readText()
            assertTrue(text.contains("npcSpawn("), "$path adds bodyguards on the general's spawn")
            val fromVoid =
                add.findAll(text).map { m ->
                    GodWarsBodyguards.Bodyguard(voidNames.getValue(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].ifEmpty { "0" }.toInt())
                }.toSet()
            assertEquals(3, fromVoid.size, path)
            assertEquals(fromVoid, GodWarsBodyguards.BY_GENERAL.getValue(general).toSet(), path)
        }
        val all = GodWarsBodyguards.BY_GENERAL.values.flatten().map { it.id }
        assertEquals(12, all.toSet().size, "each bodyguard belongs to one general")
        assertTrue(all.all { it in GodWars.GENERAL_HUNTERS }, "all bodyguards hunt as generals (Void hunt_mode aggressive)")
    }

    @Test
    fun `only absent bodyguards are added, for every chamber`() {
        GodWarsBodyguards.BY_GENERAL.forEach { (general, guards) ->
            assertEquals(guards, GodWarsBodyguards.missing(general) { false }, "empty chamber: all three")
            assertEquals(emptyList(), GodWarsBodyguards.missing(general) { true }, "full chamber: none")
            guards.forEach { alive ->
                assertEquals(guards - alive, GodWarsBodyguards.missing(general) { it == alive.id }, "$general with ${alive.id} alive")
            }
        }
        assertEquals(emptyList(), GodWarsBodyguards.missing(Npcs.NEX) { false }, "Nex has no general bodyguards here")
    }

    @Test
    fun `bodyguards are no longer independent world spawns and generals respawn them`() {
        val content = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content").toFile()
        val plugin = File(content, "areas/godwars/godwars_generals.plugin.kts").readText()
        assertFalse(plugin.contains("spawn_npc("), "no independent bodyguard spawns")
        assertTrue(plugin.contains("GodWarsBodyguards.BY_GENERAL") && plugin.contains("on_npc_spawn(") && plugin.contains("respawnOverride = false"))
        val defs = File(content, "npcs/definitions/godwars")
        listOf("general_graardor.plugin.kts", "commander_zilyana.plugin.kts").forEach {
            assertTrue(File(defs, it).readText().contains("respawnDelay = 150"), "$it: Void and Novite both 150")
        }
    }
}

package gg.rsmod.plugins.content.npcs

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Q-042: the Dagannoth Kings equal the OSRS Wiki infoboxes (raw wikitext 2026-09-14). */
class DagannothKingsOsrsTests {
    private val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/dagannoth/dagannoth_kings.plugin.kts").readText()

    @Test
    fun `definitions carry the wiki speed, respawn, levels and defences`() {
        listOf(
            "val KING_ATTACK_SPEED = 4", "val KING_RESPAWN_TICKS = 150", "hitpoints = 2550", "attack = 255", "strength = 255",
            "King(Npcs.DAGANNOTH_SUPREME, StyleType.RANGED, 2855, 2854, defence = 128, ranged = 255, magic = 255, defenceMelee = 10, defenceMagic = 255, defenceRanged = 550)",
            "King(Npcs.DAGANNOTH_PRIME, StyleType.MAGIC, 2854, 2852, defence = 255, ranged = 0, magic = 255, defenceMelee = 255, defenceMagic = 255, defenceRanged = 10)",
            "King(Npcs.DAGANNOTH_REX, StyleType.SLASH, 2853, 2854, defence = 255, ranged = 255, magic = 0, defenceMelee = 255, defenceMagic = 10, defenceRanged = 255)",
        ).forEach { assertTrue(it in plugin, it) }
    }

    @Test
    fun `live attack table max hits equal the wiki`() {
        val rows = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json"))
        val expected = mapOf(2881 to 300, 2882 to 500, 2883 to 260)
        expected.forEach { (id, max) ->
            val row = rows.first { it["id"].asInt() == id }
            val hits = row["attacks"].flatMap { a -> a["hits"].map { it["max"].asInt() } }
            assertEquals(listOf(max), hits, "npc $id")
        }
    }
}

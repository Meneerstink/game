package gg.rsmod.plugins.content.npcs

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Q-041: the King Black Dragon equals the OSRS Wiki infobox (raw wikitext 2026-09-14). */
class KingBlackDragonOsrsTests {
    @Test
    fun `definition carries the wiki levels, defences, speed and respawn`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/dragons/king_black_dragon_level_276.plugin.kts").readText()
        val def = plugin.substringAfter("set_combat_def(npc = KBD)")
        listOf(
            "attackSpeed = 4", "respawnDelay = 16", "hitpoints = 2400", "attack = 240", "strength = 240", "defence = 240", "magic = 240",
            "ranged = 1", "defenceStab = 40", "defenceSlash = 90", "defenceCrush = 90", "defenceMagic = 80", "defenceRanged = 70",
        ).forEach { assertTrue(it in def, it) }
    }

    @Test
    fun `live attack table carries the wiki max hits and poison`() {
        val row = ObjectMapper().readTree(File("../../data/cfg/npcs/npc-attacks.json")).first { it["id"].asInt() == 50 }
        val attacks = row["attacks"].associateBy { it["id"].asText() }
        assertEquals(250, attacks.getValue("melee")["hits"][0]["max"].asInt())
        assertEquals(650, attacks.getValue("dragonfire")["hits"][0]["max"].asInt())
        assertEquals(80, attacks.getValue("toxic")["poison"].asInt(), "poison 8 (NpcAttacks divides by 10)")
    }
}

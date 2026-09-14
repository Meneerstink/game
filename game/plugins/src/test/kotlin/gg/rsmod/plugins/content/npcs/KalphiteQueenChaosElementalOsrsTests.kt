package gg.rsmod.plugins.content.npcs

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Q-041: Kalphite Queen and Chaos Elemental equal the OSRS Wiki infoboxes (raw wikitext 2026-09-14). */
class KalphiteQueenChaosElementalOsrsTests {
    @Test
    fun `Kalphite Queen forms carry the crawling and airborne defences`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/kalphite_queen.plugin.kts").readText()
        val first = plugin.substringAfter("set_combat_def(npc = FIRST_FORM)").substringBefore("set_combat_def(npc = SECOND_FORM)")
        val second = plugin.substringAfter("set_combat_def(npc = SECOND_FORM)")
        listOf("defenceStab = 50", "defenceSlash = 50", "defenceCrush = 10", "defenceMagic = 100", "defenceRanged = 100")
            .forEach { assertTrue(it in first, "crawling $it") }
        listOf("defenceStab = 100", "defenceSlash = 100", "defenceCrush = 100", "defenceMagic = 10", "defenceRanged = 10")
            .forEach { assertTrue(it in second, "airborne $it") }
        listOf(first, second).forEach { form ->
            listOf("attackSpeed = 4", "hitpoints = 2550", "attack = 300", "strength = 300", "defence = 300", "magic = 150", "ranged = 1")
                .forEach { assertTrue(it in form, it) }
        }
        assertTrue("val RESPAWN_TICKS = 50" in plugin)
    }

    @Test
    fun `Chaos Elemental bulk definition equals the wiki`() {
        val def = ObjectMapper().readTree(File("../../data/cfg/npcs/combat-defs.json")).first { it["id"].asInt() == 3200 }
        assertEquals(2500, def["lifepoints"].asInt())
        listOf("attack", "strength", "defence", "magic", "ranged").forEach { assertEquals(270, def[it].asInt(), it) }
        assertEquals(4, def["attack_speed"].asInt())
        assertEquals(28, def["max_hit"].asInt())
        assertEquals(14, def["respawn_delay"].asInt())
        val bonuses = def["bonuses"]
        listOf("defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged").forEach { assertEquals(70, bonuses[it].asInt(), it) }
        assertEquals(0, bonuses["strength_bonus"].asInt())
    }
}

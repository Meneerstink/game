package gg.rsmod.plugins.content.areas.godwars

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Q-043-f: the 2011 faction gates ignore boosts (RuneScape Wiki "God Wars Dungeon" revision 2011-12-30, quoted in
 * [GodWars.hasUnboostedLevel]); the Trollheim boulder allows boosts. Roster over every level gate in the dungeon plugin.
 */
class GodWarsUnboostedGatesTests {
    private val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/godwars_dungeon.plugin.kts").readText()

    @Test
    fun `the helper reads the base level`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/GodWars.kt").readText()
        assertTrue("fun hasUnboostedLevel(player: Player, skill: Int, level: Int): Boolean = player.skills.getMaxLevel(skill) >= level" in source)
    }

    @Test
    fun `Bandos Strength, Saradomin Agility and Armadyl Ranged gates use the base level`() {
        val unboosted = Regex("""GodWars\.hasUnboostedLevel\(player, Skills\.(\w+), 70\)""").findAll(plugin).map { it.groupValues[1] }.toList()
        assertEquals(listOf("STRENGTH", "AGILITY", "AGILITY", "RANGED"), unboosted)
        val boostable = Regex("""getCurrentLevel\(Skills\.(\w+)\) < (\d+)""").findAll(plugin).map { "${it.groupValues[1]} ${it.groupValues[2]}" }.toList()
        // Boulder 60 Strength (boosts allowed), crevice 60 Agility and the Ancient Prison pipe 70 Agility (no 2011 no-boost statement).
        assertEquals(listOf("STRENGTH 60", "AGILITY 60", "AGILITY 60", "AGILITY 70"), boostable)
    }
}

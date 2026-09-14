package gg.rsmod.plugins.content.areas.watson

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Owner answer Q10: The Mimic's combat definition equals the OSRS Wiki infobox (id 8633) field by field. */
class TheMimicCombatDefTests {
    @Test
    fun `every sourced infobox value is in the combat definition`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/watson/the_mimic_combat.plugin.kts").readText()
        listOf(
            "set_combat_def(Npcs.THE_MIMIC_14402)",
            "respawnDelay = 0", "attackSpeed = 3", "poisonImmune = true", "attackStyle = StyleType.CRUSH", "neverAggro()",
            "hitpoints = 230", "attack = 185", "strength = 120", "defence = 120", "magic = 60", "ranged = 1",
            "attackBonus = 135", "strengthBonus = 48", "attackMagic = 180", "attackRanged = 0",
            "defenceStab = 160", "defenceSlash = 165", "defenceCrush = 150", "defenceMagic = 30", "defenceRanged = 145",
            "attack = 15404", "death = 15406",
        ).forEach { assertTrue(it in plugin, it) }
    }
}

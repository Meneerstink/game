package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "potions-combat" against the OSRS Wiki potion pages. */
class OsrsPotionsCombatImportTests {
    @Test
    fun `boost formulas follow the wiki at every level`() {
        (1..99).forEach { level ->
            assertEquals(kotlin.math.floor(level * 15.0 / 100).toInt() + 5, DivinePotions.superBoost(level), "super boost at $level")
            assertEquals(kotlin.math.floor(level / 10.0).toInt() + 4, DivinePotions.rangedBoost(level), "ranged boost at $level")
        }
        assertEquals(19, DivinePotions.boostFor(Skills.ATTACK, 99))
        assertEquals(13, DivinePotions.boostFor(Skills.RANGED, 99))
        assertEquals(4, DivinePotions.boostFor(Skills.MAGIC, 99), "battlemage / divine magic: +4")
        assertEquals(500, DivinePotions.DURATION_TICKS, "5 minutes")
        assertEquals(10, DivinePotions.HITPOINT_COST)
        assertEquals("You need more than 10 hitpoints to survive the power of a divine potion.", DivinePotions.REFUSAL_MESSAGE)
    }

    @Test
    fun `every dose drinks in order and ends in a vial`() {
        val families =
            mapOf(
                PotionType.SUPER_COMBAT to listOf(Items.SUPER_COMBAT_POTION_4, Items.SUPER_COMBAT_POTION_3, Items.SUPER_COMBAT_POTION_2, Items.SUPER_COMBAT_POTION_1),
                PotionType.DIVINE_SUPER_COMBAT to listOf(Items.DIVINE_SUPER_COMBAT_POTION_4, Items.DIVINE_SUPER_COMBAT_POTION_3, Items.DIVINE_SUPER_COMBAT_POTION_2, Items.DIVINE_SUPER_COMBAT_POTION_1),
                PotionType.DIVINE_SUPER_ATTACK to listOf(Items.DIVINE_SUPER_ATTACK_POTION_4, Items.DIVINE_SUPER_ATTACK_POTION_3, Items.DIVINE_SUPER_ATTACK_POTION_2, Items.DIVINE_SUPER_ATTACK_POTION_1),
                PotionType.DIVINE_SUPER_STRENGTH to listOf(Items.DIVINE_SUPER_STRENGTH_POTION_4, Items.DIVINE_SUPER_STRENGTH_POTION_3, Items.DIVINE_SUPER_STRENGTH_POTION_2, Items.DIVINE_SUPER_STRENGTH_POTION_1),
                PotionType.DIVINE_SUPER_DEFENCE to listOf(Items.DIVINE_SUPER_DEFENCE_POTION_4, Items.DIVINE_SUPER_DEFENCE_POTION_3, Items.DIVINE_SUPER_DEFENCE_POTION_2, Items.DIVINE_SUPER_DEFENCE_POTION_1),
                PotionType.DIVINE_RANGING to listOf(Items.DIVINE_RANGING_POTION_4, Items.DIVINE_RANGING_POTION_3, Items.DIVINE_RANGING_POTION_2, Items.DIVINE_RANGING_POTION_1),
                PotionType.DIVINE_MAGIC to listOf(Items.DIVINE_MAGIC_POTION_4, Items.DIVINE_MAGIC_POTION_3, Items.DIVINE_MAGIC_POTION_2, Items.DIVINE_MAGIC_POTION_1),
                PotionType.BATTLEMAGE to listOf(Items.BATTLEMAGE_POTION_4, Items.BATTLEMAGE_POTION_3, Items.BATTLEMAGE_POTION_2, Items.BATTLEMAGE_POTION_1),
                PotionType.BASTION to listOf(Items.BASTION_POTION_4, Items.BASTION_POTION_3, Items.BASTION_POTION_2, Items.BASTION_POTION_1),
                PotionType.DIVINE_BATTLEMAGE to listOf(Items.DIVINE_BATTLEMAGE_POTION_4, Items.DIVINE_BATTLEMAGE_POTION_3, Items.DIVINE_BATTLEMAGE_POTION_2, Items.DIVINE_BATTLEMAGE_POTION_1),
                PotionType.DIVINE_BASTION to listOf(Items.DIVINE_BASTION_POTION_4, Items.DIVINE_BASTION_POTION_3, Items.DIVINE_BASTION_POTION_2, Items.DIVINE_BASTION_POTION_1),
            )
        families.forEach { (type, doses) ->
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType, "$id type")
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement, "$id replacement")
            }
        }
        assertEquals(5, DivinePotions.TIMERS.size, "Attack, Strength, Defence, Ranged, Magic")
    }

    @Test
    fun `divine protection, expiry and the super combat recipe are wired`() {
        val restore = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/restoration/stat_restoration.plugin.kts").readText()
        assertTrue("DivinePotions.protects(player, index)" in restore)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/divine_potions.plugin.kts").readText()
        assertTrue("DivinePotions.expire(player, skill)" in plugin && "Skills.HERBLORE) < 90" in plugin && "addXp(Skills.HERBLORE, 150.0)" in plugin)
    }
}

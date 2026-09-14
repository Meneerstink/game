package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.herblore.mixing.PotionData
import java.io.File
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "potions-brews" against the OSRS Wiki brew pages and RuneLite's varbit timers. */
class OsrsPotionsBrewsImportTests {
    @Test
    fun `brew formulas follow the wiki at every level`() {
        (1..99).forEach { level ->
            assertEquals(floor(level / 20.0).toInt() + 2, BrewPotions.ancientMagicBoost(level), "ancient Magic at $level")
            assertEquals(floor(level * 8 / 100.0).toInt() + 3, BrewPotions.forgottenMagicBoost(level), "forgotten Magic at $level")
            assertEquals(floor(level / 10.0).toInt() + 2, BrewPotions.prayerRestore(level), "Prayer restore at $level")
            assertEquals(floor(level * 5 / 100.0).toInt(), BrewPotions.prayerOvercap(level), "5 % over base, rounded down, at $level")
            assertEquals(floor(level / 10.0).toInt() + 2, BrewPotions.drain(level), "drain at $level")
            assertEquals(floor(level / 10.0).toInt() + 4, BrewPotions.armadylRangedBoost(level), "Armadyl Ranged at $level")
            assertEquals(floor(level * 16 / 100.0).toInt() + 6, BrewPotions.menaphiteRestore(level), "remedy restore at $level")
        }
        assertEquals(11, BrewPotions.armadylHeal(99))
        assertEquals(mapOf(1 to 36.0, 2 to 72.0, 3 to 108.0, 4 to 145.0), BrewPotions.FORGOTTEN_BREW_EXPERIENCE)
        assertEquals(25 to 20, BrewPotions.MENAPHITE_INTERVAL to BrewPotions.MENAPHITE_CYCLES, "every 15 seconds for 5 minutes")
        assertEquals(12 to 66, BrewPotions.PRAYER_REGEN_INTERVAL to BrewPotions.PRAYER_REGEN_CYCLES, "one point every 12 ticks, 66 in total")
        assertEquals(500 to 25, BrewPotions.SURGE_COOLDOWN_TICKS to BrewPotions.SURGE_ENERGY, "25 % energy, once every five minutes")
        assertEquals("You now feel capable of drinking another dose of surge potion.", BrewPotions.SURGE_READY_MESSAGE)
        assertEquals("Drinking this would have no effect.", BrewPotions.SURGE_NO_EFFECT_MESSAGE)
        val mix = PotionData.ANCIENT_MIX
        assertEquals(Items.ANCIENT_BREW_2 to Items.CAVIAR, mix.primary to mix.secondary)
        assertEquals(92 to 63.0, mix.levelRequirement to mix.experience)
    }

    @Test
    fun `every dose drinks in order and timers and recipe are wired`() {
        mapOf(
            PotionType.ANCIENT_BREW to listOf(Items.ANCIENT_BREW_4, Items.ANCIENT_BREW_3, Items.ANCIENT_BREW_2, Items.ANCIENT_BREW_1),
            PotionType.ANCIENT_MIX to listOf(Items.ANCIENT_MIX_2, Items.ANCIENT_MIX_1),
            PotionType.FORGOTTEN_BREW to listOf(Items.FORGOTTEN_BREW_4, Items.FORGOTTEN_BREW_3, Items.FORGOTTEN_BREW_2, Items.FORGOTTEN_BREW_1),
            PotionType.ARMADYL_BREW to listOf(Items.ARMADYL_BREW_4, Items.ARMADYL_BREW_3, Items.ARMADYL_BREW_2, Items.ARMADYL_BREW_1),
            PotionType.MENAPHITE_REMEDY to listOf(Items.MENAPHITE_REMEDY_4, Items.MENAPHITE_REMEDY_3, Items.MENAPHITE_REMEDY_2, Items.MENAPHITE_REMEDY_1),
            PotionType.PRAYER_REGENERATION to
                listOf(Items.PRAYER_REGENERATION_POTION_4, Items.PRAYER_REGENERATION_POTION_3, Items.PRAYER_REGENERATION_POTION_2, Items.PRAYER_REGENERATION_POTION_1),
            PotionType.SURGE to listOf(Items.SURGE_POTION_4, Items.SURGE_POTION_3, Items.SURGE_POTION_2, Items.SURGE_POTION_1),
            PotionType.GOADING to listOf(Items.GOADING_POTION_4, Items.GOADING_POTION_3, Items.GOADING_POTION_2, Items.GOADING_POTION_1),
        ).forEach { (type, doses) ->
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType)
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement)
            }
        }
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/brew_potions.plugin.kts").readText()
        assertTrue("on_timer(BrewPotions.MENAPHITE_TIMER)" in plugin && "on_timer(BrewPotions.PRAYER_REGEN_TIMER)" in plugin && "on_timer(BrewPotions.SURGE_COOLDOWN)" in plugin)
        assertTrue("val essence = 20 * doses" in plugin && "Skills.HERBLORE) < 91" in plugin)
        assertEquals(listOf(6, 60, 4), listOf(BrewPotions.GOADING_INTERVAL, BrewPotions.GOADING_CYCLES, BrewPotions.GOADING_RADIUS), "6-tick cycle, 6 minutes, 9x9")
        listOf("npc.combatDef.slayerReq > player.skills.getMaxLevel(Skills.SLAYER)", "if (!multi) return@on_timer", "npc.getCombatTarget() != null", "world.collision.raycast(npc.tile, player.tile, projectile = true)")
            .forEach { assertTrue(it in plugin, it) }
        assertTrue("DivinePotions.TIMERS.values.forEach { p.timers.remove(it) }" in File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/BrewPotions.kt").readText())
    }
}

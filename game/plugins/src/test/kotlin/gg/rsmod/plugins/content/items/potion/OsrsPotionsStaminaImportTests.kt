package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.herblore.mixing.PotionData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "potions-stamina" against the OSRS Wiki stamina and energy potion pages. */
class OsrsPotionsStaminaImportTests {
    @Test
    fun `stamina values follow the wiki`() {
        assertEquals(200, StaminaPotions.STAMINA_TICKS, "2 minutes")
        assertEquals(400, StaminaPotions.EXTENDED_STAMINA_TICKS, "4 minutes")
        assertEquals(0.3, StaminaPotions.DEPLETION_MULTIPLIER, "a 70% reduction in run energy depletion")
        assertEquals(20.0, StaminaPotions.STAMINA_RESTORE)
        assertEquals(40.0, StaminaPotions.EXTENDED_RESTORE)
        assertEquals(6, StaminaPotions.MIX_HEAL)
        assertEquals("You drink the lumpy potion", PotionType.STAMINA_MIX.message)
        val mix = PotionData.STAMINA_MIX
        assertEquals(Items.STAMINA_POTION_2 to Items.CAVIAR, mix.primary to mix.secondary)
        assertEquals(86 to 60.0, mix.levelRequirement to mix.experience)
    }

    @Test
    fun `every dose drinks in order and the drain hook and recipe are wired`() {
        mapOf(
            PotionType.STAMINA to listOf(Items.STAMINA_POTION_4, Items.STAMINA_POTION_3, Items.STAMINA_POTION_2, Items.STAMINA_POTION_1),
            PotionType.STAMINA_MIX to listOf(Items.STAMINA_MIX_2, Items.STAMINA_MIX_1),
            PotionType.EXTENDED_STAMINA to listOf(Items.EXTENDED_STAMINA_POTION_4, Items.EXTENDED_STAMINA_POTION_3, Items.EXTENDED_STAMINA_POTION_2, Items.EXTENDED_STAMINA_POTION_1),
            PotionType.EXTREME_ENERGY to listOf(Items.EXTREME_ENERGY_POTION_4, Items.EXTREME_ENERGY_POTION_3, Items.EXTREME_ENERGY_POTION_2, Items.EXTREME_ENERGY_POTION_1),
        ).forEach { (type, doses) ->
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType)
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement)
            }
        }
        assertTrue("StaminaPotions.drainMultiplier(p)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/run/RunEnergy.kt").readText())
        val recipe = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/stamina_recipes.plugin.kts").readText()
        assertTrue("Skills.HERBLORE) < 77" in recipe && "25.5 * doses" in recipe && "item1 = Items.AMYLASE_CRYSTAL" in recipe)
    }
}

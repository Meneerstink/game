package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "potions-venom" against the OSRS Wiki pages and the RuneLite poison counter model. */
class OsrsPotionsVenomImportTests {
    /** RuneLite TimersAndBuffsPlugin: poison immunity |(v+1)x30| and venom immunity |(v+39)x30|, each plus up to one 30-tick cycle. */
    private fun poisonRange(v: Int) = Math.abs((v + 1) * 30)..(Math.abs((v + 1) * 30) + 30)

    private fun venomRange(v: Int) = Math.abs((v + 39) * 30)..(Math.abs((v + 39) * 30) + 30)

    @Test
    fun `derived counter values reproduce every wiki duration`() {
        assertTrue(1200 in poisonRange(-40) && 30..60 == venomRange(-40), "antidote++: 12 minutes, venom 18-36 seconds")
        assertTrue(1200 in poisonRange(-41) && 60..90 == venomRange(-41), "anti-venom: 12 minutes, venom 36-54 seconds")
        assertTrue(1500 in poisonRange(-50) && 360 in venomRange(-50), "anti-venom+: 15 minutes, venom about 3.6 minutes")
        // 17.7 minutes = 1,062 seconds = 1,770 ticks; 6.3 minutes = 378 seconds = 630 ticks.
        assertTrue(1770 in poisonRange(-59) && 630 in venomRange(-59), "extended anti-venom+: 17.7 minutes, venom about 6.3 minutes")
        assertEquals(60, PotionEffects.ANTIDOTE_PLUS_PLUS_VENOM_IMMUNITY_TICKS)
        assertEquals(1230 to 90, PotionEffects.ANTI_VENOM_POISON_IMMUNITY_TICKS to PotionEffects.ANTI_VENOM_VENOM_IMMUNITY_TICKS)
        assertEquals(1500 to 360, PotionEffects.ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS to PotionEffects.ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS)
        assertEquals(1770 to 630, PotionEffects.EXTENDED_ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS to PotionEffects.EXTENDED_ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS)
        assertEquals(1200, PotionEffects.ANTIPOISON_PLUS_PLUS_IMMUNITY_TICKS, "the existing antidote++ poison value uses the same upper-bound convention")
    }

    @Test
    fun `every dose drinks in order and recipes are wired`() {
        mapOf(
            PotionType.ANTI_VENOM to listOf(Items.ANTI_VENOM_4, Items.ANTI_VENOM_3, Items.ANTI_VENOM_2, Items.ANTI_VENOM_1),
            PotionType.ANTI_VENOM_PLUS to listOf(Items.ANTI_VENOM_PLUS_4, Items.ANTI_VENOM_PLUS_3, Items.ANTI_VENOM_PLUS_2, Items.ANTI_VENOM_PLUS_1),
            PotionType.EXTENDED_ANTI_VENOM_PLUS to
                listOf(Items.EXTENDED_ANTI_VENOM_PLUS_4, Items.EXTENDED_ANTI_VENOM_PLUS_3, Items.EXTENDED_ANTI_VENOM_PLUS_2, Items.EXTENDED_ANTI_VENOM_PLUS_1),
        ).forEach { (type, doses) ->
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType)
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement)
            }
        }
        val recipes = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/anti_venom_recipes.plugin.kts").readText()
        assertTrue("val scales = 5 * doses" in recipes && "30.0 * doses" in recipes && "Skills.HERBLORE) < 87" in recipes)
        assertTrue("item2 = Items.ANTI_VENOM_4" in recipes && "addXp(Skills.HERBLORE, 125.0)" in recipes && "Skills.HERBLORE) < 94" in recipes)
        assertTrue("VENOM_IMMUNITY] = PotionEffects.ANTIDOTE_PLUS_PLUS_VENOM_IMMUNITY_TICKS" in File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/PotionType.kt").readText())
    }
}

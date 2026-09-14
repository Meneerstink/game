package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT step 4 batch "potions-skill" against the OSRS Wiki item pages. */
class OsrsPotionsSkillImportTests {
    @Test
    fun `skill potion and hunter mix values follow the wiki`() {
        (1..99).forEach { level ->
            assertEquals(floor(level * 0.15).toInt() + 4, SkillPotions.hunterMixBoost(level), "hunter mix boost at $level")
            assertEquals(floor(level * 0.20).toInt() + 6, SkillPotions.sunlightRestore(level), "sunlight restore at $level")
        }
        assertEquals(listOf(6, 8, 22), listOf(SkillPotions.SUPER_SKILL_BOOST, SkillPotions.MIX_HEAL, SkillPotions.MOONLIGHT_PRAYER))
    }

    @Test
    fun `every dose drinks in order and release and apply are wired`() {
        mapOf(
            PotionType.SUPER_FISHING to listOf(Items.SUPER_FISHING_POTION_4, Items.SUPER_FISHING_POTION_3, Items.SUPER_FISHING_POTION_2, Items.SUPER_FISHING_POTION_1),
            PotionType.SUPER_HUNTER to listOf(Items.SUPER_HUNTER_POTION_4, Items.SUPER_HUNTER_POTION_3, Items.SUPER_HUNTER_POTION_2, Items.SUPER_HUNTER_POTION_1),
            PotionType.RUBY_HARVEST_MIX to listOf(Items.RUBY_HARVEST_MIX_2, Items.RUBY_HARVEST_MIX_1),
            PotionType.SAPPHIRE_GLACIALIS_MIX to listOf(Items.SAPPHIRE_GLACIALIS_MIX_2, Items.SAPPHIRE_GLACIALIS_MIX_1),
            PotionType.BLACK_WARLOCK_MIX to listOf(Items.BLACK_WARLOCK_MIX_2, Items.BLACK_WARLOCK_MIX_1),
            PotionType.SNOWY_KNIGHT_MIX to listOf(Items.SNOWY_KNIGHT_MIX_2, Items.SNOWY_KNIGHT_MIX_1),
            PotionType.MOONLIGHT_MOTH_MIX to listOf(Items.MOONLIGHT_MOTH_MIX_2, Items.MOONLIGHT_MOTH_MIX_1),
            PotionType.SUNLIGHT_MOTH_MIX to listOf(Items.SUNLIGHT_MOTH_MIX_2, Items.SUNLIGHT_MOTH_MIX_1),
        ).forEach { (type, doses) ->
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType)
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement)
            }
        }
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/skill_potions.plugin.kts").readText()
        listOf(
            "on_item_option(item = Items.MOONLIGHT_MOTH, option = \"Release\")",
            "on_item_option(item = Items.SUNLIGHT_MOTH, option = \"Release\")",
            "player.inventory.add(Items.BUTTERFLY_JAR, 1)",
            "on_item_option(item = dressing, option = \"Apply\")",
        ).forEach { assertTrue(it in plugin, it) }
    }
}

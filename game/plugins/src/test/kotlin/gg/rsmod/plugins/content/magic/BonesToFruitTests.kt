package gg.rsmod.plugins.content.magic

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bones to Bananas/Peaches (spell and lectern tablets, 2026-09-24): only bones "up to big bones", one shared list. */
class BonesToFruitTests {
    @Test
    fun `only bones up to big bones are converted - never dragon, babydragon, zogre, ourg or dagannoth bones`() {
        assertEquals(
            setOf(Items.BONES, Items.BURNT_BONES, Items.BAT_BONES, Items.WOLF_BONES, Items.BIG_BONES, Items.MONKEY_BONES, Items.JOGRE_BONES),
            BonesToFruit.BONES,
        )
        listOf(Items.DRAGON_BONES, Items.BABYDRAGON_BONES, Items.ZOGRE_BONES, Items.OURG_BONES, Items.DAGANNOTH_BONES).forEach {
            assertTrue(it !in BonesToFruit.BONES)
        }
    }

    @Test
    fun `spell and tablets share the list, and every lectern tablet has a use`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content/magic")
        val spells = File(root, "standard/standard_utility_spells.plugin.kts").readText()
        assertTrue("BonesToFruit.convert" in spells && "private val BONES" !in spells)
        val tablets = File(root, "magic_tablets.plugin.kts").readText()
        listOf("BONES_TO_BANANAS", "BONES_TO_PEACHES_8015", "ENCHANT_SAPPHIRE", "ENCHANT_EMERALD", "ENCHANT_RUBY", "ENCHANT_DIAMOND", "ENCHANT_DRAGONSTN", "ENCHANT_ONYX")
            .forEach { assertTrue("Items.$it" in tablets, it) }
    }
}

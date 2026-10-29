package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.tools.importer.MaxCapeMenuTool
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.SkillcapeSpellbook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-29 ("jawel op maxcape magic en normale magic cape moet je dus 5 keer kunen switchen", replaces buglist E2 of
 * 2026-10-03): the plain max cape and the Magic cape (t) carry the OSRS "Spellbook" option, five changes a day.
 */
class MaxCapeOptionsTests {
    @Test
    fun `the max cape menus are the OSRS ones, Spellbook included`() {
        assertEquals(MaxCapes.MAX_CAPE, MaxCapeMenuTool.MAX_CAPE)
        assertEquals(listOf("Teleports", "Wear", "Spellbook", "Features"), MaxCapeMenuTool.INVENTORY.toSortedMap().values.toList())
        // The worn slot shows params 528-531 only (clientscript 1612): four options, every one reachable.
        assertEquals(listOf("Home", "Teleports", "Spellbook", "Features"), MaxCapeMenuTool.WORN.toSortedMap().values.toList())
        assertTrue(MaxCapeMenuTool.MENUS.values.all { (_, worn) -> worn.keys.all { it in 528..531 } })
    }

    @Test
    fun `the Magic cape and Magic cape (t) menus are the OSRS ones`() {
        assertEquals(Items.MAGIC_CAPE, MaxCapeMenuTool.MAGIC_CAPE)
        assertEquals(Items.MAGIC_CAPE_T, MaxCapeMenuTool.MAGIC_CAPE_T)
        listOf(Items.MAGIC_CAPE, Items.MAGIC_CAPE_T).forEach { cape ->
            val (inventory, worn) = MaxCapeMenuTool.MENUS.getValue(cape)
            assertEquals(listOf("Wear", "Spellbook", "Check"), inventory.toSortedMap().values.toList())
            assertEquals(listOf("Boost", "Spellbook", "Check"), worn.toSortedMap().values.toList())
        }
    }

    @Test
    fun `Spellbook gives five changes a day and is bound on all three capes`() {
        assertEquals(5, SkillcapePerks.DailyUse.SPELLBOOK.limit)
        assertEquals("Ancient", SkillcapeSpellbook.bookName(Spellbook.ANCIENT))
        assertEquals("Your cape has 1 spellbook change left for today.", SkillcapeSpellbook.leftMessage(1))
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/skills/skillcape_spellbook.plugin.kts").readText()
        listOf("Items.MAGIC_CAPE,", "Items.MAGIC_CAPE_T,", "MaxCapes.MAX_CAPE").forEach { assertTrue(it in plugin, it) }
        assertTrue("SkillcapeSpellbook.choose(player)" in plugin)
    }
}

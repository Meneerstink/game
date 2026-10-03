package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.tools.importer.MaxCapeMenuTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner 2026-10-03 (buglist E2): the normal max cape has no "Spellbook" (spellbook swap) option, in the cache menus or bound. */
class MaxCapeOptionsTests {
    @Test
    fun `the normal max cape menus have no spellbook option`() {
        assertEquals(MaxCapes.MAX_CAPE, MaxCapeMenuTool.MAX_CAPE)
        val all = MaxCapeMenuTool.INVENTORY.values + MaxCapeMenuTool.WORN.values
        assertTrue(all.none { it.contains("spellbook", ignoreCase = true) }, "max cape menus: $all")
        assertTrue(38 in MaxCapeMenuTool.REMOVED_INVENTORY_OPCODES && 38 !in MaxCapeMenuTool.INVENTORY, "the old slot-4 Spellbook op is dropped")
        assertEquals(listOf("Teleports", "Wear", "Features"), MaxCapeMenuTool.INVENTORY.toSortedMap().values.toList())
        assertEquals(
            listOf("Home", "Crafting Guild", "Guild Teleports", "Skilling Areas", "POH Portals", "Features"),
            MaxCapeMenuTool.WORN.toSortedMap().values.toList(),
        )
    }

    @Test
    fun `no spellbook handler is bound on the normal max cape`() {
        val text = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/max_cape_options.plugin.kts").readText()
        assertTrue("option = \"Spellbook\"" !in text, "a Spellbook option is still bound on the max cape")
        assertTrue("switchSpellbook" !in text, "the max cape still swaps spellbooks")
    }
}

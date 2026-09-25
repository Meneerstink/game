package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.BlightedSackSpellbookPatchTool
import gg.rsmod.plugins.content.magic.SpellbookData
import kotlin.test.Test
import kotlin.test.assertEquals

/** The client lights exactly the spells the server lets a Blighted sack pay for (BlightedSackSpellbookPatchTool). */
class BlightedSackSpellbookTests {
    @Test
    fun `patched spell list matches the server's sacks and the production cache is patched`() {
        val server =
            BlightedSacks.Sack.values().flatMap { sack ->
                sack.spells.map { id ->
                    val spell = SpellbookData.values().first { it.uniqueId == id }
                    ((spell.interfaceId shl 16) or spell.component) to sack.item
                }
            }.toSet()
        assertEquals(server, BlightedSackSpellbookPatchTool.SPELLS.toSet())

        val library = CacheLibrary("../../data/cache")
        try {
            val tool = BlightedSackSpellbookPatchTool
            val icon = library.data(tool.CLIENTSCRIPT_INDEX, tool.ICON_SCRIPT, 0)!!
            assertEquals(icon.toList(), tool.patchIcon(icon).toList(), "production clientscript 21 must carry the sack blocks")
            val tooltip = library.data(tool.CLIENTSCRIPT_INDEX, tool.TOOLTIP_SCRIPT, 0)!!
            assertEquals(tooltip.toList(), tool.patchTooltip(tooltip).toList(), "production clientscript 10 must carry the sack blocks")
            assertEquals(tool.VARC_SACKS_ALLOWED, BlightedSacks.VARC_SACKS_ALLOWED)
        } finally {
            library.close()
        }
    }
}

package gg.rsmod.plugins.content.items.combine

import gg.rsmod.plugins.api.cfg.Items
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the godsword assembly half of the "Godswords cannot be dismantled" fix.
 *
 * `Dismantle` (inventory option 3 on items 11694/11696/11698/11700 in the production cache) turns a
 * godsword into its hilt plus the shared Godsword blade. That action is only safe to expose because
 * the inverse exists: the four [CombinationData] entries below. If they were ever lost or shadowed,
 * dismantling would silently become a one-way item sink for four of the most valuable items in the
 * game, and nothing else in the build would notice.
 */
class GodswordAssemblyTests {
    private val godswordsByHilt =
        mapOf(
            Items.ARMADYL_HILT to Items.ARMADYL_GODSWORD,
            Items.BANDOS_HILT to Items.BANDOS_GODSWORD,
            Items.SARADOMIN_HILT to Items.SARADOMIN_GODSWORD,
            Items.ZAMORAK_HILT to Items.ZAMORAK_GODSWORD,
        )

    @Test
    fun `every godsword can be reassembled from its hilt and the godsword blade`() {
        godswordsByHilt.forEach { (hilt, godsword) ->
            val data =
                CombinationData.values.singleOrNull { it.resultItem == godsword }
                    ?: throw AssertionError("no combination produces godsword $godsword")
            assertEquals(listOf(hilt, Items.GODSWORD_BLADE), data.items.toList())
            assertEquals(1, data.levelRequired)
            assertEquals(CombinationTool.NONE, data.tool)
        }
    }

    @Test
    fun `every combination binds its own item pairs and no pair is bound twice`() {
        // The plugin binds one row at a time: `on_item_on_item(itemUsed = items[0], itemsList = items)` for each row, so rows may
        // share a first ingredient (one colour kit on three robe pieces, OSRS-IMPORT magearmour) as long as no two rows produce
        // the same (item used, item) pair - a repeated pair is rejected at boot as a duplicate binding.
        val pairs =
            CombinationData.values.flatMap { row ->
                if (row.tool != CombinationTool.NONE) {
                    listOf(row.items[0] to row.tool.item)
                } else {
                    row.items.filterNot { it == row.items[0] }.map { row.items[0] to it }
                }
            }
        val repeated = pairs.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("item pairs bound twice: $repeated", repeated.isEmpty())
        val plugin = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/items/combine/item_combination.plugin.kts").readText()
        assertTrue("the plugin binds per row", "CombinationData.values.forEach { def ->" in plugin)
    }

    @Test
    fun `the godsword blade is never the first ingredient`() {
        // The plugin binds `on_item_on_item(itemUsed = items[0], itemsList = items)`; putting the
        // shared blade first would bind blade-on-blade for one god and nothing for the rest.
        CombinationData.values.forEach { data ->
            assertTrue(data.name, data.items[0] != Items.GODSWORD_BLADE)
        }
    }
}

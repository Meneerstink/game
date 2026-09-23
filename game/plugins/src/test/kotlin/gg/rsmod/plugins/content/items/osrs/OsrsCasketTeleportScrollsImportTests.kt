package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT casket sub-batch "casket-teleports" against the OSRS Wiki teleport scroll item pages. */
class OsrsCasketTeleportScrollsImportTests {
    @Test
    fun `open scrolls land on the wiki map square and gated scrolls refuse with the wiki message`() {
        assertEquals(
            mapOf(
                Items.DIGSITE_TELEPORT to CasketTeleportScrolls.Destination(3325, 3412, 2),
                Items.FELDIP_HILLS_TELEPORT to CasketTeleportScrolls.Destination(2541, 2925, 2),
                Items.PEST_CONTROL_TELEPORT to CasketTeleportScrolls.Destination(2658, 2659, 2),
                Items.PISCATORIS_TELEPORT to CasketTeleportScrolls.Destination(2339, 3649, 1),
                Items.LUMBERYARD_TELEPORT to CasketTeleportScrolls.Destination(3302, 3487, 2),
            ),
            CasketTeleportScrolls.DESTINATIONS,
        )
        assertEquals(
            mapOf(
                Items.LUNAR_ISLE_TELEPORT to "You need to complete the Lunar Diplomacy quest before you can reach the island.",
                Items.MORTTON_TELEPORT to "You must speak to Drezel after completing the Priest in Peril quest before you can teleport to Mort'ton",
                Items.IORWERTH_CAMP_TELEPORT to "You need to complete the Regicide quest before you can teleport to Tirannwn.",
                Items.MOS_LEHARMLESS_TELEPORT to "You need to complete the Cabin Fever quest before you can teleport to Mos Le'harmless.",
            ),
            CasketTeleportScrolls.REFUSALS,
        )
        assertTrue((CasketTeleportScrolls.DESTINATIONS.keys intersect CasketTeleportScrolls.REFUSALS.keys).isEmpty(), "one handler per scroll")
        assertEquals(9, (CasketTeleportScrolls.DESTINATIONS.keys + CasketTeleportScrolls.REFUSALS.keys).size)
    }

    @Test
    fun `a scroll is consumed only after the teleport is allowed`() {
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/casket_teleport_scrolls.plugin.kts").readText()
        // One shared scrollTeleport(scroll, destination, type) serves the OSRS "Teleport" and the 667 "Read" scrolls.
        val check = plugin.indexOf("player.canTeleport(type)")
        val remove = plugin.indexOf("player.inventory.remove(item = scroll, amount = 1")
        val teleport = plugin.indexOf("player.teleport(tile, type)")
        assertTrue(check in 0 until remove && remove < teleport, "canTeleport, then remove one scroll, then teleport")
        assertTrue("on_item_option(item = scroll, option = \"Teleport\") { scrollTeleport(scroll, destination, TeleportType.TAB) }" in plugin)
    }
}

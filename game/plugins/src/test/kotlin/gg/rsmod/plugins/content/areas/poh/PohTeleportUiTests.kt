package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.LootKeyInterfaceImportTool
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Box
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Nexus
import gg.rsmod.plugins.content.magic.teleports.TeleportSpell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner bug 2026-10-06 183223 ("scrying portal has no teleport interfaces"): the house nexus opens the OSRS Portal Nexus screen
 * and the jewellery box the OSRS jewellery box screen. Guards that every directory teleport has a row and an icon, that every
 * jewellery box button reaches a real destination, and the OSRS layout arithmetic of both screens.
 */
class PohTeleportUiTests {
    private val fonts = LootKeyInterfaceImportTool.Fonts(1, 2, 3, 4)

    @Test
    fun `every directory teleport fits on the nexus screen`() {
        val rows = PohTeleportUi.NEXUS_ROWS
        assertTrue(rows.size >= 60, "directory has ${rows.size} destinations")
        assertTrue(rows.size <= Nexus.ROWS, "${rows.size} destinations, the screen has ${Nexus.ROWS} rows")
        rows.indices.forEach { r -> assertEquals(r, PohTeleportUi.nexusRowOf(Nexus.row(r))) }
        assertEquals(-1, PohTeleportUi.nexusRowOf(Nexus.row(0) + Nexus.ROW_NAME))
        assertEquals(-1, PohTeleportUi.nexusRowOf(Nexus.row(rows.size)))
    }

    @Test
    fun `every teleport spell has its spellbook icon`() {
        TeleportSpell.values().forEach { assertNotNull(PohTeleports.SPELL_ICONS[it], "no icon for $it") }
        PohTeleports.leaves().forEach { leaf ->
            when (val icon = leaf.icon) {
                is PohTeleports.Icon.Sprite -> assertTrue(icon.id > 0, leaf.name)
                is PohTeleports.Icon.Obj -> assertTrue(icon.id > 0, leaf.name)
            }
        }
    }

    @Test
    fun `row texts carry the OSRS key for the first 35 rows`() {
        assertEquals("<col=ffffff>1</col> :  Varrock", PohTeleportUi.rowText(0, "Varrock"))
        assertEquals("<col=ffffff>A</col> :  Ardougne", PohTeleportUi.rowText(9, "Ardougne"))
        assertEquals("<col=ffffff>Z</col> :  X", PohTeleportUi.rowText(34, "X"))
        assertEquals("Edgeville", PohTeleportUi.rowText(35, "Edgeville"))
    }

    @Test
    fun `every jewellery box button reaches its destination`() {
        PohTeleportInterfaceImportTool.BOXES.forEachIndexed { i, box ->
            assertTrue(box.destinations.size <= Box.MAX_BUTTONS, box.title)
            box.destinations.forEachIndexed { d, name ->
                val leaf = assertNotNull(PohTeleportUi.jewelleryLeaf(Box.button(i, d)), "${box.title}: $name")
                assertEquals(name, leaf.name)
                assertEquals(PohTeleports.Icon.Obj(box.item), leaf.icon, "${box.title}: $name icon")
            }
        }
    }

    @Test
    fun `nexus layout follows OSRS 17`() {
        val comps = PohTeleportInterfaceImportTool.nexus(fonts).associateBy { it.id }
        assertEquals(Nexus.COUNT, comps.size)
        assertEquals(492, comps.getValue(DeathsOfficeInterfaceImportTool.WINDOW).width)
        assertEquals(314, comps.getValue(DeathsOfficeInterfaceImportTool.WINDOW).height)
        val list = comps.getValue(Nexus.LIST)
        assertEquals(264, list.width)
        assertEquals(246, list.height)
        val model = comps.getValue(Nexus.MODEL)
        assertTrue(model.x > list.x + list.width, "model right of the list")
        val row = comps.getValue(Nexus.row(1))
        assertEquals(24, row.y)
        assertEquals(listOf("Teleport"), row.ops)
        assertEquals(PohTeleportInterfaceImportTool.COLOUR_SCRIPT, row.onMouseOver.first())
        assertEquals(false, comps.getValue(Nexus.row(1) + Nexus.ROW_BACKING).hidden, "odd rows on black")
        assertEquals(true, comps.getValue(Nexus.row(0) + Nexus.ROW_BACKING).hidden, "even rows plain")
        assertEquals(34, comps.getValue(Nexus.row(0) + Nexus.ROW_NAME).x)
    }

    @Test
    fun `jewellery box layout follows OSRS clientscript 1685`() {
        assertEquals(listOf(225 to 91, 225 to 91, 225 to 109, 225 to 109, 225 to 72, 225 to 72), (0 until 6).map { PohTeleportInterfaceImportTool.boxGeometry(it).let { g -> g.w to g.h } })
        assertEquals(240, PohTeleportInterfaceImportTool.boxGeometry(1).x)
        assertEquals(250, PohTeleportInterfaceImportTool.boxGeometry(4).y)
        assertEquals(-25 to 25, PohTeleportInterfaceImportTool.buttonSpacing(91, 3))
        assertEquals(-23 to 15, PohTeleportInterfaceImportTool.buttonSpacing(72, 4))
        val comps = PohTeleportInterfaceImportTool.jewelleryBox(fonts).associateBy { it.id }
        assertEquals(Box.COUNT, comps.size)
        val first = comps.getValue(Box.button(0, 0))
        assertEquals("<col=ccccff>1:</col> Duel Arena", first.text)
        assertEquals(listOf("Duel Arena"), first.ops)
        val unused = comps.getValue(Box.button(4, 2))
        assertTrue(unused.hidden, "Ring of wealth has two destinations")
    }

    @Test
    fun `components without hover hooks encode as before`() {
        val c = DeathsOfficeInterfaceImportTool.Comp(1, 0, 0, 0, 10, 10, 0)
        val bytes = DeathsOfficeInterfaceImportTool.encode(c)
        // header 19, layer body 5, events 3, op keys 1, opBase 1, op count 1, pause text 1, drag 3, target verb 1, 20 empty hooks, 5 lists
        assertEquals(19 + 5 + 3 + 1 + 1 + 1 + 1 + 3 + 1 + 20 + 5, bytes.size)
    }
}

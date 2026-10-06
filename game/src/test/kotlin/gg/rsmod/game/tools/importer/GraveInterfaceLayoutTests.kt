package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool.Grave
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-06 (OWNER_QUESTIONS Q13 = A): the grave loot screen showed two item rows with empty space below (the OSRS fee
 * section, always empty under his no-fee rule). The free list now fills the window down to the info line.
 */
class GraveInterfaceLayoutTests {
    private val comps = DeathsOfficeInterfaceImportTool.grave(LootKeyInterfaceImportTool.Fonts(1, 2, 3, 4), 1).associateBy { it.id }

    private fun bottom(id: Int) = comps.getValue(id).let { it.y + it.height }

    @Test
    fun `the free list fills the window down to the info line`() {
        val free = comps.getValue(Grave.FREE_CONTAINER)
        val pay = comps.getValue(Grave.PAY_CONTAINER)
        assertEquals(pay.y, bottom(Grave.FREE_CONTAINER), "free section ends where the info line starts")
        assertTrue(bottom(Grave.PAY_CONTAINER) <= Grave.HEIGHT - 10, "info line inside the window")
        assertEquals(Grave.FREE_VIEW_HEIGHT, comps.getValue(Grave.FREE_ITEMS).height)
        assertEquals(Grave.FREE_VIEW_HEIGHT, comps.getValue(Grave.FREE_SCROLLBAR).height)
        assertTrue(comps.getValue(Grave.FREE_ITEMS).y + Grave.FREE_VIEW_HEIGHT <= free.height, "list inside its section")
        assertTrue(Grave.FREE_VIEW_HEIGHT / Grave.FREE_ROW_STEP >= 5, "at least five full rows visible")
        assertEquals(0, comps.getValue(Grave.INFO).y)
    }
}

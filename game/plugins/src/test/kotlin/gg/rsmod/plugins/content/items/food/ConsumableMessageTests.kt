package gg.rsmod.plugins.content.items.food

import kotlin.test.Test
import kotlin.test.assertEquals

/** Testclient 2026-10-31: a keg sip said "You drink the dwarven stout (4)." - the serving count is not part of the drink's name. */
class ConsumableMessageTests {
    @Test
    fun `the default message drops a keg's serving count and keeps every other name`() {
        assertEquals("You drink the dwarven stout.", ConsumableTable.defaultMessage(true, "Dwarven stout (4)"))
        assertEquals("You drink the dwarven stout.", ConsumableTable.defaultMessage(true, "Dwarven stout(m3)"))
        assertEquals("You drink the cup of tea.", ConsumableTable.defaultMessage(true, "Cup of tea"))
        assertEquals("You eat the toad batta.", ConsumableTable.defaultMessage(false, "Toad batta"))
    }
}

package gg.rsmod.plugins.content.areas.poh

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N210 (owner 2026-10-22): the prayer altar in a player's house offers no spellbook swap - only the prayer book choice. */
class PohAltarNoSpellbookTests {
    @Test
    fun `the house prayer altar's Pray offers the prayer books only`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/areas/poh/player_house.plugin.kts").readText()
        val pray = source.substringAfter("on_obj_option(obj = PlayerHouse.ALTAR, option = \"pray\")").substringBefore("\n}\n")
        assertTrue("Use Ancient Curses." in pray)
        assertFalse("spellbook" in pray.lowercase().replace("no spellbook swap", ""), "no spellbook choice: $pray")
    }
}

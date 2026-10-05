package gg.rsmod.plugins.content.combat

import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner 2026-10-04 031046 ("graphics settings or sound settings or whatever setting ... the gui keeps disappearing while you
 * are getting attacked"): every settings page the Options tab (261) opens on the main screen stays open when the player is
 * hit. Read from the source, so a settings page added later is covered too. Also the Barrows puzzle (03-2049: a crypt rat's
 * hit closed it in the cycle it opened, so the door "did nothing").
 */
class CombatPersistentModalTests {
    @Test
    fun `every Options tab settings page stays open when hit`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/inter/gameframe/settings_tab.plugin.kts").readText()
        val pages =
            Regex("""on_button\(interfaceId = 261, component = \d+\) \{\s*player\.openInterface\(interfaceId = (\d+), dest = InterfaceDestination\.MAIN_SCREEN\)""")
                .findAll(source).map { it.groupValues[1].toInt() }.toList()
        assertTrue("found the Options tab settings pages in settings_tab.plugin.kts: $pages", pages.containsAll(listOf(742, 743, 623)))
        assertEquals("settings pages closed by a hit", emptyList<Int>(), pages.filterNot { Combat.staysOpenWhenHit(it) })
    }

    @Test
    fun `the Barrows puzzle stays open when hit`() {
        assertTrue(Combat.staysOpenWhenHit(Barrows.PUZZLE_INTERFACE))
    }
}

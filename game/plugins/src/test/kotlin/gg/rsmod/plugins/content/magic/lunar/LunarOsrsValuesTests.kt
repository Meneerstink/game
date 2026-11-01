package gg.rsmod.plugins.content.magic.lunar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** N72 deel C: existing Lunar spells with OSRS values (ChatGPT-B report N72-DEELC-ONDERZOEK.txt 'DEELS'). */
class LunarOsrsValuesTests {
    private val src = File("src/main/kotlin/gg/rsmod/plugins/content/magic/lunar/lunar_spells.plugin.kts").readText()

    @Test
    fun `Dream heals 1 hitpoint every 20 seconds (10 life points per 33 ticks), three times the normal rate`() {
        assertTrue("val DREAM_HEAL_TICKS = 33" in src)
        assertTrue("val DREAM_HEAL = 10" in src)
        assertTrue("wait(DREAM_HEAL_TICKS)" in src && "player.heal(DREAM_HEAL)" in src)
    }

    @Test
    fun `Spellbook Swap gives 130 Magic XP with its cast animation and graphic`() {
        val swap = src.substringAfter("on_magic_spell_button(\"Spellbook Swap\")").substringBefore("on_timer(SpellbookSwap.TIMER)")
        assertTrue("player.addXp(Skills.MAGIC, 130.0" in swap)
        assertTrue("player.animate(6299)" in swap && "player.graphic(1062)" in swap)
        // Paid and rewarded only after a book was chosen.
        assertTrue(swap.indexOf("options(") < swap.indexOf("removeRunes"))
    }
}

package gg.rsmod.plugins.content.mechanics.prayer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the death-stage boundary used by the Wrath blast. */
class AncientCursesDeathHookTests {
    @Test
    fun `Wrath is dispatched before respawn and curse cleanup is deferred to the death hook`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/prayer/ancient_curses.plugin.kts").readText()
        val preDeath = source.substringAfter("on_player_pre_death {").substringBefore("on_player_death {")
        val death = source.substringAfterLast("on_player_death {")

        assertTrue("AncientCurses.wrathExplosion(player)" in preDeath)
        assertTrue("AncientCurses.deactivateAllCurses(player)" in death)
        assertTrue("AncientCurses.clearDrainState(player)" in death)
    }
}

package gg.rsmod.plugins.content.mechanics.prayer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the death-stage boundary used by the Wrath blast. */
class AncientCursesDeathHookTests {
    @Test
    fun `Wrath is dispatched at the start of death and curse cleanup is deferred to the death hook`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/prayer/ancient_curses.plugin.kts").readText()
        // Void PlayerDeath: wrath(this) fires at the moment of death, before the death animation (not after it with the items).
        val deathStart = source.substringAfter("on_player_death_start {").substringBefore("on_player_death {")
        val death = source.substringAfterLast("on_player_death {")

        assertTrue("AncientCurses.wrathExplosion(player)" in deathStart)
        assertTrue("AncientCurses.deactivateAllCurses(player)" in death)
        assertTrue("AncientCurses.clearDrainState(player)" in death)
    }

    @Test
    fun `Retribution is dispatched at the start of death`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/prayer/prayers.plugin.kts").readText()
        val deathStart = source.substringAfter("on_player_death_start {").substringBefore("}")

        assertTrue("Retribution.onPlayerDeath(player)" in deathStart)
    }
}

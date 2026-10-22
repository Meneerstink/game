package gg.rsmod.plugins.content.areas.grandexchange

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** N198 (owner 2026-10-22, choice: Trailblazer unlock power): reading the Ancient hymnal plays an unlock-power sound, not a key in a lock. */
class CurseUnlockSoundTests {
    @Test
    fun `the hymnal plays the unlock-power sound and GE lamps name the real gain`() {
        val s = File("src/main/kotlin/gg/rsmod/plugins/content/areas/grandexchange/grandexchange_unlocks.plugin.kts").readText()
        val hymnal = s.substringAfter("on_item_option(item = Items.ANCIENT_HYMNAL, option = \"read\")").substringBefore("\n}\n")
        assertTrue("Sfx.TRAILBLAZER_UNLOCK_POWER" in hymnal && "Sfx.UNLOCK)" !in hymnal, hymnal)
        assertTrue("ExperienceLamps.grant(player, selected.second, xp)" in s)
    }
}

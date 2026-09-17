package gg.rsmod.plugins.content.skills.hunter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 delayed interaction stability: a timed trap must not message a stale owner. */
class HunterDelayedInteractionTests {
    @Test
    fun `timed trap failure notification requires an online living owner`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/skills/hunter/Hunter.kt").readText()
        val callback = source.substringAfter("// Trap timed out without a catch")

        assertTrue("player.isOnline && !player.isDead()" in callback)
        assertTrue(callback.indexOf("player.isDead()") < callback.indexOf("player.filterableMessage"))
    }
}

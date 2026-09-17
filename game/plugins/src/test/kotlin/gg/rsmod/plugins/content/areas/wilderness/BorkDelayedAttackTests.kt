package gg.rsmod.plugins.content.areas.wilderness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: Bork's delayed curse must not hit a dead/offline victim. */
class BorkDelayedAttackTests {
    @Test
    fun `delayed curse rejects stale victim before presentation and damage`() {
        val source =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/bork.plugin.kts",
            ).readText()
        val delayedAttack = source.substringAfter("val victim = target").substringBefore("elite.postAttackLogic")

        assertTrue(
            delayedAttack.contains("if (victim is Player && victim.isOnline && !victim.isDead())"),
            "delayed Bork curse must stop after logout/death",
        )
        assertTrue(delayedAttack.indexOf("victim.isDead()") < delayedAttack.indexOf("victim.hit"))
    }
}

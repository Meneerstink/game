package gg.rsmod.plugins.content.skills.firemaking

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: firemaking's delayed facing/timer callback must not touch stale players. */
class FiremakingDelayedInteractionTests {
    @Test
    fun `delayed facing rejects offline or dead player before player mutation`() {
        val source =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/skills/firemaking/FiremakingAction.kt",
            ).readText()
        val delayedFacing = source.substringAfter("val targetTile = player.findWesternTile()").substringAfter("world.queue {").substringBefore("                    }")

        assertTrue(
            delayedFacing.contains("if (!player.isOnline || player.isDead()) return@queue"),
            "delayed firemaking callback must stop after logout/death",
        )
        assertTrue(delayedFacing.indexOf("player.isDead()") < delayedFacing.indexOf("player.faceTile"))
    }
}

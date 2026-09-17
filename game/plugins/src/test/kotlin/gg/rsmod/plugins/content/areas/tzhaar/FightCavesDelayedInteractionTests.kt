package gg.rsmod.plugins.content.areas.tzhaar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: Fight Caves must not message a player after logout. */
class FightCavesDelayedInteractionTests {
    @Test
    fun `leave reward messages stop after player logout`() {
        val source =
            File(
                "src/main/kotlin/gg/rsmod/plugins/content/areas/tzhaar/fightcaves/FightCaves.kt",
            ).readText()
        val leave = source.substringAfter("fun leave(").substringBefore("private fun addOrDrop")
        val queue = leave.substringAfter("world.queue {")

        assertTrue(
            "if (!player.isOnline) return@queue" in queue,
            "delayed Fight Caves reward messages must stop after logout",
        )
        assertTrue(queue.indexOf("player.isOnline") < queue.indexOf("player.message"))
    }
}

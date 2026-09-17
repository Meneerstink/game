package gg.rsmod.plugins.content.activity.duel_arena

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the already-scheduled duel countdown from mutating logged-out participants. */
class DuelArenaDelayedInteractionTests {
    @Test
    fun `countdown stops after match invalidation and gates participant mutations on online state`() {
        val source =
            Files.readString(
                Path.of(
                    "src",
                    "main",
                    "kotlin",
                    "gg",
                    "rsmod",
                    "plugins",
                    "content",
                    "activity",
                    "duel_arena",
                    "duel_arena.plugin.kts",
                ),
            )

        assertTrue("if (match.stage != DuelStage.FIGHTING) return@queue" in source)
        assertTrue("if (it.isOnline) it.forceChat(\"\$count\")" in source)
        assertTrue("if (it.isOnline) {" in source)
        assertTrue(source.indexOf("match.stage != DuelStage.FIGHTING") < source.indexOf("forceChat(\"\$count\")"))
        assertTrue(source.indexOf("if (it.isOnline) {") < source.indexOf("CAN_FIGHT_ATTR"))
    }
}

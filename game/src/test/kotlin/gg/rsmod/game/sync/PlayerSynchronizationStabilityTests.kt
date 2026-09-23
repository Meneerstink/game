package gg.rsmod.game.sync

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Shared player-update stability contract: encoded skip segments must not abort the task. */
class PlayerSynchronizationStabilityTests {
    @Test
    fun `skip-count segments do not throw after being encoded`() {
        val source =
            Path(
                "src/main/kotlin/gg/rsmod/game/sync/task/PlayerSynchronizationTask.kt",
            ).readText()

        assertTrue("PlayerSkipCountSegment(skipCount)" in source)
        assertTrue("PlayerSkipCountSegment(count = skipCount)" in source)
        assertFalse("throw RuntimeException()" in source)
    }
}

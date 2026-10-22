package gg.rsmod.game.model.entity

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N29 (owner VRAGEN 20 = A, 2026-10-17): an X-logged body stays while it is hit, at most 60 seconds (100 ticks), boss or PvP. */
class XLogHoldTests {
    @Test
    fun `the forced disconnection deadline is 60 seconds`() {
        assertEquals(100, Player.X_LOG_MAX_TICKS)
        val source = File("src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        assertTrue("timers[FORCE_DISCONNECTION_TIMER] = X_LOG_MAX_TICKS" in source)
    }
}

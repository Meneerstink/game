package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [Pawn.freeze]'s freeze-immunity behaviour (further-foundations pass following
 * P9, 2026-09-02 autonomous run — see `FREEZE_IMMUNITY_TIMER`'s doc comment in `Timers.kt` for
 * the sourcing note: 5 ticks of immunity after any freeze wears off, sourced from the OSRS
 * wiki's "Freeze" article, applicable to this server's ~2011/rev-667 era). The movement-lock
 * mechanic itself (`FROZEN_TIMER`, `stopMovement()`) already existed and is unchanged; only the
 * new immunity gate is exercised here.
 */
class PawnExtFreezeTests {
    @Test
    fun `freeze succeeds and arms both the freeze timer and a longer-lasting immunity timer`() {
        val player = newPlayer()
        var freezeCallbackRan = false

        val result = player.freeze(cycles = 4) { freezeCallbackRan = true }

        assertTrue(result)
        assertTrue(freezeCallbackRan)
        assertEquals(4, player.timers[FROZEN_TIMER])
        // 4 (freeze duration) + 5 (immunity ticks) = 9, so immunity outlasts the freeze itself.
        assertEquals(9, player.timers[FREEZE_IMMUNITY_TIMER])
        verify { player.stopMovement() }
    }

    @Test
    fun `freeze fails while already frozen`() {
        val player = newPlayer()
        player.timers[FROZEN_TIMER] = 4
        var freezeCallbackRan = false

        val result = player.freeze(cycles = 4) { freezeCallbackRan = true }

        assertFalse(result)
        assertFalse(freezeCallbackRan)
    }

    @Test
    fun `freeze fails during the post-freeze immunity window even though the freeze itself has expired`() {
        val player = newPlayer()
        // Simulates a freeze that has already worn off (FROZEN_TIMER at 0) but is still within
        // its immunity window (FREEZE_IMMUNITY_TIMER > 0).
        player.timers[FREEZE_IMMUNITY_TIMER] = 3
        var freezeCallbackRan = false

        val result = player.freeze(cycles = 4) { freezeCallbackRan = true }

        assertFalse(result)
        assertFalse(freezeCallbackRan)
    }

    @Test
    fun `freeze succeeds again once both the freeze and its immunity window have fully expired`() {
        val player = newPlayer()
        // Neither timer has been set at all - the equivalent of both having ticked down to 0.
        var freezeCallbackRan = false

        val result = player.freeze(cycles = 4) { freezeCallbackRan = true }

        assertTrue(result)
        assertTrue(freezeCallbackRan)
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.timers } returns TimerMap()
        return player
    }
}

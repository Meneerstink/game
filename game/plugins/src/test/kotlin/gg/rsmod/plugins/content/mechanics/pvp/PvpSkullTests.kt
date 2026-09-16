package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.GameContext
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.SkullIcon
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman PvP guards plan (2026-09-16) coverage for [PvpSkull]'s reworked duration/reset/pause
 * model: 5-minute base duration, reset-to-5-minutes on every fresh non-retaliation attack, and
 * the pause rule (instanced area or ~1 minute standing on the same tile) implemented via
 * [gg.rsmod.game.model.timer.TimerMap.pause]/[gg.rsmod.game.model.timer.TimerMap.resume] so the
 * countdown genuinely freezes rather than merely being compensated after the fact.
 */
class PvpSkullTests {
    @Test
    fun `duration constant is 5 minutes in 0-6s cycles`() {
        assertEquals(500, PvpSkull.SKULL_DURATION_CYCLES)
    }

    @Test
    fun `an unprovoked attack skulls the attacker for the full 5 minutes and arms pause tracking`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, home = home)
        val victim = newPlayer(tile = home, home = home)

        PvpSkull.onPlayerInitiatedAttack(attacker, victim)

        verify { attacker.skullIcon = SkullIcon.RED.id }
        assertEquals(PvpSkull.SKULL_DURATION_CYCLES, attacker.timers[SKULL_ICON_DURATION_TIMER])
        assertTrue(attacker.timers.exists(PvpSkull.SKULL_PAUSE_CHECK_TIMER), "pause-tracking driver must be armed")
    }

    @Test
    fun `attacking again while already skulled resets the timer back to 5 minutes`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, home = home)
        val victimA = newPlayer(tile = home, home = home)
        val victimB = newPlayer(tile = home, home = home)

        PvpSkull.onPlayerInitiatedAttack(attacker, victimA)
        // Simulate time passing (a partially-drained timer) before the second attack.
        attacker.timers[SKULL_ICON_DURATION_TIMER] = 42

        PvpSkull.onPlayerInitiatedAttack(attacker, victimB)

        assertEquals(PvpSkull.SKULL_DURATION_CYCLES, attacker.timers[SKULL_ICON_DURATION_TIMER])
    }

    @Test
    fun `tickPauseTracking pauses the skull timer while the player is in an instanced area`() {
        val player = newSkulledPlayer(tile = Tile(6500, 100, 0))

        PvpSkull.tickPauseTracking(player)

        assertTrue(player.timers.isPaused(SKULL_ICON_DURATION_TIMER))
    }

    @Test
    fun `tickPauseTracking does not pause a moving player outside an instance`() {
        val player = newSkulledPlayer(tile = Tile(3100, 3100, 0))

        PvpSkull.tickPauseTracking(player)

        assertFalse(player.timers.isPaused(SKULL_ICON_DURATION_TIMER))
    }

    @Test
    fun `tickPauseTracking pauses after standing on the same tile for about a minute`() {
        val tile = Tile(3100, 3100, 0)
        val player = newSkulledPlayer(tile = tile)

        // The first call always establishes the baseline tile (stallCycles=0); the stall counter
        // only starts incrementing from the second call onward, so it takes
        // SAME_TILE_STALL_CYCLES + 1 total calls on the same tile to reach the threshold.
        repeat(PvpSkull.SAME_TILE_STALL_CYCLES) {
            PvpSkull.tickPauseTracking(player)
            assertFalse(player.timers.isPaused(SKULL_ICON_DURATION_TIMER), "must not pause before the stall threshold")
        }
        PvpSkull.tickPauseTracking(player)

        assertTrue(player.timers.isPaused(SKULL_ICON_DURATION_TIMER))
    }

    @Test
    fun `tickPauseTracking resumes once the player moves off the stalled tile`() {
        val player = newSkulledPlayer(tile = Tile(3100, 3100, 0))
        repeat(PvpSkull.SAME_TILE_STALL_CYCLES + 1) { PvpSkull.tickPauseTracking(player) }
        assertTrue(player.timers.isPaused(SKULL_ICON_DURATION_TIMER))

        every { player.tile } returns Tile(3100, 3101, 0)
        PvpSkull.tickPauseTracking(player)

        assertFalse(player.timers.isPaused(SKULL_ICON_DURATION_TIMER))
    }

    @Test
    fun `tickPauseTracking stops rescheduling once the skull is gone`() {
        val player = newSkulledPlayer(tile = Tile(3100, 3100, 0))
        // Sentinel: the early-return path must leave this untouched (not re-arm it to 1),
        // proving it stops rescheduling once the skull clears.
        player.timers[PvpSkull.SKULL_PAUSE_CHECK_TIMER] = 99
        every { player.skullIcon } returns SkullIcon.NONE.id
        player.timers.remove(SKULL_ICON_DURATION_TIMER)

        PvpSkull.tickPauseTracking(player)

        assertEquals(99, player.timers[PvpSkull.SKULL_PAUSE_CHECK_TIMER])
    }

    private fun newSkulledPlayer(tile: Tile): Player {
        val home = Tile(3140, 3640, 0)
        val player = newPlayer(tile = tile, home = home)
        every { player.skullIcon } returns SkullIcon.RED.id
        player.timers[SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES
        return player
    }

    private fun newPlayer(
        tile: Tile,
        home: Tile,
    ): Player {
        val gameContext = mockk<GameContext>(relaxed = true)
        every { gameContext.home } returns home

        val world = mockk<World>(relaxed = true)
        every { world.gameContext } returns gameContext

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.entityType } returns EntityType.PLAYER
        every { player.isOnline } returns true
        every { player.invisible } returns false
        every { player.lock } returns LockState.NONE
        every { player.combatLevel } returns 100
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        return player
    }

    companion object {
        private val DEFINITIONS = gg.rsmod.game.fs.DefinitionSet()
    }
}

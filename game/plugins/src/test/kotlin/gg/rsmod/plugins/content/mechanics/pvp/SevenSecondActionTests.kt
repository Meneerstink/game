package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman 7-second timer interface coverage for [SevenSecondAction]: the shared countdown used by
 * logout, non-teleport transport and skulled teleports.
 */
class SevenSecondActionTests {
    @Test
    fun `start arms the countdown for the configured duration and does not run the action yet`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        var ran = false

        SevenSecondAction.start(player, SevenSecondAction.Kind.TRANSPORT) { ran = true }

        assertTrue(SevenSecondAction.isActive(player))
        assertEquals(SevenSecondAction.DURATION_CYCLES, player.timers[SevenSecondAction.COUNTDOWN_TIMER])
        assertEquals(7, SevenSecondAction.secondsLeft(player))
        assertFalse(ran)
    }

    @Test
    fun `countdown text counts whole seconds in the chatbox`() {
        assertEquals("Logging out in 7 seconds...", SevenSecondAction.countdownText(SevenSecondAction.Kind.LOGOUT, 7))
        assertEquals("Teleporting in 1 second...", SevenSecondAction.countdownText(SevenSecondAction.Kind.TELEPORT, 1))
    }

    @Test
    fun `starting a second countdown while one is already active is a no-op`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        var firstRan = false
        var secondRan = false
        SevenSecondAction.start(player, SevenSecondAction.Kind.TRANSPORT) { firstRan = true }

        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { secondRan = true }
        SevenSecondAction.complete(player)

        assertTrue(firstRan, "the original pending action must be the one that fires")
        assertFalse(secondRan, "a second start() while active must not replace the pending action")
    }

    @Test
    fun `complete runs the pending action exactly once and clears the countdown state`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        var runs = 0
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { runs++ }

        SevenSecondAction.complete(player)

        assertEquals(1, runs)
        assertFalse(SevenSecondAction.isActive(player))
    }

    @Test
    fun `cancel clears an active countdown without running the pending action`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        var ran = false
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { ran = true }

        SevenSecondAction.cancel(player, "cancelled")

        assertFalse(SevenSecondAction.isActive(player))
        assertFalse(ran)
    }

    @Test
    fun `cancel is a safe no-op when no countdown is active`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        SevenSecondAction.cancel(player)
        assertFalse(SevenSecondAction.isActive(player))
    }

    @Test
    fun `onZoneCheck cancels the countdown the moment the player's tile changes`() {
        val player = newPlayer(Tile(3200, 3200, 0))
        var ran = false
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { ran = true }

        every { player.tile } returns Tile(3200, 3201, 0)
        SevenSecondAction.onZoneCheck(player)

        assertFalse(SevenSecondAction.isActive(player), "movement must cancel the countdown")
        assertFalse(ran)
    }

    @Test
    fun `onZoneCheck cancels the countdown when the chatbox timer interface is closed or replaced`() {
        val interfaces = newInterfaces()
        val player = newPlayer(Tile(3200, 3200, 0), interfaces)
        var ran = false
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) { ran = true }
        assertTrue(interfaces.isVisible(SevenSecondAction.CHATBOX_INTERFACE), "start must open the chatbox timer interface")

        interfaces.close(752, 13)
        SevenSecondAction.onZoneCheck(player)

        assertFalse(SevenSecondAction.isActive(player), "closing the interface must cancel the countdown")
        assertFalse(ran)
    }

    @Test
    fun `onZoneCheck does not cancel a countdown while the player stays on the same tile with the interface open`() {
        val interfaces = newInterfaces()
        val player = newPlayer(Tile(3200, 3200, 0), interfaces)
        SevenSecondAction.start(player, SevenSecondAction.Kind.LOGOUT) {}

        SevenSecondAction.onZoneCheck(player)

        assertTrue(SevenSecondAction.isActive(player))
    }

    private fun newInterfaces(): InterfaceSet = InterfaceSet(mockk(relaxed = true))

    private fun newPlayer(
        tile: Tile,
        interfaces: InterfaceSet = newInterfaces(),
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.tile } returns tile
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.interfaces } returns interfaces
        return player
    }
}

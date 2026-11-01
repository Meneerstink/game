package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** N516: every typed boarding route moves remotely, so its first move must obey Tele Block too. */
class PestControlRemoteRouteTests {
    private fun player(tile: Tile, blocked: Boolean = false): Player = mockk<Player>(relaxed = true).also {
        every { it.tile } returns tile
        every { it.world } returns mockk<World>(relaxed = true)
        every { it.attr } returns AttributeMap()
        every { it.timers } returns TimerMap()
        every { it.lock } returns LockState.NONE
        every { it.combatLevel } returns 138
        every { it.isDead() } returns false
        every { it.isLocked() } returns false
        if (blocked) it.timers[TELEBLOCK_TIMER] = 100
    }

    @Test
    fun `all remote landers refuse death zones and Tele Block before moving or enrolling`() {
        for (tier in PestControlTier.values()) {
            for (p in listOf(player(Tile(3300, 3900)), player(Tile(3213, 3428), blocked = true))) {
                PestControlHandler.boardRemote(p, tier)
                assertNull(PestControlHandler.lobbyOf(p))
                verify(exactly = 0) { p.moveTo(any<Tile>()) }
            }
        }
    }

    @Test
    fun `all remote landers still board an unblocked player from a safe zone`() {
        for (tier in PestControlTier.values()) {
            val p = player(Tile(3213, 3428))
            try {
                PestControlHandler.boardRemote(p, tier)
                assertEquals(tier, PestControlHandler.lobbyOf(p))
                verify(exactly = 1) { p.moveTo(tier.boardTile) }
            } finally {
                PestControlHandler.leaveLander(p, move = false)
            }
        }
    }
}

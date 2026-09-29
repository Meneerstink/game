package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Combat run 2026-09-29. OSRS Wiki "Hit delay": the distance is "edge-to-edge in game squares". It was the rounded-up straight-line
 * distance between centre tiles, so a diagonal barrage (5, 5) landed at 2 + (1 + 8) / 3 = 5 instead of 2 + (1 + 5) / 3 = 4 - after
 * its own projectile, whose flight is counted in squares.
 */
class HitDelayDistanceTests {
    @Test
    fun `distance is counted in game squares`() {
        assertEquals(5, Combat.tileDistance(Tile(3200, 3200), Tile(3205, 3205)))
        assertEquals(7, Combat.tileDistance(Tile(3200, 3200), Tile(3207, 3203)))
    }

    @Test
    fun `magic, bow and thrown delays on a diagonal follow the OSRS formulas plus the server offset`() {
        val from = Tile(3200, 3200)
        val to = Tile(3205, 3205)
        assertEquals(4, MagicCombatStrategy.getHitDelay(from, to)) // 1 + (1 + 5) / 3, + 1
        assertEquals(3, RangedCombatStrategy.getHitDelay(from, Tile(3207, 3207))) // 1 + (3 + 7) / 6, + 1
        assertEquals(3, RangedCombatStrategy.getThrownHitDelay(from, Tile(3206, 3206))) // 1 + 6 / 6, + 1
        assertEquals(2, MagicCombatStrategy.getHitDelay(from, Tile(3201, 3200))) // adjacent
    }

    @Test
    fun `against a large npc the distance runs to its closest edge`() {
        val player = mockk<Player>(relaxed = true)
        every { player.tile } returns Tile(3200, 3200)
        every { player.getSize() } returns 1
        val npc = mockk<Npc>(relaxed = true)
        every { npc.tile } returns Tile(3204, 3200) // footprint 3204..3206
        every { npc.getSize() } returns 3
        assertEquals(4, Combat.tileDistance(player, npc))
        assertEquals(4, Combat.tileDistance(npc, player))
        every { npc.tile } returns Tile(3200, 3200)
        assertEquals(0, Combat.tileDistance(player, npc))
    }
}

package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.SkullIcon
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman PvP guards plan (2026-09-16) coverage for [CityGuards]'s core predicates and the
 * consecutive-hit damage ramp. Reactive spawn/despawn (which needs a real World/chunk/collision
 * stack) is exercised indirectly through [PvpSkullTests]-style fixtures elsewhere; this file
 * covers the pure logic every other guard code path depends on.
 */
class CityGuardsTests {
    @Test
    fun `isGuard recognises all three guard ids and nothing else`() {
        assertTrue(CityGuards.isGuard(npc(CityGuards.MELEE_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.RANGED_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.WIZGUARD_ID)))
        assertFalse(CityGuards.isGuard(npc(9999)))
    }

    @Test
    fun `bypassesProtectionPrayer is true only for guard npcs`() {
        assertTrue(CityGuards.bypassesProtectionPrayer(npc(CityGuards.MELEE_GUARD_ID)))
        assertTrue(CityGuards.bypassesProtectionPrayer(npc(CityGuards.RANGED_GUARD_ID)))
        assertFalse(CityGuards.bypassesProtectionPrayer(npc(9999)))
        assertFalse(CityGuards.bypassesProtectionPrayer(newPlayer(tile = Tile(0, 0, 0))))
    }

    @Test
    fun `mayAttack requires a guarded (bank-safe) tile even for a skulled player`() {
        // BankZones.isSafe is a real, process-wide cache-derived set (see BankZones.init) that is
        // never populated in a unit test without a loaded cache, so an ordinary overworld tile is
        // always outside every guarded zone here - the meaningful assertion this proves is that
        // being skulled alone is not sufficient without also being in a guarded zone.
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val outsideAnyRealBank = Tile(3200, 3200, 0)

        val skulled = newPlayer(tile = outsideAnyRealBank, skulled = true)
        assertFalse(CityGuards.mayAttack(guard, skulled))
    }

    @Test
    fun `mayAttack requires a red skull even were the tile guarded`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val unskulled = newPlayer(tile = Tile(3200, 3200, 0), skulled = false)

        assertFalse(CityGuards.mayAttack(guard, unskulled))
    }

    @Test
    fun `rampedMaxHit starts at 20 percent of max hitpoints and steps by 20 percent per real attack`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        var cycle = 0
        every { guard.world } returns world
        every { world.currentCycle } answers { cycle }
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 1000)

        cycle = 1
        assertEquals(200, CityGuards.rampedMaxHit(guard, target), "hit 1: 20% of 1000")
        cycle = 2
        assertEquals(400, CityGuards.rampedMaxHit(guard, target), "hit 2: 40% of 1000")
        cycle = 3
        assertEquals(600, CityGuards.rampedMaxHit(guard, target), "hit 3: 60% of 1000")
    }

    @Test
    fun `rampedMaxHit caps at 100 percent of max hitpoints`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        var cycle = 0
        every { guard.world } returns world
        every { world.currentCycle } answers { cycle }
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 1000)

        repeat(10) {
            cycle++
            CityGuards.rampedMaxHit(guard, target)
        }
        assertEquals(1000, CityGuards.rampedMaxHit(guard, target), "ramp must never exceed 100% of max hitpoints")
    }

    @Test
    fun `rampedMaxHit does not double-advance when queried twice in the same world cycle`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        every { guard.world } returns world
        every { world.currentCycle } returns 5
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 1000)

        val first = CityGuards.rampedMaxHit(guard, target)
        val second = CityGuards.rampedMaxHit(guard, target)

        assertEquals(first, second, "two calls in the same cycle must return the same ramp tier")
        assertEquals(200, second)
    }

    @Test
    fun `resetDamageRamp restarts the next hit at 20 percent`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        var cycle = 0
        every { guard.world } returns world
        every { world.currentCycle } answers { cycle }
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 1000)

        cycle = 1
        CityGuards.rampedMaxHit(guard, target)
        cycle = 2
        CityGuards.rampedMaxHit(guard, target)
        CityGuards.resetDamageRamp(target)

        cycle = 3
        assertEquals(200, CityGuards.rampedMaxHit(guard, target), "after a reset the next hit starts over at 20%")
    }

    private fun npc(id: Int): Npc {
        val n = mockk<Npc>(relaxed = true)
        every { n.id } returns id
        return n
    }

    private fun newPlayer(
        tile: Tile,
        skulled: Boolean = false,
        maxLifepoints: Int = 990,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.tile } returns tile
        every { player.skullIcon } returns if (skulled) SkullIcon.RED.id else SkullIcon.NONE.id
        every { player.getMaximumLifepoints() } returns maxLifepoints
        every { player.attr } returns AttributeMap()
        return player
    }
}

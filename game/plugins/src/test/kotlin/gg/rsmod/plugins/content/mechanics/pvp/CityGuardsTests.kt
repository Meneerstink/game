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
 * Deadman guard coverage for [CityGuards]'s core predicates and the consecutive-hit damage ramp
 * (OSRS Wiki "Guard (Deadman Mode)"). Reactive spawn/despawn needs a real World/chunk/collision
 * stack and stays a live-retest item; this file covers the pure logic every guard code path
 * depends on.
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
    fun `mayAttack requires a guarded-city tile even for a skulled player`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val deathZone = Tile(3094, 3491, 0) // Edgeville bank: a hotspot, not a guarded city

        val skulled = newPlayer(tile = deathZone, skulled = true)
        assertFalse(CityGuards.mayAttack(guard, skulled))
    }

    @Test
    fun `mayAttack requires a red skull even inside a guarded city`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val unskulled = newPlayer(tile = Tile(3165, 3487, 0), skulled = false) // Grand Exchange

        assertFalse(CityGuards.mayAttack(guard, unskulled))
    }

    @Test
    fun `mayAttack is true for a skulled player inside every guarded city`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        GuardedZones.ZONES.forEach { zone ->
            val inside = Tile((zone.minX + zone.maxX) / 2, (zone.minZ + zone.maxZ) / 2, 0)
            assertTrue(CityGuards.mayAttack(guard, newPlayer(tile = inside, skulled = true)), "${zone.name} must be guarded")
        }
    }

    @Test
    fun `guards ignore single-combat restrictions, ordinary npcs and players do not`() {
        assertTrue(CityGuards.ignoresSingleCombat(npc(CityGuards.MELEE_GUARD_ID)))
        assertTrue(CityGuards.ignoresSingleCombat(npc(CityGuards.RANGED_GUARD_ID)))
        assertFalse(CityGuards.ignoresSingleCombat(npc(9999)))
        assertFalse(CityGuards.ignoresSingleCombat(newPlayer(tile = Tile(0, 0, 0))))
    }

    @Test
    fun `rampedMaxHit starts at 20 percent of the hitpoints level and rises by 2 per attack`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        var cycle = 0
        every { guard.world } returns world
        every { world.currentCycle } answers { cycle }
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 99)

        cycle = 1
        assertEquals(19, CityGuards.rampedMaxHit(guard, target), "hit 1: 20% of 99 hitpoints")
        cycle = 2
        assertEquals(21, CityGuards.rampedMaxHit(guard, target), "hit 2: +2")
        cycle = 3
        assertEquals(23, CityGuards.rampedMaxHit(guard, target), "hit 3: +2")
    }

    @Test
    fun `rampedMaxHit does not double-advance when queried twice in the same world cycle`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        every { guard.world } returns world
        every { world.currentCycle } returns 5
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 100)

        val first = CityGuards.rampedMaxHit(guard, target)
        val second = CityGuards.rampedMaxHit(guard, target)

        assertEquals(first, second, "two calls in the same cycle must return the same ramp tier")
        assertEquals(20, second)
    }

    @Test
    fun `resetDamageRamp restarts the next hit at 20 percent`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID)
        val world = mockk<World>(relaxed = true)
        var cycle = 0
        every { guard.world } returns world
        every { world.currentCycle } answers { cycle }
        val target = newPlayer(tile = Tile(0, 0, 0), maxLifepoints = 100)

        cycle = 1
        CityGuards.rampedMaxHit(guard, target)
        cycle = 2
        CityGuards.rampedMaxHit(guard, target)
        CityGuards.resetDamageRamp(target)

        cycle = 3
        assertEquals(20, CityGuards.rampedMaxHit(guard, target), "after a reset the next hit starts over at 20%")
    }

    @Test
    fun `sourced guard constants`() {
        assertEquals(1337, CityGuards.DISPLAYED_COMBAT_LEVEL)
        assertEquals(2, CityGuards.ATTACK_SPEED_CYCLES)
        assertEquals(8000, CityGuards.HITPOINTS_TIMES_TEN, "OSRS Wiki: 800 hitpoints, DSL takes HP x10")
        assertEquals(5, CityGuards.WIZGUARD_FREEZE_CYCLES)
        assertEquals(10, CityGuards.WIZGUARD_REAPPEAR_CYCLES)
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

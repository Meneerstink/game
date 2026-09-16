package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.SkullIcon
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman guard coverage for [CityGuards]'s core predicates, the two-guard cap and the
 * consecutive-hit damage ramp (OSRS Wiki "Guard (Deadman Mode)", owner 2026-09-17). Reactive
 * spawn/despawn needs a real World/chunk/collision stack and stays a live-retest item; this file
 * covers the pure logic every guard code path depends on.
 */
class CityGuardsTests {
    private val grandExchange = Tile(3165, 3487, 0)

    @Test
    fun `isGuard recognises the three imported OSRS Deadman guard ids and nothing else`() {
        assertTrue(CityGuards.isGuard(npc(CityGuards.MELEE_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.RANGED_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.WIZGUARD_ID)))
        assertFalse(CityGuards.isGuard(npc(1145)), "the ordinary 667 Ardougne guard is no longer a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(3231)))
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
        val guard = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        val deathZone = Tile(3094, 3491, 0) // Edgeville bank: a hotspot, not a guarded city

        val skulled = newPlayer(tile = deathZone, skulled = true)
        assertFalse(CityGuards.mayAttack(guard, skulled))
    }

    @Test
    fun `mayAttack requires a red skull even inside a guarded city`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        val unskulled = newPlayer(tile = grandExchange, skulled = false)

        assertFalse(CityGuards.mayAttack(guard, unskulled))
    }

    @Test
    fun `mayAttack refuses a guard that itself stands outside every guarded zone`() {
        val outside = npc(CityGuards.MELEE_GUARD_ID, tile = Tile(3300, 3500, 0)) // Lumber Yard
        val skulled = newPlayer(tile = grandExchange, skulled = true)

        assertFalse(CityGuards.mayAttack(outside, skulled), "guards never fight from outside a safe zone")
    }

    @Test
    fun `mayAttack is true for a skulled player inside every guarded zone`() {
        GuardedZones.ZONES.forEach { zone ->
            val guard = npc(CityGuards.MELEE_GUARD_ID, tile = zone.sample)
            assertTrue(CityGuards.mayAttack(guard, newPlayer(tile = zone.sample, skulled = true)), "${zone.name} must be guarded")
        }
    }

    @Test
    fun `never more than two guards may attack one player`() {
        val target = newPlayer(tile = grandExchange, skulled = true)
        val first = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        val second = npc(CityGuards.RANGED_GUARD_ID, tile = grandExchange)
        val third = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)

        assertTrue(CityGuards.mayAttack(first, target))
        assertTrue(CityGuards.mayAttack(second, target))
        assertFalse(CityGuards.mayAttack(third, target), "a third guard must be refused")
        assertTrue(CityGuards.mayAttack(first, target), "a guard already on the player keeps its slot")
        assertEquals(2, CityGuards.engagedGuardCount(target))
    }

    @Test
    fun `a guard that stopped fighting frees its slot for another guard`() {
        val target = newPlayer(tile = grandExchange, skulled = true)
        val world = target.world
        var cycle = 10
        every { world.currentCycle } answers { cycle }
        val first = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        val second = npc(CityGuards.RANGED_GUARD_ID, tile = grandExchange)
        val third = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        assertTrue(CityGuards.mayAttack(first, target))
        assertTrue(CityGuards.mayAttack(second, target))
        // Both are really fighting the target.
        first.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(target)
        second.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(target)
        cycle = 20
        assertFalse(CityGuards.mayAttack(third, target))

        // The first guard lets go (target no longer its combat focus): after the grace window its slot is free.
        first.attr.remove(COMBAT_TARGET_FOCUS_ATTR)
        cycle = 30
        assertTrue(CityGuards.mayAttack(third, target))
    }

    @Test
    fun `release clears the cap bookkeeping`() {
        val target = newPlayer(tile = grandExchange, skulled = true)
        assertTrue(CityGuards.mayAttack(npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange), target))
        assertTrue(CityGuards.mayAttack(npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange), target))
        CityGuards.release(target)
        assertEquals(0, CityGuards.engagedGuardCount(target))
    }

    @Test
    fun `every random spawn combination has at most two guards and at least one damaging guard`() {
        assertTrue(CityGuards.SPAWN_COMBINATIONS.isNotEmpty())
        CityGuards.SPAWN_COMBINATIONS.forEach { combo ->
            assertTrue(combo.size in 1..CityGuards.MAX_GUARDS_PER_PLAYER, "$combo exceeds the cap")
            assertEquals(combo.size, combo.distinct().size, "$combo repeats a kind")
            assertTrue(combo.any { it == CityGuards.Kind.MELEE || it == CityGuards.Kind.RANGED }, "$combo would only freeze")
        }
        // Every kind the owner named is reachable: a lone ranger, a lone melee guard, and the Wizguard.
        assertTrue(CityGuards.SPAWN_COMBINATIONS.contains(listOf(CityGuards.Kind.RANGED)))
        assertTrue(CityGuards.SPAWN_COMBINATIONS.contains(listOf(CityGuards.Kind.MELEE)))
        assertTrue(CityGuards.SPAWN_COMBINATIONS.any { CityGuards.Kind.MAGE in it })
        for (roll in 0 until 20) {
            assertTrue(CityGuards.pickCombination(roll) in CityGuards.SPAWN_COMBINATIONS)
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
        assertEquals(2, CityGuards.MAX_GUARDS_PER_PLAYER)
    }

    private fun npc(
        id: Int,
        tile: Tile = Tile(0, 0, 0),
    ): Npc {
        val n = mockk<Npc>(relaxed = true)
        every { n.id } returns id
        every { n.tile } returns tile
        every { n.isActive() } returns true
        every { n.attr } returns AttributeMap()
        return n
    }

    private fun newPlayer(
        tile: Tile,
        skulled: Boolean = false,
        maxLifepoints: Int = 990,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.currentCycle } returns 0
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.skullIcon } returns if (skulled) SkullIcon.RED.id else SkullIcon.NONE.id
        every { player.getMaximumLifepoints() } returns maxLifepoints
        every { player.attr } returns AttributeMap()
        return player
    }
}

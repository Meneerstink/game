package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
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
    fun `isGuard recognises the imported OSRS Deadman guards and the owner's three named posts, nothing else`() {
        assertTrue(CityGuards.isGuard(npc(CityGuards.MELEE_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.RANGED_GUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.WIZGUARD_ID)))
        assertTrue(CityGuards.isGuard(npc(CityGuards.THIRD_AGE_RANGER_ID)), "owner pin list: npc 14404 third age ranger")
        assertTrue(CityGuards.isGuard(npc(CityGuards.THIRD_AGE_MAGE_ID)), "owner pin list: npc 14405 third age mage")
        assertTrue(CityGuards.isGuard(npc(CityGuards.LUCIEN_ID)), "owner pin list: 14256 Lucien")
        assertFalse(CityGuards.isGuard(npc(1145)), "the ordinary 667 Ardougne guard is no longer a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(3231)))
        assertFalse(CityGuards.isGuard(npc(9999)))
    }

    @Test
    fun `every guard id has exactly one attack style and the owner's nine tiles are all posted`() {
        val styled = CityGuards.MELEE_GUARD_IDS + CityGuards.RANGED_GUARD_IDS + CityGuards.MAGE_GUARD_IDS
        assertEquals(styled.size, CityGuards.MELEE_GUARD_IDS.size + CityGuards.RANGED_GUARD_IDS.size + CityGuards.MAGE_GUARD_IDS.size, "an id must not sit in two style sets")
        assertEquals(CityGuards.GUARD_IDS, styled + CityGuards.WIZGUARD_ID)
        val owner =
            listOf(
                Triple(2588, 3341, CityGuards.THIRD_AGE_RANGER_ID),
                Triple(2612, 3341, CityGuards.THIRD_AGE_RANGER_ID),
                Triple(2614, 3101, CityGuards.THIRD_AGE_RANGER_ID),
                Triple(3187, 3446, null), Triple(3186, 3432, CityGuards.THIRD_AGE_MAGE_ID),
                Triple(3164, 3469, null), Triple(2939, 3356, CityGuards.THIRD_AGE_MAGE_ID),
                Triple(2966, 3399, null), Triple(3006, 3388, null), Triple(3006, 3326, null),
                Triple(3237, 3225, CityGuards.LUCIEN_ID), Triple(3218, 3251, null),
            )
        owner.forEach { (x, z, id) ->
            val post = GuardPosts.ALL.firstOrNull { it.tile.x == x && it.tile.z == z }
            assertTrue(post != null, "owner tile $x,$z must be a guard post")
            assertEquals(id, post!!.npcId, "owner tile $x,$z npc")
        }
        assertEquals(8, CityGuards.PATROL_RADIUS, "owner: alle guards 8 tiles kunnen roamen")
    }

    @Test
    fun `patrol radius is bounded to eight tiles including diagonals`() {
        val post = Tile(3200, 3400, 0)
        assertTrue(CityGuards.isWithinPatrolRadius(post, Tile(3208, 3400, 0)))
        assertFalse(CityGuards.isWithinPatrolRadius(post, Tile(3208, 3408, 0)), "a diagonal corner is more than 8 tiles away")
        assertFalse(CityGuards.isWithinPatrolRadius(post, Tile(3208, 3400, 1)), "patrol cannot change plane")
    }

    @Test
    fun `mayPursue keeps a guard on a skulled intruder anywhere inside the zone and drops it outside`() {
        val guard = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        assertTrue(CityGuards.mayPursue(guard, newPlayer(tile = Tile(3200, 3430, 0), skulled = true)), "far side of Varrock, still the same zone")
        assertFalse(CityGuards.mayPursue(guard, newPlayer(tile = Tile(3094, 3491, 0), skulled = true)), "Edgeville is a death zone")
        assertFalse(CityGuards.mayPursue(guard, newPlayer(tile = grandExchange, skulled = false)), "unskulled: let go")
        assertFalse(CityGuards.mayPursue(guard, npc(9999, tile = grandExchange)), "npcs are never pursued")
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

    @Test
    fun `an ordinary Guard posted inside any guarded zone roams, one outside or with another name does not`() {
        fun ordinary(name: String, spawn: Tile): Npc {
            val n = npc(id = 9, tile = spawn)
            every { n.spawnTile } returns spawn
            every { n.def.name } returns name
            return n
        }
        GuardedZones.ZONES.forEach { zone ->
            assertTrue(CityGuards.isOrdinaryZoneGuard(ordinary("Guard", zone.sample)), "${zone.name}: a posted Guard must roam")
            assertFalse(CityGuards.isOrdinaryZoneGuard(ordinary("Man", zone.sample)), "${zone.name}: only guards")
        }
        assertFalse(CityGuards.isOrdinaryZoneGuard(ordinary("Guard", Tile(3300, 3500, 0))), "outside every zone")
        val deadman = npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange)
        every { deadman.spawnTile } returns grandExchange
        every { deadman.def.name } returns "Guard"
        assertFalse(CityGuards.isOrdinaryZoneGuard(deadman), "Deadman guards patrol from the leash, not the generic walk")
    }

    @Test
    fun `an npc entering a local list gets its block segment whenever it faces something or overrides its level`() {
        fun withBuffer(init: gg.rsmod.game.sync.block.UpdateBlockBuffer.() -> Unit) = gg.rsmod.game.sync.block.UpdateBlockBuffer().apply(init)
        val task = gg.rsmod.game.sync.task.NpcSynchronizationTask
        assertFalse(task.hasPersistentBlockState(withBuffer { }), "nothing persistent: no segment needed")
        assertTrue(task.hasPersistentBlockState(withBuffer { facePawnIndex = 32768 + 1 }), "facing a player while the mask is clean")
        assertTrue(task.hasPersistentBlockState(withBuffer { faceDegrees = (3164 shl 16) or 3480 }), "facing a tile")
        assertTrue(task.hasPersistentBlockState(withBuffer { combatLevel = CityGuards.DISPLAYED_COMBAT_LEVEL }), "1337 level override")
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
        // The skull state is the running skull timer (PvpSkull.isSkulled), never an icon id.
        every { player.timers } returns TimerMap().also { if (skulled) it[SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES }
        every { player.skullIcon } returns if (skulled) SkullIcon.DMM_VERY_LOW_RISK.id else SkullIcon.NONE.id
        every { player.getMaximumLifepoints() } returns maxLifepoints
        every { player.attr } returns AttributeMap()
        return player
    }
}

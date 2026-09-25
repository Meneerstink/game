package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.SkullIcon
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman guard coverage for [CityGuards]'s core predicates, the per-zone OSRS variants, player
 * attacks on guards and the consecutive-hit damage ramp (OSRS Wiki "Guard (Deadman Mode)"; owner
 * 2026-09-19: 100 % OSRS). Reactive spawn/despawn needs a real World/chunk/collision stack and stays
 * a live-retest item; this file covers the pure logic every guard code path depends on.
 */
class CityGuardsTests {
    private val grandExchange = Tile(3165, 3487, 0)

    @Test
    fun `isGuard recognises exactly the imported OSRS Deadman guard variants and the Wizguard`() {
        val imported = (14407..14409) + (14414..14435)
        assertEquals(imported.toSet(), CityGuards.GUARD_IDS, "tx-20260919-150209 imported ids")
        imported.forEach { assertTrue(CityGuards.isGuard(npc(it)), "npc $it") }
        assertFalse(CityGuards.isGuard(npc(14404)), "Third Age Ranger is not a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(14405)), "Third Age Mage is not a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(14256)), "Lucien is not a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(1145)), "the ordinary 667 Ardougne guard is no longer a Deadman guard")
        assertFalse(CityGuards.isGuard(npc(3231)))
        assertFalse(CityGuards.isGuard(npc(9999)))
    }

    @Test
    fun `every guard id has exactly one attack style and every guarded zone has its OSRS variant`() {
        assertTrue((CityGuards.MELEE_GUARD_IDS intersect CityGuards.RANGED_GUARD_IDS).isEmpty(), "an id must not sit in two style sets")
        assertEquals(CityGuards.GUARD_IDS, CityGuards.MELEE_GUARD_IDS + CityGuards.RANGED_GUARD_IDS + CityGuards.WIZGUARD_ID)
        GuardedZones.ZONES.filter { it.name != "Tutorial Island" }.forEach { zone ->
            assertTrue(zone.name in CityGuards.VARIANTS, "${zone.name} has no OSRS guard variant")
        }
        // Wiki: Seers' Village and Catherby only have a ranged guard version.
        assertEquals(null, CityGuards.VARIANTS.getValue("Seers' Village bank").melee)
        assertEquals(null, CityGuards.VARIANTS.getValue("Catherby bank").melee)
        assertEquals(CityGuards.VARIANTS.getValue("Lumbridge"), CityGuards.variantFor("Tutorial Island"))
        assertEquals(8, CityGuards.PATROL_RADIUS, "owner: alle guards 8 tiles kunnen roamen")
    }

    @Test
    fun `a player may attack a guard only while skulled inside the guarded area, never the Wizguard`() {
        val deathZone = Tile(3094, 3491, 0)
        (CityGuards.MELEE_GUARD_IDS + CityGuards.RANGED_GUARD_IDS).forEach { id ->
            val guard = npc(id, tile = grandExchange)
            assertTrue(CityGuards.mayBeAttackedBy(guard, newPlayer(tile = grandExchange, skulled = true)), "npc $id: skulled inside")
            assertFalse(CityGuards.mayBeAttackedBy(guard, newPlayer(tile = grandExchange, skulled = false)), "npc $id: unskulled")
            assertFalse(CityGuards.mayBeAttackedBy(guard, newPlayer(tile = deathZone, skulled = true)), "npc $id: attacker outside")
        }
        assertFalse(CityGuards.mayBeAttackedBy(npc(CityGuards.WIZGUARD_ID, tile = grandExchange), newPlayer(tile = grandExchange, skulled = true)))
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
    fun `any number of guards may attack one skulled player`() {
        // Wiki: "Multiple guards are able to attack the player regardless of the location's multicombat area status."
        val target = newPlayer(tile = grandExchange, skulled = true)
        repeat(6) { i ->
            val id = if (i % 2 == 0) CityGuards.MELEE_GUARD_ID else CityGuards.RANGED_GUARD_ID
            assertTrue(CityGuards.mayAttack(npc(id, tile = grandExchange), target), "guard ${i + 1} must be allowed")
        }
    }

    @Test
    fun `the reactive spawn is one melee or one ranged guard plus the Wizguard`() {
        // Wiki: the spawned guard "can be either a melee or a ranged one"; Wizguards freeze every skulled intruder.
        assertEquals(
            listOf(listOf(CityGuards.Kind.MELEE, CityGuards.Kind.MAGE), listOf(CityGuards.Kind.RANGED, CityGuards.Kind.MAGE)),
            CityGuards.SPAWN_COMBINATIONS,
        )
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
    fun `Audit D-12 - an intruder without a live melee or ranged guard is retried, one with a guard is not`() {
        assertTrue(CityGuards.needsPhysicalGuard(null), "no guard placed yet")
        assertTrue(CityGuards.needsPhysicalGuard(emptyList()), "the pool had none free: the plan must not count as engaged")
        assertTrue(CityGuards.needsPhysicalGuard(listOf(false)), "the guard is gone")
        assertFalse(CityGuards.needsPhysicalGuard(listOf(false, true)))
        assertTrue(CityGuards.GUARD_RETRY_CYCLES in 1..10, "retried within a few seconds")
    }

    @Test
    fun `Audit D-10 - a loot-key carrier without a skull timer is a skulled intruder`() {
        val carrier = newPlayer(tile = grandExchange)
        val inventory = gg.rsmod.game.model.container.ItemContainer(gg.rsmod.game.fs.DefinitionSet(), gg.rsmod.game.model.container.key.INVENTORY_KEY)
        every { carrier.inventory } returns inventory
        assertFalse(CityGuards.isSkulledIntruder(carrier))
        inventory[0] = gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.LOOT_KEY, 1)
        assertTrue(CityGuards.isSkulledIntruder(carrier))
        assertTrue(CityGuards.mayAttack(npc(CityGuards.MELEE_GUARD_ID, tile = grandExchange), carrier))
    }

    @Test
    fun `sourced guard constants`() {
        assertEquals(1337, CityGuards.DISPLAYED_COMBAT_LEVEL)
        assertEquals(2, CityGuards.ATTACK_SPEED_CYCLES)
        assertEquals(8000, CityGuards.HITPOINTS_TIMES_TEN, "OSRS Wiki: 800 hitpoints, DSL takes HP x10")
        assertEquals(800, CityGuards.ATTACK_LEVEL)
        assertEquals(400, CityGuards.STRENGTH_LEVEL)
        assertEquals(300, CityGuards.DEFENCE_LEVEL)
        assertEquals(1, CityGuards.MAGIC_LEVEL)
        assertEquals(1, CityGuards.RANGED_LEVEL)
        assertEquals(60, CityGuards.ATTACK_BONUS)
        assertEquals(7, CityGuards.STRENGTH_BONUS)
        assertEquals(8, CityGuards.DEFENCE_STAB_BONUS)
        assertEquals(9, CityGuards.DEFENCE_SLASH_BONUS)
        assertEquals(7, CityGuards.DEFENCE_CRUSH_BONUS)
        assertEquals(0, CityGuards.DEFENCE_MAGIC_BONUS)
        assertEquals(8, CityGuards.DEFENCE_RANGED_BONUS)
        assertEquals(5, CityGuards.WIZGUARD_FREEZE_CYCLES)
        assertEquals(10, CityGuards.WIZGUARD_REAPPEAR_CYCLES)
        assertEquals(30, CityGuards.WIZGUARD_MAX_HIT, "OSRS Wiki Ice Barrage base max hit")
        assertEquals("You probably don't want to do that.", CityGuards.ATTACK_REFUSED_MESSAGE)
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
        // Player.getMaximumLifepoints() is the displayed 1:1 hitpoints level (99, not 990).
        maxLifepoints: Int = 99,
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

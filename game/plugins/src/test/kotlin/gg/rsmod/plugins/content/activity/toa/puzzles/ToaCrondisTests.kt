package gg.rsmod.plugins.content.activity.toa.puzzles

import kotlin.test.*
import gg.rsmod.game.model.*
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.plugins.content.activity.toa.*
import io.mockk.*

class ToaCrondisTests {
    @Test fun `world queues advance a room once per cycle and stop immediately after wipe`() {
        lateinit var room: CrondisRoom
        ToaPuzzleFixture(1, ToaPath.CRONDIS) { CrondisRoom(it).also { room = it } }.use { f ->
            assertEquals(0, room.tick)
            f.advance(); assertEquals(1, room.tick)
            f.advance(3); assertEquals(4, room.tick)
            val run = f.raid.progress.current!!
            run.wipe(f.cycle); f.cycle++; f.raid.progress.cycle(f.cycle)
            f.advance(5); assertEquals(4, room.tick)
        }
    }

    @Test fun `death and logout during carrying invalidate personal water while the other player continues`() {
        lateinit var room: CrondisRoom
        ToaPuzzleFixture(2, ToaPath.CRONDIS) { CrondisRoom(it).also { room = it } }.use { f ->
            val p = f.players.first()
            p.inventory.add(assertNotNull(ToaIds.item(CrondisRoom.CONTAINER)))
            f.advance()
            assertTrue(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[0]), true))
            assertEquals(100.0, room.puzzle.water(p.uid))
            f.dead += p.uid; f.advance()
            assertEquals(0.0, room.puzzle.water(p.uid)); assertFalse(room.eligible(p))
            assertFalse(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[1]), true))
            f.dead -= p.uid; f.advance()
            assertTrue(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[1]), true))
            f.world.players.entries[1] = null; f.advance()
            assertEquals(0.0, room.puzzle.water(p.uid)); assertFalse(room.eligible(p))
            assertTrue(room.eligible(f.players[1])); assertTrue(room.live)
        }
    }

    @Test fun `real room starts and watering actions open only the boss gate for solo and eight players`() {
        for (size in listOf(1, 8)) {
            lateinit var room: CrondisRoom
            ToaPuzzleFixture(size, ToaPath.CRONDIS) { CrondisRoom(it).also { room = it } }.use { f ->
                assertEquals(1, f.entities.filterIsInstance<Npc>().size)
                val palm = f.entities.filterIsInstance<Npc>().single()
                val source = f.entities.filterIsInstance<gg.rsmod.game.model.entity.GroundItem>().first()
                f.players.forEach { p ->
                    assertTrue(room.canTake(p, source))
                    assertTrue(p.inventory.add(source.item).hasSucceeded())
                    assertFalse(room.canTake(p, source))
                }
                assertNull(f.raid.progress.boss(ToaPath.CRONDIS))
                var rounds = 0
                while (!room.puzzle.complete && rounds < 20) {
                    val p = f.players[rounds % size]
                    f.at(p, CrondisRoom.WATERFALLS[rounds % 4].transform(1, 0))
                    assertTrue(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[rounds % 4]), itemUsed = true))
                    assertFalse(room.water(p, palm, itemUsed = true)) //A duplicate tick cannot fill AND pour.
                    f.advance()
                    f.at(p, CrondisRoom.PALM_TILE.transform(-1, 0))
                    assertTrue(room.water(p, palm, itemUsed = true))
                    f.advance()
                    rounds++
                }
                assertTrue(room.puzzle.complete)
                assertTrue(f.raid.progress.challengeCleared(ToaPath.CRONDIS))
                assertTrue(f.entities.isEmpty(), "scope must remove NPCs, source items and dynamic locs on clear")
                assertFalse(room.water(f.players.first(), palm, itemUsed = true))
                assertNotNull(f.raid.progress.boss(ToaPath.CRONDIS))
            }
        }
    }

    @Test fun `real room wipe cancels delayed filling and refuses stale objects with full inventory`() {
        lateinit var room: CrondisRoom
        ToaPuzzleFixture(1, ToaPath.CRONDIS) { CrondisRoom(it).also { room = it } }.use { f ->
            val p = f.players.single()
            val source = f.entities.filterIsInstance<gg.rsmod.game.model.entity.GroundItem>().first()
            repeat(28) { p.inventory[it] = gg.rsmod.game.model.item.Item(100 + it) }
            assertFalse(p.inventory.add(source.item).hasSucceeded())
            assertFalse(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[0])))
            p.inventory[0] = gg.rsmod.game.model.item.Item(source.item)
            assertTrue(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[0])))
            val run = f.raid.progress.current!!
            assertTrue(run.wipe(f.cycle))
            f.cycle++; f.raid.progress.cycle(f.cycle)
            f.advance(3)
            assertTrue(room.puzzle.closed); assertEquals(0.0, room.puzzle.water(p.uid))
            assertTrue(f.entities.isEmpty()); assertNull(f.raid.progress.boss(ToaPath.CRONDIS))
            assertFalse(room.fill(p, f.obj(45398, CrondisRoom.WATERFALLS[0]), true))
        }
    }

    private class PointsRoom(raid: ToaRaid) : ToaPuzzleRoom(raid) {
        override fun startPuzzle() = true
        override fun cycle() {}
        override fun closePuzzle() {}
        fun earn(uid: PlayerUID, amount: Int) { award(uid, amount) }
    }

    @Test fun `puzzle points reach the real ledger exactly once on clear and not on a wipe`() {
        for (clear in listOf(false, true)) {
            val world = mockk<World>(relaxed = true)
            val map = mockk<InstancedMap>(relaxed = true)
            every { map.area } returns Area(6400, 0, 6848, 448)
            lateinit var room: PointsRoom
            val registration = ToaRooms.register(ToaRoomKey(ToaPath.CRONDIS, false)) { PointsRoom(it).also { created -> room = created } }
            val uid = PlayerUID(1)
            val raid = ToaRaid(1, listOf(uid), ToaRaidSettings(), map, world)
            try {
                val run = raid.progress.challenge(ToaPath.CRONDIS, 0)!!
                room.start(run); room.earn(uid, 100)
                assertEquals(0, raid.points.roomPoints(uid))
                if (clear) {
                    assertTrue(run.clear(1)); room.clear(run)
                    assertEquals(5400, raid.points.total(uid)) //5000 start +100 earned +300 solo MVP.
                } else {
                    run.wipe(1); raid.progress.cycle(2)
                    assertEquals(5000, raid.points.total(uid))
                }
                room.earn(uid, 100); assertEquals(0, raid.points.roomPoints(uid))
            } finally { raid.close(); registration.close() }
        }
    }

    @Test fun `default Crondis profile is installed and solo and eight player water states remain solvable`() {
        val profile = assertNotNull(ToaPuzzleReferences.crondis)
        assertTrue(profile.trapEvents.value.isNotEmpty())
        assertTrue(profile.crocodileSpawns.value.isNotEmpty())
        for (size in listOf(1, 8)) {
            val state = CrondisPuzzle<Int>(size)
            repeat(size) { state.container(it, Any()) }
            var cycle = 0
            while (!state.complete && cycle < 10000) {
                val player = cycle % size
                if (state.fill(player, cycle % 4, cycle, profile.refillCycles.value[size - 1])) state.waterPalm(player, cycle + 1)
                cycle += 2
            }
            assertTrue(state.complete)
        }
    }
    @Test fun `world defaults cannot overwrite a scoped puzzle actor stats or bonuses`() {
        val world = mockk<World>(relaxed = true)
        val map = mockk<InstancedMap>(relaxed = true)
        every { map.area } returns Area(6400, 0, 6848, 448)
        val raid = ToaRaid(1, listOf(PlayerUID(1)), ToaRaidSettings(), map, world)
        val run = raid.progress.challenge(ToaPath.CRONDIS, 0)!!
        val scope = raid.scope(run)!!
        val actor = mockk<Npc>(relaxed = true)
        every { actor.world } returns world; every { actor.tile } returns Tile(6420, 160)
        var definition = NpcCombatDef.DEFAULT
        every { actor.combatDef } answers { definition }
        every { actor.combatDef = any() } answers { definition = firstArg() }
        val bonuses = IntArray(18) { 999 }
        every { actor.equipmentBonuses } returns bonuses
        every { world.spawn(actor) } answers { definition = NpcCombatDef.DEFAULT; true }
        assertTrue(ToaPuzzleActors.spawn(scope, actor, CrondisRoom.CROCODILE_DEF))
        assertEquals(30, actor.combatDef.lifepoints)
        assertEquals(listOf(150, 60, 100, 30, 1), actor.combatDef.stats)
        assertEquals(350, bonuses[6]); assertEquals(0, bonuses.last())
        assertFalse(actor.combatDef.completesEncounter)
        assertEquals(listOf(actor), scope.spawned)
        verify { actor.setCurrentLifepoints(30); actor.stats.setCurrentLevel(0, 150) }
        scope.close(); verify(exactly = 1) { world.remove(actor) }
    }

    @Test fun `failed actor spawn cannot configure an unowned actor`() {
        val scope = mockk<ToaRoomScope>()
        val actor = mockk<Npc>(relaxed = true)
        every { scope.spawn(actor) } returns false
        assertFalse(ToaPuzzleActors.spawn(scope, actor, CrondisRoom.CROCODILE_DEF))
        verify(exactly = 0) { actor.combatDef = any() }
        verify(exactly = 0) { actor.setCurrentLifepoints(any()) }
    }
    @Test fun `solo and eight player palms can be completed with conserved water`() {
        for (size in listOf(1, 8)) {
            val state = CrondisPuzzle<Int>(size)
            assertEquals(175 + 125 * (size - 1), state.requiredWater)
            (0 until size).forEach { state.container(it, Any()) }
            var cycle = 0
            var delivered = 0.0
            while (!state.complete) {
                val player = cycle % size
                assertTrue(state.fill(player, player % 4, cycle, 1))
                assertEquals(0.0, state.waterPalm(player, cycle), "same tick action must not pour twice")
                delivered += state.waterPalm(player, ++cycle)
                cycle++
            }
            assertEquals(state.requiredWater.toDouble(), delivered)
            assertEquals(4, state.palmStage)
            assertEquals(0.0, state.waterPalm(0, cycle))
        }
    }

    @Test fun `two players cannot take the same waterfall until its sourced refill`() {
        val state = CrondisPuzzle<Int>(8)
        state.container(1, Any()); state.container(2, Any())
        assertTrue(state.fill(1, 0, 10, 12))
        assertFalse(state.fill(2, 0, 10, 12))
        assertFalse(state.fill(2, 0, 21, 12))
        assertTrue(state.fill(2, 0, 22, 12))
    }

    @Test fun `destroyed container replacement cannot recover water and duplicate clicks cannot create it`() {
        val state = CrondisPuzzle<Int>(1)
        val container = Any()
        assertFalse(state.fill(1, 0, 0, 1))
        state.container(1, container)
        assertTrue(state.fill(1, 0, 0, 1))
        state.container(1, container)
        assertEquals(100, state.displayedWater(1))
        assertFalse(state.fill(1, 1, 0, 1))
        state.container(1, Any())
        assertEquals(0, state.displayedWater(1))
        assertEquals(0.0, state.waterPalm(1, 1))
    }

    @Test fun `trap history is personal capped and expires at thirty seconds`() {
        val state = CrondisPuzzle<Int>(8)
        repeat(9) { state.obstacle(1, it) }
        assertEquals(36, state.crocodileDamage(1, 9, false))
        assertEquals(12, state.crocodileDamage(1, 9, true))
        assertEquals(18, state.crocodileDamage(2, 9, false))
        assertEquals(21, state.crocodileDamage(1, 57, false))
        assertEquals(18, state.crocodileDamage(1, 58, false))
    }

    @Test fun `spilling retains fractions and bite below fifteen percent uses ten percent`() {
        val state = CrondisPuzzle<Int>(1)
        state.container(1, Any()); state.fill(1, 0, 0, 1)
        repeat(3) { state.obstacle(1, it) }
        assertEquals(12.5, state.water(1))
        state.crocodileBite(1)
        assertEquals(11.25, state.water(1))
        repeat(20) { state.obstacle(1, it) }
        assertEquals(1, state.displayedWater(1))
        assertTrue(state.empty(1)); assertEquals(0, state.displayedWater(1))
    }

    @Test fun `destroying a container does not cleanse the personal crocodile debuff`() {
        val state = CrondisPuzzle<Int>(1)
        state.container(1, Any()); state.fill(1, 0, 0, 1); state.obstacle(1, 1)
        state.containerGone(1)
        assertEquals(0.0, state.water(1))
        assertEquals(21, state.crocodileDamage(1, 2, false))
        assertEquals(CrondisPuzzle.Target.Player(1), state.target(listOf(1), setOf(1)))
        state.container(1, Any())
        assertEquals(21, state.crocodileDamage(1, 3, false))
        assertNull(state.target(listOf(1), setOf(1)))
    }

    @Test fun `watering cans add one point and refused or duplicate actions never consume charges`() {
        val state = CrondisPuzzle<Int>(1)
        var charges = 20
        assertTrue(state.waterFromCan(1, 0) { charges -= 10; true })
        assertEquals(1.0, state.growth); assertEquals(10, charges)
        assertFalse(state.waterFromCan(1, 0) { error("double click may not consume") })
        assertFalse(state.waterFromCan(1, 1) { false }); assertEquals(1.0, state.growth)
        assertTrue(state.waterFromCan(1, 2) { charges -= 10; true })
        assertEquals(2.0, state.growth); assertEquals(0, charges)
        state.close(); assertFalse(state.waterFromCan(1, 3) { error("stale callback") })
    }

    @Test fun `specific crocodile targeting excludes empty container and honours watered palm`() {
        val state = CrondisPuzzle<Int>(1)
        assertEquals(CrondisPuzzle.Target.Player(1), state.target(listOf(1), setOf(1)))
        state.container(1, Any())
        assertNull(state.target(listOf(1), setOf(1)))
        state.fill(1, 0, 0, 1)
        assertEquals(CrondisPuzzle.Target.Player(1), state.target(listOf(1), emptySet()))
        state.waterPalm(1, 1)
        assertEquals(CrondisPuzzle.Target.Palm, state.target(listOf(1), setOf(1)))
        assertEquals(100.0, state.eatPalm(120.0)); assertEquals(0.0, state.growth)
    }

    @Test fun `wipe invalidates callbacks and new instance has clean growth containers and waterfalls`() {
        val state = CrondisPuzzle<Int>(1)
        state.container(1, Any()); state.fill(1, 0, 0, 10); state.waterPalm(1, 1)
        state.close()
        assertFalse(state.fill(1, 1, 2, 1)); assertEquals(0.0, state.waterPalm(1, 3))
        assertNull(state.target(listOf(1), setOf(1)))
        val retry = CrondisPuzzle<Int>(1)
        assertFalse(retry.complete); assertEquals(0.0, retry.growth)
        assertTrue(retry.waterfallAvailable(0, 0))
    }
}

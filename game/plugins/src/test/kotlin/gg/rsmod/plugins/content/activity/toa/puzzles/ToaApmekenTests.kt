package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.game.message.impl.MapAnimMessage
import gg.rsmod.game.message.impl.LocAnimMessage
import gg.rsmod.game.message.impl.LocAddChangeMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Hit
import io.mockk.*
import kotlin.test.*

class ToaApmekenTests {
    @Test fun `disconnecting the sight holder during corruption transfers ownership and leaves a completable task`() {
        val saved = ApmekenReferences.profile
        val defaults = ApmekenDefaults.reference()
        ApmekenReferences.profile = defaults.copy(actors = ToaPuzzleReferences.Evidence(
            defaults.actors.value.mapValues { (_, actor) -> actor.copy(range = 1, hitDelay = 1000) }, "isolate participant loss"))
        try {
            lateinit var room: ApmekenRoom
            ToaPuzzleFixture(2, ToaPath.APMEKEN) { ApmekenRoom(it).also { room = it } }.use { f ->
                f.advance()
                val old = f.players.first(); val survivor = f.players[1]
                assertEquals(old.uid, room.puzzle.sight)
                assertTrue(room.takePotions(survivor))
                assertTrue(room.puzzle.beginTask(ApmekenPuzzle.Action.CORRUPTION, 1, emptyMap(), room.tick + 13))
                f.raid.disconnect(old); f.advance()
                assertEquals(survivor.uid, room.puzzle.sight)
                assertFalse(room.eligible(old)); assertFalse(room.pour(old))
                assertTrue(room.pour(survivor)); assertNull(room.puzzle.task)
                f.raid.reconnect(old); f.advance()
                assertFalse(room.eligible(old), "a spectator cannot regain task ownership")
                assertEquals(survivor.uid, room.puzzle.sight)
                assertTrue(room.live); assertNull(f.raid.progress.boss(ToaPath.APMEKEN))
            }
        } finally { ApmekenReferences.profile = saved }
    }

    @Test fun `source collision permits the player click route from supplies to every task site`() {
        val geometry = ToaPuzzleSourceCollision("apmeken", Tile(3776, 5248))
        //40-loc-placements2686/15186: antechamber hammer45497 and potion45498; pass45135 then enter from3797,5280.
        for (crate in listOf(Tile(3793, 5284), Tile(3795, 5285)))
            assertTrue(geometry.route(ToaPath.APMEKEN.challengeEntry, crate).success, "supply crate$crate")
        val inside = Tile(3797, 5280)
        for (site in ApmekenRoom.PILLARS) {
            val route = geometry.route(inside, site, 3, 3)
            assertTrue(route.success, "roof support$site")
            //At most10 running ticks from the entry; the supplied task window is13 ticks.
            assertTrue((route.path.size + 1) / 2 < 13, "support$site takes${route.path.size} walking steps")
        }
        for (vent in ApmekenRoom.VENTS) {
            val route = geometry.route(inside, vent, stepOn = true)
            assertTrue(route.success, "vent$vent")
            assertEquals(vent, route.tail)
            assertTrue((route.path.size + 1) / 2 < 13)
        }
        assertTrue(geometry.route(inside, Tile(3806, 5280), stepOn = true).success, "corruption stack west of the statue")
    }

    @Test fun `default timed tasks can be solved through crate repair vent and corruption actions before their deadlines`() {
        val saved = ApmekenReferences.profile
        val defaults = ApmekenDefaults.reference()
        ApmekenReferences.profile = defaults.copy(actors = ToaPuzzleReferences.Evidence(
            defaults.actors.value.mapValues { (_, actor) -> actor.copy(range = 1, hitDelay = 1000) },
            "isolate scheduled task route from projectile flight"))
        try {
            for (size in listOf(1, 2, 8)) {
                lateinit var room: ApmekenRoom
                ToaPuzzleFixture(size, ToaPath.APMEKEN) { ApmekenRoom(it).also { room = it } }.use { f ->
                    f.players.forEach { assertTrue(room.takeHammer(it)) }
                    f.advance()
                    f.players.forEach { assertTrue(room.takePotions(it)) }
                    val potion = assertNotNull(ToaIds.item(ApmekenRoom.POTION))
                    val tasks = if (size == 1) listOf(ApmekenPuzzle.Action.PILLAR, ApmekenPuzzle.Action.VENT)
                        else ApmekenPuzzle.Action.values().toList()
                    for (kind in tasks) {
                        while (room.puzzle.task == null && room.tick < 80) f.advance()
                        val task = assertNotNull(room.puzzle.task, "no scheduled $kind task for$size players")
                        assertEquals(kind, task.type)
                        assertEquals(size, task.required + if (kind == ApmekenPuzzle.Action.CORRUPTION) 1 else 0)
                        val inventoryBefore = f.players.sumOf { it.inventory.getItemCount(potion) }
                        if (kind == ApmekenPuzzle.Action.CORRUPTION) {
                            val holder = f.players.single { it.uid == room.puzzle.sight }
                            //Players stack on the Sight holder; one Pour cleanses the entire stack.
                            f.players.forEach { f.at(it, Tile(3806, 5280)) }
                            assertTrue(room.pour(holder))
                            assertEquals(inventoryBefore - 1, f.players.sumOf { it.inventory.getItemCount(potion) })
                        } else {
                            var member = 0
                            for ((site, uses) in task.remainingSites.toMap()) repeat(uses) {
                                val player = f.players[member++]
                                if (kind == ApmekenPuzzle.Action.PILLAR) {
                                    f.at(player, ApmekenRoom.PILLAR_FRONTS[site])
                                    assertTrue(room.repair(player, f.obj(45494, ApmekenRoom.PILLARS[site])))
                                } else {
                                    f.at(player, ApmekenRoom.VENTS[site])
                                    assertTrue(room.pour(player))
                                }
                            }
                            assertEquals(inventoryBefore - if (kind == ApmekenPuzzle.Action.VENT) size else 0,
                                f.players.sumOf { it.inventory.getItemCount(potion) })
                        }
                        assertTrue(room.tick < task.deadline)
                        assertNull(room.puzzle.task)
                        assertNull(f.raid.progress.boss(ToaPath.APMEKEN), "tasks alone do not clear the eight waves")
                        f.advance()
                    }
                }
            }
        } finally { ApmekenReferences.profile = saved }
    }

    @Test fun `volatile detonation happens once while a lethal hit waits for the engine death phase`() {
        val saved = ApmekenReferences.profile
        val defaults = ApmekenDefaults.reference()
        ApmekenReferences.profile = defaults.copy(actors = ToaPuzzleReferences.Evidence(
            defaults.actors.value.mapValues { (_, actor) -> actor.copy(range = 1, hitDelay = 100) }, "isolate volatile director"))
        try {
            lateinit var room: ApmekenRoom
            ToaPuzzleFixture(1, ToaPath.APMEKEN) { ApmekenRoom(it).also { room = it } }.use { f ->
                every { ToaIds.npcSeq(11716, 9754) } returns 9004
                every { ToaIds.npcSeq(11716, 9756) } returns 9002
                f.advance()
                f.entities.filterIsInstance<Npc>().toList().forEach { it.setCurrentLifepoints(0); f.world.remove(it) }
                f.advance()
                val volatile = room.scope.spawned.single { it.attr[ToaPuzzles.SOURCE] == 11716 }
                assertEquals(listOf(9004), volatile.combatDef.deathAnimation, "an ordinary ranged kill does not play another damaging explosion")
                val p = f.players.single()
                f.at(p, assertNotNull(ToaMap.source(f.map, volatile.tile)).transform(-1, 0))
                f.advance(2) //Charge followed by the explosion; NPC hit processing is deliberately delayed by this fixture.
                assertEquals(listOf(9002), volatile.combatDef.deathAnimation)
                assertEquals(8, volatile.getCurrentLifepoints())
                f.advance(3)
                verify(exactly = 1) { p.addHit(match<Hit> { it.hitmarks.sumOf { mark -> mark.damage } == 7 }) }
            }
        } finally { ApmekenReferences.profile = saved }
    }

    @Test fun `private shaking keeps the real support ID clickable instead of sending an unresolvable fake object`() {
        ToaPuzzleFixture(1, ToaPath.APMEKEN, ::ApmekenRoom).use { f ->
            val viewer = f.players.single()
            every { viewer.lastKnownRegionBase } returns viewer.tile.transform(-48, -48).let { gg.rsmod.game.model.Coordinate(it.x, it.z, it.height) }
            every { ToaIds.seq(9502) } returns 9001
            val tile = ToaMap.instance(f.map, ApmekenRoom.PILLARS.first())
            ToaPuzzleVisuals.pillar(viewer, tile, shaking = true)
            verify(exactly = 1) { viewer.write(match<LocAnimMessage> {
                it.animation == 9001 && it.gameObject.id == 50000 + 45494 && it.gameObject.tile == tile
            }) }
            // any<T>() matches every message (erasure): only a real LocAddChangeMessage may fail this.
            verify(exactly = 0) { viewer.write(match { it is LocAddChangeMessage }) }
        }
    }

    @Test fun `private corruption graphics target the moving player index and reach only the sight viewer`() {
        ToaPuzzleFixture(2, ToaPath.APMEKEN, ::ApmekenRoom).use { f ->
            val viewer = f.players[0]; val target = f.players[1]
            every { ToaIds.spot(2137) } returns 9000 //Synthetic imported visual in the fixture, not a real cache ID.
            ToaPuzzleVisuals.playerSpot(viewer, target, 2137)
            verify(exactly = 1) { viewer.write(match<MapAnimMessage> { it.id == 9000 && it.tile == (2 or (1 shl 28)) }) }
            verify(exactly = 0) { target.write(any<MapAnimMessage>()) }
            ToaPuzzleVisuals.playerSpot(viewer, target, null)
            verify(exactly = 1) { viewer.write(match<MapAnimMessage> { it.id == -1 && it.tile == (2 or (1 shl 28)) }) }
        }
    }

    @Test fun `real adapter spawns every wave and opens the boss only after the final actor is removed`() {
        val saved = ApmekenReferences.profile
        val defaults = ApmekenDefaults.reference()
        //Suppress projectile flight during this lifecycle test; the ordinary directors and actual spawned NPCs still run.
        ApmekenReferences.profile = defaults.copy(actors = ToaPuzzleReferences.Evidence(
            defaults.actors.value.mapValues { (_, actor) -> actor.copy(range = 1, hitDelay = 100) }, "test isolation: actor flight"))
        try {
            for (size in listOf(1, 8)) {
                lateinit var room: ApmekenRoom
                ToaPuzzleFixture(size, ToaPath.APMEKEN) { ApmekenRoom(it).also { room = it } }.use { f ->
                    assertEquals(f.players.first().uid, room.puzzle.sight)
                    assertNull(f.raid.progress.boss(ToaPath.APMEKEN))
                    f.advance()
                    repeat(8) { wave ->
                        assertEquals(wave + 1, room.puzzle.wave)
                        val actors = f.entities.filterIsInstance<Npc>()
                        assertTrue(actors.isNotEmpty())
                        assertTrue(actors.all { it in room.scope.spawned && it.respawnOverride == false })
                        actors.forEach { npc -> npc.setCurrentLifepoints(0); f.world.remove(npc) }
                        f.advance()
                    }
                    assertTrue(f.raid.progress.challengeCleared(ToaPath.APMEKEN))
                    assertTrue(f.entities.isEmpty()); assertTrue(room.puzzle.closed)
                    assertNotNull(f.raid.progress.boss(ToaPath.APMEKEN))
                }
            }
        } finally { ApmekenReferences.profile = saved }
    }

    @Test fun `real adapter handles full crates shared repairs and group stack cleansing without duplicate consumption`() {
        val saved = ApmekenReferences.profile
        val defaults = ApmekenDefaults.reference()
        ApmekenReferences.profile = defaults.copy(actors = ToaPuzzleReferences.Evidence(
            defaults.actors.value.mapValues { (_, actor) -> actor.copy(range = 1, hitDelay = 100) }, "isolate crate and task interactions"))
        try {
            lateinit var room: ApmekenRoom
            ToaPuzzleFixture(8, ToaPath.APMEKEN) { ApmekenRoom(it).also { room = it } }.use { f ->
                f.advance() //Admit every subsequent entrant through the real room queue before a group task starts.
                val p = f.players.first(); val teammate = f.players[1]
                repeat(28) { p.inventory[it] = gg.rsmod.game.model.item.Item(100 + it) }
                assertFalse(room.takePotions(p))
                p.inventory[0] = null
                f.cycle++
                assertTrue(room.takePotions(p))
                val id = assertNotNull(ToaIds.item(ApmekenRoom.POTION))
                assertEquals(100, p.inventory.getItemCount(id))
                assertFalse(room.takePotions(p)); assertEquals(100, p.inventory.getItemCount(id))
                f.cycle++
                p.inventory[1] = null
                assertTrue(room.takeHammer(p))
                assertTrue(room.puzzle.beginTask(ApmekenPuzzle.Action.PILLAR, 1, mapOf(0 to 1), 20))
                f.cycle++
                assertTrue(room.repair(p, f.obj(45494, ApmekenRoom.PILLARS[0])))
                assertFalse(room.repair(p, f.obj(45494, ApmekenRoom.PILLARS[0])))
                assertTrue(room.puzzle.beginTask(ApmekenPuzzle.Action.CORRUPTION, 7, emptyMap(), 20))
                f.players.forEach { f.at(it, ToaPath.APMEKEN.challengeEntry) }
                f.cycle++
                assertTrue(room.pour(p))
                assertNull(room.puzzle.task); assertEquals(99, p.inventory.getItemCount(id))
                assertFalse(room.pour(p)); assertEquals(99, p.inventory.getItemCount(id))
                val run = f.raid.progress.current!!
                run.wipe(f.cycle); f.cycle++; f.raid.progress.cycle(f.cycle)
                assertTrue(room.puzzle.closed)
                assertFalse(room.pour(p, teammate)); assertFalse(room.takeHammer(p))
                assertTrue(p.inventory.contains(gg.rsmod.plugins.api.cfg.Items.HAMMER), "a normal crate hammer must not be destroyed by room cleanup")
                assertTrue(f.entities.isEmpty())
            }
        } finally { ApmekenReferences.profile = saved }
    }

    @Test fun `installed default plans provide eight completable waves for solo and eight members`() {
        val profile = assertNotNull(ApmekenReferences.profile)
        for (size in listOf(1, 8)) {
            val state = ApmekenPuzzle<Int>(size); repeat(size) { state.enter(it) }
            repeat(8) { index ->
                val plan = profile.waves.value(size, index + 1)
                val ids = plan.map { Any() to it.type }
                assertTrue(state.nextWave(profile.remainingThresholds.value[index]) { ids })
                ids.forEach { assertTrue(state.killed(it.first)) }
            }
            assertTrue(state.complete)
            profile.tasks.value.filter { size > 1 || it.type != ApmekenPuzzle.Action.CORRUPTION }.forEach { task ->
                assertTrue(task.requiredByPartySize[size - 1] > 0)
                if (task.type != ApmekenPuzzle.Action.CORRUPTION)
                    assertTrue(task.usesByPartySize[size - 1].values.sum() >= task.requiredByPartySize[size - 1])
            }
        }
    }

    @Test fun `eight waves gate completion and source-provided compositions are checked for solo and eight`() {
        for (size in listOf(1, 8)) {
            val state = ApmekenPuzzle<Int>(size)
            repeat(size) { state.enter(it) }
            repeat(8) { index ->
                assertFalse(state.complete)
                assertTrue(state.nextWave(0) { expected ->
                    (expected.map { if (it == ApmekenPuzzle.Baboon.SPECIAL) ApmekenPuzzle.Baboon.SHAMAN else it } +
                        if (size > 1) listOf(ApmekenPuzzle.Baboon.BRAWLER) else emptyList()).map { Any() to it }
                        .also { wave -> spawned = wave.map { it.first } }
                })
                assertEquals(index + 1, state.wave)
                assertFalse(state.nextWave(0) { error("cannot advance while above the observed threshold") })
                spawned.forEach { assertTrue(state.killed(it)); assertFalse(state.killed(it)) }
            }
            assertTrue(state.complete)
            assertFalse(state.nextWave(0) { error("no ninth wave") })
        }
    }
    private var spawned = emptyList<Any>()

    @Test fun `failed spawn cannot count as an empty wave clear`() {
        val state = ApmekenPuzzle<Int>(1); state.enter(1)
        assertFalse(state.nextWave(0) { emptyList() }); assertEquals(0, state.wave)
        assertFalse(state.nextWave(0) { listOf(Any() to ApmekenPuzzle.Baboon.CURSED) })
        assertEquals(0, state.wave)
        val group = ApmekenPuzzle<Int>(8); group.enter(1)
        assertFalse(group.nextWave(0) { expected -> expected.map { Any() to it } })
        assertEquals(0, group.wave)
    }

    @Test fun `combat triangle and specialist ranged maxima are independent of raid level`() {
        for (type in ApmekenPuzzle.Baboon.values().filter { it.combat }) {
            assertTrue(type.maximumHit(type.weakness!!))
            CombatClass.values().filter { it != type.weakness }.forEach { assertFalse(type.maximumHit(it)) }
        }
        assertTrue(ApmekenPuzzle.Baboon.SHAMAN.maximumHit(CombatClass.RANGED))
        assertTrue(ApmekenPuzzle.Baboon.CURSED.maximumHit(CombatClass.RANGED))
        assertFalse(ApmekenPuzzle.Baboon.CURSED.maximumHit(CombatClass.MAGIC))
        assertFalse(ApmekenPuzzle.Baboon.VOLATILE.maximumHit(CombatClass.RANGED))
        assertFalse(ApmekenPuzzle.Baboon.THRALL.maximumHit(CombatClass.RANGED))
        assertEquals(2, ApmekenPuzzle.Baboon.THRALL.hp)
    }

    @Test fun `all baboon variants retain their post rebalance HP at Entry Normal and Expert`() {
        val expected = listOf(25, 30, 20, 30, 35, 25, 40, 8, 20, 2)
        val variants = ApmekenPuzzle.Baboon.values().filter { it != ApmekenPuzzle.Baboon.SPECIAL }
        for (level in listOf(0, 150, 300)) for ((index, type) in variants.withIndex()) {
            assertEquals(expected[index], ApmekenRoom.definition(type, level).lifepoints)
            assertEquals(type == ApmekenPuzzle.Baboon.SHAMAN, !ApmekenRoom.definition(type, level).poisonImmunity)
        }
    }

    @Test fun `first entrant gets sight and group corruption requires the sight holder and matching stack`() {
        val state = ApmekenPuzzle<Int>(8)
        repeat(8) { state.enter(it) }; assertEquals(0, state.sight)
        assertTrue(state.beginTask(ApmekenPuzzle.Action.CORRUPTION, 7, emptyMap(), 20))
        assertEquals(ApmekenPuzzle.Result.WRONG, state.cleanse(1, (0..7).toSet(), 1))
        assertEquals(ApmekenPuzzle.Result.PERFORMED, state.cleanse(0, setOf(1, 2), 1))
        assertEquals(setOf(3, 4, 5, 6, 7), state.task!!.corrupted)
        assertEquals(ApmekenPuzzle.Result.COMPLETE, state.cleanse(0, setOf(3, 4, 5, 6, 7), 2))
        assertNull(state.task)
        assertEquals(ApmekenPuzzle.Result.WRONG, state.cleanse(0, (0..7).toSet(), 3))
        assertTrue(state.transferSight(7)); assertEquals(7, state.sight)
    }

    @Test fun `two simultaneous site actions cannot repair the same fault twice`() {
        val state = ApmekenPuzzle<Int>(2); state.enter(1); state.enter(2)
        assertTrue(state.beginTask(ApmekenPuzzle.Action.PILLAR, 2, mapOf(0 to 1, 1 to 1), 10))
        assertEquals(ApmekenPuzzle.Result.PERFORMED, state.fix(1, ApmekenPuzzle.Action.PILLAR, 0, 1))
        assertEquals(ApmekenPuzzle.Result.WRONG, state.fix(2, ApmekenPuzzle.Action.PILLAR, 0, 1))
        assertEquals(ApmekenPuzzle.Result.WRONG, state.fix(1, ApmekenPuzzle.Action.VENT, 1, 1))
        assertEquals(ApmekenPuzzle.Result.COMPLETE, state.fix(2, ApmekenPuzzle.Action.PILLAR, 1, 1))
    }

    @Test fun `deadline failure counts only missed actions and is applied once`() {
        val state = ApmekenPuzzle<Int>(3); repeat(3) { state.enter(it) }
        assertTrue(state.beginTask(ApmekenPuzzle.Action.VENT, 3, mapOf(0 to 1, 1 to 1, 2 to 1), 20))
        state.fix(0, ApmekenPuzzle.Action.VENT, 0, 19)
        assertEquals(0, state.expireTask(19))
        assertEquals(ApmekenPuzzle.Result.WRONG, state.fix(1, ApmekenPuzzle.Action.VENT, 1, 20))
        assertEquals(2, state.expireTask(20)); assertEquals(0, state.expireTask(20))
    }

    @Test fun `solo never receives corruption and departed players cannot be required to cleanse`() {
        val solo = ApmekenPuzzle<Int>(1); solo.enter(1)
        assertFalse(solo.beginTask(ApmekenPuzzle.Action.CORRUPTION, 1, emptyMap(), 5))
        val group = ApmekenPuzzle<Int>(2); group.enter(1); group.enter(2)
        assertTrue(group.beginTask(ApmekenPuzzle.Action.CORRUPTION, 1, emptyMap(), 5))
        group.leave(2, null); assertNull(group.task)
        group.leave(1, null); assertNull(group.sight)
        group.close()
        assertEquals(ApmekenPuzzle.Result.REFUSED, group.fix(1, ApmekenPuzzle.Action.PILLAR, 0, 5))
        group.enter(1); assertNull(group.sight)
        val retry = ApmekenPuzzle<Int>(2); retry.enter(2)
        assertEquals(2, retry.sight); assertEquals(0, retry.wave); assertFalse(retry.complete)
    }
}

package gg.rsmod.plugins.content.activity.toa.puzzles

import kotlin.test.*
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasReferences.Kind

class ToaScabarasTests {
    @Test fun `nuisance scarab defence and accuracy scale with mode while party size affects only HP`() {
        val entry = ScabarasRoom.definition(0, 1)
        assertEquals(12, entry.lifepoints); assertEquals(listOf(20, 32, 28, 1, 50), entry.stats)
        val normal = ScabarasRoom.definition(150, 1)
        assertEquals(19, normal.lifepoints); assertEquals(44, normal.stats[2]); assertEquals(80, normal.stats[4])
        val expert = ScabarasRoom.definition(300, 8)
        assertEquals(153, expert.lifepoints); assertEquals(61, expert.stats[2]); assertEquals(110, expert.stats[4])
        assertEquals(4, expert.attackSpeed); assertTrue(expert.poisonImmunity && expert.venomImmunity)
    }

    @Test fun `real room is playable through memory branches addition and matching for solo and eight`() {
        val saved = ScabarasReferences.profile
        val defaults = ScabarasDefaults.reference()
        val sequence = listOf(0, 2, 4, 6, 8)
        try {
            for (size in listOf(1, 8)) {
                val layout = ScabarasDefaults.layoutForOrder(size, listOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION))
                val initial = ScabarasReferences.Initial((0..4).toList(), 0, 21, (1..9).toList(), (1..9).toList(), setOf(1, 3, 5, 7))
                ScabarasReferences.profile = defaults.copy(
                    layout = ToaPuzzleReferences.Evidence({ layout }, "deterministic layout fixture"),
                    initial = ToaPuzzleReferences.Evidence({ _, _ -> initial }, "deterministic state fixture"),
                    memoryOrder = ToaPuzzleReferences.Evidence({ sequence }, "deterministic playback fixture"),
                    scarabSpawns = ToaPuzzleReferences.Evidence(emptyList(), "isolate puzzle actions from nuisance combat"))
                lateinit var room: ScabarasRoom
                ToaPuzzleFixture(size, ToaPath.SCABARAS) { ScabarasRoom(it).also { room = it } }.use { f ->
                    val p = f.players.first()
                    assertEquals(5, f.entities.filterIsInstance<Npc>().size)
                    assertNull(f.raid.progress.boss(ToaPath.SCABARAS))
                    assertFalse(room.inspect(p, f.obj(45339, layout.tablet)))
                    assertTrue(room.button(p, f.obj(45338, layout.memoryButton)))
                    assertFalse(room.button(p, f.obj(45338, layout.memoryButton)))
                    f.advance(12)
                    sequence.forEach { f.at(p, layout.memoryTiles[it]); f.advance() }
                    if (size > 1) {
                        val lights = ScabarasPuzzle.Lights(initial.lights)
                        lights.solution().forEach { f.at(p, layout.lights[it]); f.advance() }
                        val obelisks = room.scope.spawned.take(5)
                        for (npc in obelisks) {
                            val hit = Hit.Builder().addHit(1, 0).build().also { it.source = p }
                            npc.hitModifier!!.invoke(hit)
                            assertEquals(0, hit.hitmarks.sumOf { it.damage })
                            f.advance()
                        }
                    }
                    assertTrue(room.inspect(p, f.obj(45339, layout.tablet)))
                    //Wiki target21 solution: fourth column (facing east), five adjacent cells.
                    listOf(3, 8, 13, 18, 23).forEach { f.at(p, layout.additionTiles[it]); f.advance() }
                    assertNull(f.raid.progress.boss(ToaPath.SCABARAS), "matching must still gate the boss")
                    for (symbol in 1..9) {
                        if (size == 1 && symbol in initial.soloPreSolved) continue
                        f.at(p, layout.matchingButtons[0][symbol - 1]); f.advance()
                        f.at(p, layout.matchingButtons[1][symbol - 1]); f.advance()
                    }
                    assertTrue(f.raid.progress.challengeCleared(ToaPath.SCABARAS))
                    assertTrue(f.entities.isEmpty())
                    assertFalse(room.button(p, f.obj(45338, layout.memoryButton)))
                    assertNotNull(f.raid.progress.boss(ToaPath.SCABARAS))
                }
            }
        } finally { ScabarasReferences.profile = saved }
    }

    @Test fun `real memory playback is cancelled by wipe before any stale tile can clear a fresh run`() {
        val saved = ScabarasReferences.profile
        val defaults = ScabarasDefaults.reference()
        val layout = ScabarasDefaults.layoutForOrder(1, listOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION))
        ScabarasReferences.profile = defaults.copy(layout = ToaPuzzleReferences.Evidence({ layout }, "fixture"))
        try {
            lateinit var room: ScabarasRoom
            ToaPuzzleFixture(1, ToaPath.SCABARAS) { ScabarasRoom(it).also { room = it } }.use { f ->
                val run = f.raid.progress.current!!
                assertTrue(room.button(f.players.first(), f.obj(45338, layout.memoryButton)))
                f.advance()
                run.wipe(f.cycle); f.cycle++; f.raid.progress.cycle(f.cycle)
                f.advance(20)
                assertTrue(f.entities.isEmpty()); assertFalse(room.live)
                assertFalse(run.clear(f.cycle))
                assertFalse(f.raid.progress.challengeCleared(ToaPath.SCABARAS))
            }
        } finally { ScabarasReferences.profile = saved }
    }

    @Test fun `independent RuneLite solutions verify the physical board orientation and glyph values`() {
        //LlemonDuck cec1665058a3244bf41daddc0e1f43177a0b3edf AdditionPuzzleSolver, scene row-major NW->SE.
        val physical = listOf(9, 3, 7, 2, 9, 7, 6, 4, 1, 1, 8, 6, 3, 8, 5, 6, 2, 7, 2, 4, 1, 9, 5, 4, 3)
        for (row in 0..4) for (col in 0..4)
            assertEquals(physical[row * 5 + col], ScabarasPuzzle.NUMBER_GRID[(4 - col) * 5 + row])
        val profile = ScabarasDefaults.reference()
        assertEquals(45347, profile.numberLocs.value[3]) //Triangle3, not star6.
        assertEquals(45350, profile.numberLocs.value[6]) //Bird6, not crook7.
        assertEquals(45386, profile.numberSelectedLocs.value[7])
        repeat(20) { assertEquals(setOf(1, 3, 5, 7), profile.initial.value(1, it).soloPreSolved) }
    }

    @Test fun `all twenty four default chamber arrangements have valid solo routes and eight player gates`() {
        assertNotNull(ScabarasReferences.profile)
        val orders = permutations(listOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION))
        for (size in listOf(1, 8)) for (order in orders) {
            val layout = ScabarasDefaults.layoutForOrder(size, order)
            assertEquals(9, layout.memoryTiles.size)
            assertEquals(8, layout.lights.size)
            assertEquals(25, layout.additionTiles.size)
            assertEquals(18, layout.matchingButtons.flatten().distinct().size)
            if (size == 1) for (first in 0..1) {
                val gates = ScabarasGateState(size, layout.soloSkipsOnClear, layout.numberPrerequisites)
                assertTrue(gates.finish(order[first]))
                val second = if (first == 0) 3 else 2
                if (order[second] == Kind.ADDITION) assertTrue(gates.canReadNumber)
                assertTrue(gates.finish(order[second]))
                assertTrue(gates.canMatch); assertFalse(gates.complete)
                assertTrue(gates.finish(Kind.MATCHING)); assertTrue(gates.complete)
            } else {
                val gates = ScabarasGateState(size, layout.soloSkipsOnClear, layout.numberPrerequisites)
                order.forEach { assertTrue(gates.finish(it)) }
                assertTrue(gates.canMatch); assertFalse(gates.complete)
                gates.finish(Kind.MATCHING); assertTrue(gates.complete)
            }
        }
    }

    @Test fun `default matching permutations and observed lights states are always solvable`() {
        val profile = assertNotNull(ScabarasReferences.profile)
        for (size in listOf(1, 8)) repeat(20) { attempt ->
            val initial = profile.initial.value(size, attempt)
            val lights = ScabarasPuzzle.Lights(initial.lights); lights.solution().forEach(lights::step); assertTrue(lights.complete)
            val matching = ScabarasPuzzle.Matching(initial.leftSymbols, initial.rightSymbols, size,
                if (size == 1) initial.soloPreSolved else emptySet())
            (1..9).filterNot(matching::matched).forEach { symbol ->
                matching.activate(0, initial.leftSymbols.indexOf(symbol)); matching.activate(1, initial.rightSymbols.indexOf(symbol))
            }
            assertTrue(matching.complete)
            val numbers = ScabarasPuzzle.Addition(initial.additionTarget); assertNotNull(numbers.solution())
        }
    }

    @Test fun `two players sharing a held pressure plate cannot retrigger it until everybody leaves`() {
        val contacts = ScabarasPuzzle.Contacts<Int, Int>()
        assertEquals(listOf(1 to 7), contacts.update(listOf(1 to 7, 2 to 7)))
        assertTrue(contacts.update(listOf(2 to 7)).isEmpty())
        assertTrue(contacts.update(listOf(2 to 7, 3 to 7)).isEmpty())
        contacts.update(emptyList())
        assertEquals(listOf(3 to 7), contacts.update(listOf(3 to 7)))
        contacts.clear()
        assertEquals(listOf(3 to 7), contacts.update(listOf(3 to 7)))
    }
    @Test fun `number tablet never waits for the final matching puzzle and boss gate waits for matching`() {
        val group = ScabarasGateState(8, emptyMap())
        assertFalse(group.canReadNumber); assertFalse(group.canMatch); assertFalse(group.complete)
        assertFalse(group.finish(Kind.ADDITION)); assertFalse(group.finish(Kind.MATCHING))
        listOf(Kind.MEMORY, Kind.OBELISKS, Kind.LIGHTS).forEach { assertTrue(group.finish(it)) }
        assertTrue(group.canReadNumber); assertFalse(group.canMatch)
        assertTrue(group.finish(Kind.ADDITION)); assertTrue(group.canMatch); assertFalse(group.complete)
        assertTrue(group.finish(Kind.MATCHING)); assertTrue(group.complete)
        assertFalse(group.finish(Kind.MATCHING))
    }

    @Test fun `source supplied solo skips are ignored in a group and can never skip matching`() {
        //This route is a test fixture, not a claim about an OSRS layout.
        val skips = mapOf(Kind.MEMORY to setOf(Kind.OBELISKS, Kind.LIGHTS))
        val solo = ScabarasGateState(1, skips)
        solo.finish(Kind.MEMORY); assertTrue(solo.canReadNumber)
        solo.finish(Kind.ADDITION); assertTrue(solo.canMatch); assertFalse(solo.complete)
        solo.finish(Kind.MATCHING); assertTrue(solo.complete)
        val group = ScabarasGateState(8, skips)
        group.finish(Kind.MEMORY); assertFalse(group.canReadNumber); assertTrue(group.skipped.isEmpty())
        assertFailsWith<IllegalArgumentException> { ScabarasGateState(1, mapOf(Kind.MEMORY to setOf(Kind.MATCHING))) }
    }

    @Test fun `all nine independent Wiki image lights solutions reproduce the source result`() {
        //07-scabaras-beeldoplossingen.tsv, image configuration1..9. Indices follow the image, not generated game states.
        val examples = listOf(
            251 to listOf(0, 2, 3, 6, 7), 250 to listOf(3, 4, 5, 7), 0 to listOf(0, 1, 2, 3, 4, 5, 6, 7),
            253 to listOf(1, 3, 4, 5, 7), 126 to listOf(0, 1, 3, 4, 6, 7), 4 to listOf(1, 4, 5),
            252 to listOf(0, 1, 2, 3, 6, 7), 125 to listOf(2, 4), 2 to listOf(0, 2, 6),
        )
        examples.forEach { (initial, steps) ->
            val board = ScabarasPuzzle.Lights(initial)
            steps.forEach(board::step)
            assertTrue(board.complete, "Wiki configuration $initial")
        }
    }

    @Test fun `every mathematically possible eight plate state has a solution without the damaging flip`() {
        repeat(256) { initial ->
            val board = ScabarasPuzzle.Lights(initial)
            board.solution().forEach(board::step)
            assertTrue(board.complete, "state $initial")
        }
        assertFalse(ScabarasPuzzle.Lights.CELLS.contains(2 to 2))
    }

    @Test fun `all twenty six Wiki image sum solutions match the independently inferred board`() {
        //07-scabaras-beeldoplossingen.tsv target20..45, row-major cell indices, facing east.
        val examples = mapOf(
            20 to listOf(13, 17, 21), 21 to listOf(3, 8, 13, 18, 23), 22 to listOf(4, 9, 14, 19, 24),
            23 to listOf(4, 8, 12, 16, 20), 24 to listOf(9, 13, 17, 21), 25 to listOf(7, 12, 17, 22),
            26 to listOf(3, 4, 9, 14, 19, 24), 27 to listOf(7, 8, 12, 17, 22), 28 to listOf(0, 1, 6, 11, 16, 21),
            29 to listOf(3, 7, 12, 17, 22), 30 to listOf(2, 7, 12, 17, 22), 31 to listOf(20, 21, 22, 23, 24),
            32 to listOf(1, 2, 3, 4, 9, 14, 19, 24), 33 to listOf(18, 20, 21, 22, 23, 24),
            34 to listOf(2, 3, 7, 12, 17, 22), 35 to listOf(15, 16, 17, 18, 19, 20),
            36 to listOf(0, 1, 2, 5, 10, 15, 20), 37 to listOf(2, 3, 4, 7, 12, 17, 22),
            38 to listOf(2, 6, 7, 10, 12, 17, 22), 39 to listOf(0, 1, 5, 7, 10, 15, 20),
            40 to listOf(19, 20, 21, 22, 23, 24), 41 to listOf(10, 15, 20, 21, 22, 23, 24),
            42 to listOf(4, 8, 9, 12, 14, 16, 19, 20, 24), 43 to listOf(5, 10, 15, 20, 21, 22, 23, 24),
            44 to listOf(0, 5, 10, 15, 16, 17, 18, 19), 45 to listOf(3, 8, 13, 18, 20, 21, 22, 23),
        )
        examples.forEach { (target, steps) ->
            assertEquals(target, steps.sumOf { ScabarasPuzzle.NUMBER_GRID[it] }, "source target $target")
            val board = ScabarasPuzzle.Addition(target)
            assertEquals(ScabarasPuzzle.Result.IGNORED, board.step(steps.first()), "tablet must stay locked")
            assertFalse(board.unlock(false)); assertTrue(board.unlock(true))
            steps.forEach(board::step)
            assertTrue(board.complete)
        }
    }

    @Test fun `all target numbers are solvable without duplicate tile contributions`() {
        for (target in 20..45) {
            val board = ScabarasPuzzle.Addition(target)
            board.unlock(true)
            val solution = assertNotNull(board.solution())
            assertEquals(solution.size, solution.toSet().size)
            solution.forEach(board::step)
            assertTrue(board.complete)
        }
    }

    @Test fun `addition overflow resets selection and repeat contact does not count a tile twice`() {
        val board = ScabarasPuzzle.Addition(20); board.unlock(true)
        assertEquals(ScabarasPuzzle.Result.PROGRESS, board.step(0)); assertEquals(9, board.total)
        assertEquals(ScabarasPuzzle.Result.IGNORED, board.step(0)); assertEquals(9, board.total)
        board.step(19)
        assertEquals(ScabarasPuzzle.Result.WRONG, board.step(20)); assertEquals(0, board.total)
        board.close(); assertEquals(ScabarasPuzzle.Result.IGNORED, board.step(0))
    }

    @Test fun `all one hundred twenty obelisk permutations can be solved and wrong order cannot clear`() {
        val orders = permutations((0..4).toList())
        assertEquals(120, orders.size)
        orders.forEach { order ->
            val board = ScabarasPuzzle.Obelisks(order, true)
            assertEquals(ScabarasPuzzle.Result.WRONG, board.strike((order.first() + 1) % 5))
            assertFalse(board.complete)
            order.forEach(board::strike)
            assertTrue(board.complete)
            assertEquals(ScabarasPuzzle.Result.IGNORED, board.strike(0))
        }
    }

    @Test fun `new memory button press cancels old playback and a wrong step cannot give a clear`() {
        val board = ScabarasPuzzle.Memory(9, true)
        val first = board.show(listOf(2, 5, 8))!!
        assertEquals(ScabarasPuzzle.Result.IGNORED, board.step(2))
        val second = board.show(listOf(0, 1, 2))!!
        assertFalse(board.finishPlayback(first)); assertTrue(board.finishPlayback(second))
        assertEquals(ScabarasPuzzle.Result.PROGRESS, board.step(0))
        assertEquals(ScabarasPuzzle.Result.WRONG, board.step(2))
        assertEquals(ScabarasPuzzle.Result.WRONG, board.step(1))
        listOf(0, 1, 2).forEach(board::step); assertTrue(board.complete)
        board.close(); assertNull(board.show(listOf(3, 4, 5)))
    }

    @Test fun `solo four pre-solved pairs and full eight player matching both finish all nine pairs`() {
        val left = (1..9).toList(); val right = left.reversed()
        for (size in listOf(1, 8)) {
            val solved = if (size == 1) (1..4).toSet() else emptySet()
            val board = ScabarasPuzzle.Matching(left, right, size, solved)
            for (symbol in 1..9) {
                if (symbol in solved) continue
                assertEquals(ScabarasPuzzle.Result.PROGRESS, board.activate(0, left.indexOf(symbol)))
                assertEquals(ScabarasPuzzle.Result.IGNORED, board.activate(0, left.indexOf(symbol)))
                board.activate(1, right.indexOf(symbol))
                assertTrue(board.matched(symbol))
            }
            assertTrue(board.complete)
        }
    }

    @Test fun `matching mismatch reveals no permanent pair and wipe rejects old activations`() {
        val board = ScabarasPuzzle.Matching((1..9).toList(), (1..9).toList(), 8, emptySet())
        board.activate(0, 0)
        assertEquals(ScabarasPuzzle.Result.WRONG, board.activate(1, 1))
        assertFalse(board.matched(1)); assertFalse(board.matched(2))
        board.close(); assertEquals(ScabarasPuzzle.Result.IGNORED, board.activate(0, 0))
        assertFalse(ScabarasPuzzle.Matching((1..9).toList(), (1..9).toList(), 8, emptySet()).complete)
    }

    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { first -> permutations(values - first).map { listOf(first) + it } }
}

package gg.rsmod.plugins.content.scrolls

import kotlin.random.Random
import kotlin.test.*

class TreasureTrailChallengeTests {
    @Test fun `hot cold uses every sourced boundary and a nine by nine dig area`() {
        val temperatures = TreasureTrailHotCold.Temperature.values().reversed()
        val ranges = listOf(0L..3L, 4L..29L, 30L..69L, 70L..99L, 100L..149L, 150L..199L, 200L..499L, 500L..10_000L)
        ranges.forEachIndexed { index, range ->
            assertEquals(temperatures[index], TreasureTrailHotCold.temperature(range.first))
            assertEquals(temperatures[index], TreasureTrailHotCold.temperature(range.last))
        }
        for (dx in -5..5) for (dz in -5..5) {
            assertEquals(dx in -4..4 && dz in -4..4, TreasureTrailHotCold.canDig(3000+dx, 3300+dz, 0, 3000, 3300, 0))
        }
        assertFalse(TreasureTrailHotCold.canDig(3000, 3300, 1, 3000, 3300, 0))
        assertEquals(4294967295L, TreasureTrailHotCold.distance(Int.MIN_VALUE, 0, Int.MAX_VALUE, 0))
        assertFailsWith<IllegalArgumentException> { TreasureTrailHotCold.temperature(-1) }
        assertEquals((3..8).toSet(), (0..1000).map { TreasureTrailHotCold.feelDamage(Random(it)) }.toSet())
    }

    @Test fun `sliding puzzle cannot accept forged impossible or nonadjacent moves and restores the exact state`() {
        val solved = IntArray(25) { (it+1)%25 }
        val puzzle = TreasureTrailSlidingPuzzle.restore(solved)
        assertTrue(puzzle.solved)
        for (index in listOf(-1, 0, 18, 25, Int.MAX_VALUE)) {
            assertFalse(puzzle.move(index))
            assertContentEquals(solved, puzzle.tiles())
        }
        assertTrue(puzzle.move(23)); assertFalse(puzzle.solved)
        val copy = TreasureTrailSlidingPuzzle.decode(puzzle.encode())
        assertContentEquals(puzzle.tiles(), copy.tiles())
        assertTrue(copy.move(24)); assertTrue(copy.solved)
        assertFalse(puzzle.solved) // restoring does not alias the first board
        assertFailsWith<IllegalArgumentException> { TreasureTrailSlidingPuzzle.restore(IntArray(25)) }
        val impossible = solved.copyOf().also { it[0]=2; it[1]=1 }
        assertFailsWith<IllegalArgumentException> { TreasureTrailSlidingPuzzle.restore(impossible) }
        repeat(128) { seed ->
            val generated = TreasureTrailSlidingPuzzle.create(Random(seed))
            assertFalse(generated.solved)
            val blank = generated.tiles().indexOf(0)
            assertEquals(1, (blank / 5 + blank % 5) % 2, "101 slides must change the blank's checkerboard colour")
            assertContentEquals(generated.tiles(), TreasureTrailSlidingPuzzle.decode(generated.encode()).tiles())
        }
    }

    @Test fun `standard treasure trail scramble makes exactly 101 legal random choices`() {
        var choices = 0
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("Only a neighbour index should be rolled")
            override fun nextInt(until: Int): Int {
                assertTrue(until in 2..4)
                choices++
                return 0
            }
        }
        val puzzle = TreasureTrailSlidingPuzzle.create(random)
        assertEquals(101, choices)
        assertFalse(puzzle.solved)
        assertContentEquals(puzzle.tiles(), TreasureTrailSlidingPuzzle.decode(puzzle.encode()).tiles())
    }

    @Test fun `light box requires solvable wiring keeps sessions separate and accepts only its eight switches`() {
        val masks = IntArray(8) { 1 shl it }
        val box = TreasureTrailLightBox(masks)
        assertTrue(box.solved)
        assertFalse(box.toggle(-1)); assertFalse(box.toggle(8))
        assertTrue(box.toggle(0)); assertFalse(box.solved)
        assertEquals(TreasureTrailLightBox.ALL_ON xor 1, box.lights)
        val secondAccount = TreasureTrailLightBox(masks)
        assertTrue(secondAccount.solved)
        masks[0] = 0 // caller cannot change an existing puzzle
        assertTrue(box.toggle(0)); assertTrue(box.solved)
        val restored = TreasureTrailLightBox(box.masks(), box.switches)
        assertEquals(box.lights, restored.lights)
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightBox(IntArray(7)) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightBox(IntArray(8) { -1 }) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightBox(IntArray(8), baseLights=0) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightBox(IntArray(8), switches=256) }
    }
}

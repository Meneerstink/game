package gg.rsmod.plugins.content.scrolls

import kotlin.random.Random
import kotlin.test.*

class ClueStepSelectionTests {
    private class Rolls(vararg values: Int): Random() {
        val bounds = mutableListOf<Int>()
        private val rolls = values.toList().iterator()
        override fun nextBits(bitCount: Int): Int = error("Unexpected random primitive")
        override fun nextInt(until: Int): Int = rolls.next().also { bounds += until; require(it in 0 until until) }
    }

    @Test fun `each master category has one of seven equal first-stage slots regardless of its step count`() {
        val rows = TreasureTrailStepCatalogue.current.rows.filter { it.tier == ClueScrollTier.MASTER }
        val available = rows.filterNot { it.tornPart }.map(::TreasureTrailSourceStep) +
            TreasureTrailThreePartStep(rows.filter { it.tornPart })
        val expected = available.groupBy { if (it is TreasureTrailSourceStep) it.source.kind else "CRYPTIC" }.values.toList()
        assertEquals(7,expected.size)
        assertTrue(expected.map { it.size }.distinct().size > 1)
        for ((categoryIndex, category) in expected.withIndex()) for ((stepIndex, step) in category.withIndex()) {
            val rolls = Rolls(categoryIndex,stepIndex)
            assertSame(step,ClueStepSelection.choose(available,rolls))
            assertEquals(listOf(7,category.size),rolls.bounds)
        }
    }

    @Test fun `master-only source rule does not silently apply equal categories to other tiers`() {
        for (tier in ClueScrollTier.values().filter { it != ClueScrollTier.MASTER }) {
            val available = TreasureTrailStepCatalogue.current.rows.filter { it.tier == tier }.map(::TreasureTrailSourceStep)
            for ((index,step) in available.withIndex()) {
                val rolls=Rolls(index)
                assertSame(step,ClueStepSelection.choose(available,rolls))
                assertEquals(listOf(available.size),rolls.bounds)
            }
        }
    }

    @Test fun `empty and mixed-tier pools cannot accidentally issue a different tier step`() {
        assertFailsWith<IllegalArgumentException> { ClueStepSelection.choose(emptyList()) }
        val rows = TreasureTrailStepCatalogue.current.rows
        val mixed = listOf(rows.first(),rows.first { it.tier == ClueScrollTier.MASTER }).map(::TreasureTrailSourceStep)
        assertFailsWith<IllegalArgumentException> { ClueStepSelection.choose(mixed) }
    }
}

package gg.rsmod.plugins.content.scrolls

import java.io.File
import kotlin.random.Random
import kotlin.test.*

class ClueRewardCatalogueTests {
    private val catalogue = ClueRewardCatalogue.load(File("../../data/cfg/clue/rewards.json"))

    private fun at(value: Double) = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double = value
        override fun nextInt(from: Int, until: Int): Int = from
    }

    @Test fun `every allowed outcome of all five tiers can actually be awarded`() {
        for (tier in ClueScrollTier.values()) {
            var start = 0.0
            for (outcome in catalogue.outcomes.getValue(tier)) {
                val rolled = catalogue.roll(tier, at(start + outcome.probability / 2))
                assertEquals(outcome.rows.map { ClueRewardCatalogue.Reward(it.osrsId, it.minimum, it.section.contains("mega-rare", ignoreCase = true)) }, rolled)
                start += outcome.probability
            }
            assertEquals(1.0, start, 1e-12, tier.name)
        }
    }

    @Test fun `hard mega rare potions are one joint result with the original marginal rates`() {
        val event = catalogue.outcomes.getValue(ClueScrollTier.HARD).single { it.rows.size > 1 }
        assertEquals(setOf(2437, 2441, 2443), event.rows.map { it.osrsId }.toSet())
        assertTrue(event.rows.all { it.minimum == 5 && it.maximum == 5 })
        assertEquals(1.0 / 16210, event.probability, 1e-15) // owner: reject four shop-only kit slots out of 1625
        assertEquals(1.0 + 1.0 / 8125, catalogue.tiers.getValue(ClueScrollTier.HARD).sumOf { it.probability }, 1e-12)
    }

    @Test fun `ranger boots and third age keep the exact OSRS per roll chances`() {
        assertEquals(1.0 / 1133, catalogue.tiers.getValue(ClueScrollTier.MEDIUM).single { it.osrsId == 2577 }.probability, 1e-15)
        assertEquals(1.0 / 211250, catalogue.tiers.getValue(ClueScrollTier.HARD).single { it.osrsId == 10350 }.probability, 1e-15)
        val expected = (3..5).map { 1.0 - Math.pow(1.0 - 1.0 / 1133, it.toDouble()) }.average()
        assertEquals(expected, catalogue.casketChance(ClueScrollTier.MEDIUM, 2577), 1e-15)
    }

    @Test fun `missing assets are reported without removing or boosting their slots`() {
        assertTrue(catalogue.missing(ClueScrollTier.MASTER) { null }.contains(20014))
        assertTrue(catalogue.missing(ClueScrollTier.MASTER) { it }.isEmpty())
        assertEquals(173, catalogue.tiers.getValue(ClueScrollTier.MASTER).size)
    }

    @Test fun `five included tiers have the OSRS length bounds and no beginner entry point`() {
        assertEquals(listOf(2..4, 3..5, 4..6, 5..7, 6..8), ClueScrollTier.values().map { it.trailSteps })
        assertEquals(listOf(2..4, 3..5, 4..6, 4..6, 5..7), ClueScrollTier.values().map { it.rewardRolls })
        assertTrue(ClueScrollTier.values().none { it.name == "BEGINNER" })
    }

    @Test fun `shop-only kits never roll and all other source weights are preserved relatively`() {
        for (tier in ClueScrollTier.values()) {
            val source = catalogue.sourceOutcomes.getValue(tier)
            val allowed = catalogue.outcomes.getValue(tier)
            assertTrue(allowed.flatMap { it.rows }.none { it.shopOnly })
            val mass = source.filter { entry -> entry.rows.none { it.shopOnly } }.sumOf { it.probability }
            allowed.forEach { outcome ->
                val original = source.single { it.rows == outcome.rows }
                assertEquals(original.probability / mass, outcome.probability, 1e-15)
            }
        }
    }

    @Test fun `Mimic viewer probabilities use its remaining fixed rolls including zero after exhaustion`() {
        val tier = ClueScrollTier.MASTER
        val outcome = catalogue.outcomes.getValue(tier).first()
        val id = outcome.rows.first().osrsId
        val chance = catalogue.outcomes.getValue(tier).filter { event -> event.rows.any { it.osrsId == id } }.sumOf { it.probability }
        for (rolls in 0..6) assertEquals(1.0 - Math.pow(1.0 - chance, rolls.toDouble()),
            catalogue.casketChance(tier, id, rolls..rolls), 1e-15)
        assertFailsWith<IllegalArgumentException> { catalogue.casketChance(tier, id, -1..1) }
        assertFailsWith<IllegalArgumentException> { catalogue.casketChance(tier, id, 5..4) }
    }
}

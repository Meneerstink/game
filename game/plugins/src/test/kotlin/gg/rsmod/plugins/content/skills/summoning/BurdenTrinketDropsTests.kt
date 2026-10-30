package gg.rsmod.plugins.content.skills.summoning

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N423 deel 5 (owner 2026-10-29 23:5x): the three small burden trinkets drop at the decided chances - boss 1/250 / 1/500 / 1/1,000,
 * breach monster 1/1,000 / 1/2,000 / 1/4,000, Deadman's Chest 1/100 / 1/200 / 1/400 - no bracket boost; the two big ones never drop.
 */
class BurdenTrinketDropsTests {
    private val t = BurdenTrinkets.Tier.values().associateBy { it.slots }

    @Test
    fun `the decided chances`() {
        val drops = BurdenTrinketDrops
        assertEquals(listOf(250, 500, 1_000), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.BOSS, t.getValue(it)) })
        assertEquals(listOf(1_000, 2_000, 4_000), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.BREACH_MONSTER, t.getValue(it)) })
        assertEquals(listOf(100, 200, 400), listOf(6, 9, 12).map { drops.denominator(BurdenTrinketDrops.Source.CHEST, t.getValue(it)) })
        BurdenTrinketDrops.Source.values().forEach { s ->
            assertEquals(0.0, drops.chance(s, t.getValue(18)))
            assertEquals(0.0, drops.chance(s, t.getValue(30)))
        }
    }

    @Test
    fun `a roll gives at most one trinket and over many rolls matches the chances`() {
        val random = Random(42)
        val n = 400_000
        val counts = HashMap<BurdenTrinkets.Tier, Int>()
        repeat(n) { BurdenTrinketDrops.roll(random, BurdenTrinketDrops.Source.CHEST)?.let { counts.merge(it, 1, Int::plus) } }
        // Kalphite 1/100, Bull ant 1/200, Terrorbird 1/400 (the rarer kinds are read first, so a Kalphite roll is slightly lower).
        fun near(tier: BurdenTrinkets.Tier, expected: Double) {
            val got = (counts[tier] ?: 0).toDouble() / n
            assertTrue(got in expected * 0.85..expected * 1.15, "${tier.trinketName}: $got vs $expected")
        }
        near(t.getValue(6), 1.0 / 100)
        near(t.getValue(9), 1.0 / 200)
        near(t.getValue(12), 1.0 / 400)
        assertNull(counts[t.getValue(18)])
        assertNull(counts[t.getValue(30)])
    }
}

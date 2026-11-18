package gg.rsmod.plugins.content.activity.cox

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** CoxLoot against the OSRS Wiki Ancient chest numbers (normal mode, 12 August 2026 weights). */
class CoxLootTests {
    @Test
    fun `unique weights total 60 and the common table has 33 different lines`() {
        assertEquals(60, CoxLoot.UNIQUES.sumOf { it.weight })
        assertEquals(33, CoxLoot.COMMONS.size)
        assertEquals(33, CoxLoot.COMMONS.map { it.osrs }.toSet().size)
    }

    @Test
    fun `570,000 points give a 65,7 percent unique chance and the rest rolls again`() {
        val r = Random(1)
        val n = 200_000
        val one = (0 until n).count { CoxLoot.uniqueCount(570_000, r) == 1 }.toDouble() / n
        assertTrue(one in 0.650..0.664, "570k -> $one")
        // 855,000 points: 65.7% then 32.85% (Wiki example) -> expected uniques 0.657 + 0.3285 = 0.9855
        val mean = (0 until n).sumOf { CoxLoot.uniqueCount(855_000, r) }.toDouble() / n
        assertTrue(mean in 0.975..0.996, "855k mean -> $mean")
        assertEquals(0, CoxLoot.uniqueCount(0, r))
    }

    @Test
    fun `at most six uniques however many points`() {
        val r = Random(2)
        repeat(1000) { assertTrue(CoxLoot.uniqueCount(100_000_000, r) <= 6) }
    }

    @Test
    fun `two common rolls never land on the same line and scale to 131,071 points`() {
        val r = Random(3)
        repeat(5000) {
            val d = CoxLoot.commons(131_071, r)
            assertEquals(2, d.size)
            assertNotEquals(d[0].osrs, d[1].osrs)
        }
        // At the cap the death rune line gives its maximum 3640 (Wiki 1-3640).
        val full = CoxLoot.COMMONS.first { it.name == "Death rune" }
        assertEquals(3640, maxOf(1, (full.max * 1.0).toInt()))
        val low = CoxLoot.commons(0, Random(4))
        assertTrue(low.all { it.amount >= 1 })
    }

    @Test
    fun `the receiver is weighted by personal points`() {
        val r = Random(5)
        val wins = (0 until 100_000).count { CoxLoot.pickReceiver(mapOf("a" to 900, "b" to 100), r) == "a" }
        assertTrue(wins in 89_000..91_000, "a won $wins")
    }

    @Test
    fun `chest without a unique gives two commons or the tablet instead of one, plus the journal when not banked`() {
        val r = Random(6)
        val drops = CoxLoot.chest(null, 50_000, hasTablet = true, hasJournal = false, random = r)
        assertTrue(drops.any { it.osrs == CoxLoot.DARK_JOURNAL })
        assertTrue(drops.none { it.osrs == CoxLoot.ANCIENT_TABLET })
        val withUnique = CoxLoot.chest(CoxLoot.UNIQUES.last(), 50_000, hasTablet = false, hasJournal = true, random = r)
        assertEquals(20997, withUnique.first().osrs)
        assertTrue(withUnique.none { it.osrs == CoxLoot.ELITE_CLUE })
    }
}

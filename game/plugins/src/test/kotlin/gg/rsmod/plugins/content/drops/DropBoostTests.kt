package gg.rsmod.plugins.content.drops

import java.util.Random
import kotlin.math.abs
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N100 (owner 2026-10-18, VRAGEN 34 a = A "zeldzaam eerst"): the pure rules of [DropBoost], the one function every boosted roll
 * and every display reads. Rules, written from the owner's words ("elk item x3-x5 kans, gewone items iets minder, nog steeds 1 item
 * per kill"):
 *  1. a roll is still ONE pick: the boosted chances never add up to more than 100 %;
 *  2. no line gets more than rate x its chance (max 100 %) and no line gets less than half of its own chance (never unreachable);
 *  3. rarer lines never get a smaller boost factor than commoner lines (rarest first);
 *  4. when everything fits in 100 % every line gets exactly rate x (the "nothing" share is the first thing that shrinks);
 *  5. lines with the same chance always get the same boosted chance; rate 1 changes nothing.
 */
class DropBoostTests {
    private val eps = 1e-9

    @Test
    fun `a table with room for everything gives every line exactly rate x its chance`() {
        // 1/128 + 32/128 + 4/128 at 3x = 111/128: fits.
        val base = doubleArrayOf(1 / 128.0, 32 / 128.0, 4 / 128.0)
        val got = DropBoost.chances(base, 3.0)
        base.indices.forEach { assertEquals(3 * base[it], got[it], eps) }
    }

    @Test
    fun `a table that always drops something serves the rarest lines first and the commons give way`() {
        // Hand-derived: helm 1/128, nested 4/128, coins 32/128 at 5x: 185/128 does not fit. Rarest first: helm 5/128 = 0.0390625,
        // nested 20/128 = 0.15625, coins get what is left, 1 - 0.0390625 - 0.15625 = 0.8046875 (3.2x, commons ARE the "nothing" first).
        val got = DropBoost.chances(doubleArrayOf(1 / 128.0, 32 / 128.0, 4 / 128.0), 5.0)
        assertEquals(5 / 128.0, got[0], eps)
        assertEquals(20 / 128.0, got[2], eps)
        assertEquals(0.8046875, got[1], eps)

        // A full table (no nothing): rare 1% + common 99% at 5x. The rare line gets 5%, the common line keeps 95% (never below 50%).
        val full = DropBoost.chances(doubleArrayOf(0.01, 0.99), 5.0)
        assertEquals(0.05, full[0], eps)
        assertEquals(0.95, full[1], eps)
        assertEquals(1.0, full.sum(), eps)

        // Commons give way, but only to half of their own chance: 40% rare + 60% common at 5x.
        val heavy = DropBoost.chances(doubleArrayOf(0.4, 0.6), 5.0)
        assertEquals(0.7, heavy[0], eps) // budget 1 - 0.5 x 0.6
        assertEquals(0.3, heavy[1], eps) // exactly half of 0.6: the floor
    }

    @Test
    fun `lines with the same chance share equally and rate 1 changes nothing`() {
        val base = doubleArrayOf(0.2, 0.2, 0.2, 0.2, 0.2)
        val got = DropBoost.chances(base, 4.0)
        got.forEach { assertEquals(0.2, it, eps) } // full table, equal lines: nothing can rise
        val mixed = DropBoost.chances(doubleArrayOf(0.1, 0.45, 0.45), 3.0)
        assertEquals(mixed[1], mixed[2], eps)
        assertEquals(listOf(0.3, 0.0, 0.5).size, DropBoost.chances(doubleArrayOf(0.3, 0.0, 0.5), 1.0).size)
        DropBoost.chances(doubleArrayOf(0.3, 0.0, 0.5), 1.0).forEachIndexed { i, v -> assertEquals(doubleArrayOf(0.3, 0.0, 0.5)[i], v, 0.0) }
    }

    @Test
    fun `random tables of every shape obey the five rules at every Annihilation rate`() {
        val random = Random(92100L)
        val failures = mutableListOf<String>()
        repeat(20_000) { n ->
            val lines = 1 + random.nextInt(14)
            val weights = DoubleArray(lines) { if (random.nextInt(5) == 0) 0.0 else Math.pow(random.nextDouble(), 3.0) + 1e-6 }
            val total = weights.sum()
            if (total <= 0.0) return@repeat
            // Fill 100 % (always-drop tables) or leave a nothing share.
            val fill = if (random.nextBoolean()) 1.0 else random.nextDouble().coerceAtLeast(0.05)
            val base = DoubleArray(lines) { weights[it] / total * fill }
            // equal lines now and then
            if (lines > 2 && random.nextInt(4) == 0) base[1] = base[0]
            val sum = base.sum()
            if (sum > 1.0 + 1e-12) return@repeat
            for (rate in doubleArrayOf(3.0, 4.0, 5.0)) {
                val q = DropBoost.chances(base, rate)
                val tag = "table#$n rate $rate base=${base.toList()} q=${q.toList()}"
                if (q.sum() > 1.0 + 1e-9) failures += "1. one pick, sum ${q.sum()} $tag"
                var fits = 0.0
                base.indices.forEach { i ->
                    if (base[i] <= 0.0) { if (q[i] != 0.0) failures += "0-line got a chance $tag"; return@forEach }
                    if (q[i] > min(1.0, rate * base[i]) + 1e-9) failures += "2. line $i above rate x $tag"
                    if (q[i] < 0.5 * base[i] - 1e-9) failures += "2. line $i below half its own chance $tag"
                    fits += min(1.0, rate * base[i])
                }
                if (fits <= 1.0) base.indices.forEach { if (base[it] > 0 && abs(q[it] - min(1.0, rate * base[it])) > 1e-9) failures += "4. fits but line $it is not rate x $tag" }
                val live = base.indices.filter { base[it] > 0 }
                for (a in live) for (b in live) {
                    if (base[a] < base[b] - 1e-12 && q[a] / base[a] < q[b] / base[b] - 1e-9) failures += "3. rarer line $a boosted less than commoner $b $tag"
                    if (abs(base[a] - base[b]) < 1e-12 && abs(q[a] - q[b]) > 1e-9) failures += "5. equal lines $a/$b differ $tag"
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} violations, first:\n" + failures.take(8).joinToString("\n"))
    }

    @Test
    fun `pick lays the chances end to end and the rest is nothing`() {
        val q = doubleArrayOf(0.1, 0.2, 0.3)
        assertEquals(0, DropBoost.pick(q, 0.0))
        assertEquals(0, DropBoost.pick(q, 0.0999))
        assertEquals(1, DropBoost.pick(q, 0.1))
        assertEquals(2, DropBoost.pick(q, 0.3001))
        assertEquals(2, DropBoost.pick(q, 0.5999))
        assertEquals(-1, DropBoost.pick(q, 0.6))
        assertEquals(-1, DropBoost.pick(q, 0.9999))
    }
}

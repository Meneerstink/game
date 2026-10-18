package gg.rsmod.plugins.content.drops

import kotlin.math.abs

/**
 * N100 (owner 2026-10-18, VRAGEN 34 a = A "zeldzame items eerst"): the Deadman "Annihilation" drop rate (3x / 3x / 4x / 5x) for ONE
 * roll, a probability-form port of Void's `DropTable.roll(multiplier)` (Donors/void/engine/src/main/kotlin/world/gregs/voidps/
 * engine/entity/item/drop/DropTable.kt). The three parts of Void's mechanism, in this order:
 *
 *  1. `random(maximum, multiplier)` :34-44 - the roll's denominator is divided by the multiplier (`base / multiplier`) and
 *     clamped so it never goes below what the table occupies (`coerceIn(minRoll, base)`): [denominator] = 1/rate, a roll over that
 *     shorter range lands on a line rate x as often; when the lines occupy less than the shorter range, all of them fit;
 *  2. `collect` :49 - the lines are walked rarest first (`drops.sortedBy { it.chance }`, "preserve items with the lowest chances
 *     when table is full"): the lines at the front are the ones served at rate x;
 *  3. `collect` :58-66 - a nested table is entered when the roll lands in its part and is then rolled over its OWN whole range:
 *     here the common lines at the back of the walk are such a nested table, so when the table is full they all give way
 *     together, in proportion, and none becomes unreachable (the plain tail cut of step 2 would drop coins/runes completely).
 *
 * A roll is still ONE pick. ONE addition to Void, in ONE place, because the owner decided "gewone items iets minder": the rare
 * tier may use at most `1 - [COMMON_KEEP]` of the shortened range ([tierLimit]), so the commons keep at least [COMMON_KEEP] of their
 * own chance; Void has no equivalent (its commons never give way because its floor `minRoll` is the whole occupied part).
 *
 * The roll ([DropTableFactory], Barrows chest, breach loot) and every display (Drop Viewer, N108) read this one function
 * (ROOT_CAUSES 4 / 15: a viewer with its own copy of the formula shows chances the roll does not give).
 */
object DropBoost {
    /** PROVISIONAL (owner: "gewone items iets minder"): the share of its own chance a common line keeps at least. */
    const val COMMON_KEEP = 0.5

    private const val EPS = 1e-12

    /** Void `random()` :40-42: the roll range at [rate]: the table (1.0) divided by the multiplier. */
    fun denominator(rate: Double): Double = if (rate <= 1.0) 1.0 else 1.0 / rate

    /** How much of the shortened roll range the rarest lines may use together (the commons keep [COMMON_KEEP] of the rest). */
    fun tierLimit(rate: Double): Double = denominator(rate) * (1.0 - COMMON_KEEP)

    /**
     * The boosted chance of every line. [base] = the chance of each line of ONE roll (their sum is at most 1, the rest is
     * "nothing"; 0 = a line that never counts, such as an explicit `nothing`). At [rate] <= 1 the chances come back unchanged.
     */
    fun chances(
        base: DoubleArray,
        rate: Double,
    ): DoubleArray {
        if (rate <= 1.0) return base.copyOf()
        val out = DoubleArray(base.size)
        // Void collect :49 - rarest first (stable, so equal lines keep their list order and stay together).
        val order = base.indices.filter { base[it] > 0.0 }.sortedBy { base[it] }
        val occupied = order.sumOf { base[it] }
        val scaled = denominator(rate)
        // Void random() :42 - everything occupied fits in the shortened range: every line is rate x (the "nothing" share shrinks).
        if (occupied <= scaled + EPS) {
            for (i in order) out[i] = base[i] / scaled
            return out
        }
        // The rare tier: the rarest lines (equal lines all or none) while they fit in the tier limit; each is rate x.
        val limit = tierLimit(rate)
        var tierMass = 0.0
        var at = 0
        while (at < order.size) {
            var end = at
            var groupMass = 0.0
            while (end < order.size && abs(base[order[end]] - base[order[at]]) < EPS) {
                groupMass += base[order[end]]
                end++
            }
            if (tierMass + groupMass > limit + EPS) break
            for (n in at until end) out[order[n]] = base[order[n]] / scaled
            tierMass += groupMass
            at = end
        }
        // Void collect :58-66 - what the tier did not take is the nested table of commons, rolled over its own whole range.
        val commonMass = occupied - tierMass
        val commonShare = 1.0 - tierMass / scaled
        for (n in at until order.size) out[order[n]] = commonShare * base[order[n]] / commonMass
        return out
    }

    /** The line a roll of [roll] (0 until 1) lands on, or -1 for "nothing" (the chances are laid end to end in line order). */
    fun pick(
        chances: DoubleArray,
        roll: Double,
    ): Int {
        var r = roll
        for (i in chances.indices) {
            r -= chances[i]
            if (r < 0.0) return i
        }
        return -1
    }
}

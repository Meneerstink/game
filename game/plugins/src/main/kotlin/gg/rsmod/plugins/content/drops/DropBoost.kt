package gg.rsmod.plugins.content.drops

import kotlin.math.abs
import kotlin.math.min

/**
 * N100 (owner 2026-10-18, VRAGEN 34 a = A "zeldzame items eerst"): the ONE formula that turns the chances of one roll's lines
 * into the boosted chances of the Deadman "Annihilation" drop rate (3x / 3x / 4x / 5x). A roll still gives AT MOST ONE line
 * (one pick), so the boost cannot be "every line x k" at once on a table that always drops something; instead:
 *
 *  - lines are served rarest first: each gets `k x its chance` (never more than 100 %), the table's "nothing" share is the
 *    first thing to be used up;
 *  - once the budget (100 %) is spent, the common lines (coins, runes ...) give way, but never below [COMMON_KEEP] of their
 *    own chance ("gewone items iets minder"), so no line becomes unreachable (Void's clamp, Donors/void/engine/.../drop/
 *    DropTable.kt:29-44 "coerceIn(minRoll, base)", has the same promise and Void's `collect` sorts rarest first too, :46-49);
 *  - lines with the same chance are one group and share what is left equally.
 *
 * The roll ([DropTableFactory], Barrows chest, breach loot) and every display (Drop Viewer, N108) read this one function
 * (ROOT_CAUSES 4 / 15: a viewer with its own copy of the formula shows chances the roll does not give).
 */
object DropBoost {
    /** PROVISIONAL: the share of its own chance a common line keeps when the rare lines have used the budget (owner: "iets minder"). */
    const val COMMON_KEEP = 0.5

    private const val EPS = 1e-12

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
        val order = base.indices.filter { base[it] > 0.0 }.sortedBy { base[it] }
        // Everything fits: each line simply gets its rate x (the "nothing" share shrinks).
        if (order.sumOf { min(1.0, base[it] * rate) } <= 1.0 + EPS) {
            for (i in order) out[i] = min(1.0, base[i] * rate)
            return out
        }
        var budget = 1.0
        var massAfter = order.sumOf { base[it] }
        var at = 0
        while (at < order.size) {
            val p = base[order[at]]
            var end = at
            var groupMass = 0.0
            while (end < order.size && abs(base[order[end]] - p) < EPS) {
                groupMass += base[order[end]]
                end++
            }
            val count = end - at
            massAfter -= groupMass
            // Rate x chance while the budget allows it, never below the floor, never more than the budget minus what the commons keep.
            val room = ((budget - COMMON_KEEP * massAfter) / count).coerceAtLeast(0.0)
            val q = min(min(1.0, p * rate), room)
            for (n in at until end) out[order[n]] = q
            budget -= q * count
            at = end
        }
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

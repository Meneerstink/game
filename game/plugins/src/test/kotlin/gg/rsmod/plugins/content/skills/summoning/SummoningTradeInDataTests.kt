package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase P of the re-audit: the Bogrog shard trade-in data, across the whole roster.
 *
 * `ObeliskRenewalTests` already covers the obelisk side of this phase - every sourced obelisk
 * exposing renew and infuse, renewing restoring a drained account, the special-move pool being left
 * alone, and a Summoning potion dose. What nothing covered is the **trade-in** numbers Bogrog reads:
 * how many shards a pouch or scroll returns, at what level, and how many scrolls make up one swap.
 *
 * Those are per-familiar values on the sourced tables, and a zero or negative one is not a
 * cosmetic fault: it is either a free swap or one that can never complete. Asserted roster-wide and
 * naming offenders, as internal-consistency rules rather than invented quantities.
 */
class SummoningTradeInDataTests {
    private val roster = SummoningPouchData.values().toList()

    /** Trading a pouch back must return something, or the option is a way to destroy pouches. */
    @Test
    fun `every pouch returns shards when traded in, all 78`() {
        val faults =
            roster.filter { it.numSwapShards <= 0 }
                .map { "${it.name} returns ${it.numSwapShards} shards" }
        assertTrue(faults.isEmpty(), "${faults.size} pouches return no shards on trade-in: $faults")
    }

    /**
     * The level at which a pouch becomes swappable has to be a level a player can reach.
     *
     * Deliberately **not** asserted: any relationship between this and the pouch's own creation
     * level. The first version of this test required the swap level to be no higher than the
     * creation level, on the reasoning that a pouch you can make but never trade back would be a
     * dead end - and 77 of the 78 "failed" it. The data is not wrong; the premise was. Every entry
     * sits above its creation level in a smooth, deliberate progression that converges on 99
     * (Spirit wolf 1 to 21, Pack yak 96 to 97), which is a sourced relationship of some kind rather
     * than an accident. Without a source describing it, no rule about the two levels is asserted
     * here.
     */
    @Test
    fun `every pouch's shard-swap level is a reachable Summoning level, all 78`() {
        val faults =
            roster.filterNot { it.shardSwapLevel in 1..99 }
                .map { "${it.name} swaps at level ${it.shardSwapLevel}" }
        assertTrue(faults.isEmpty(), "${faults.size} pouches have an unreachable swap level: $faults")
    }

    /** The same on the scroll side: a scroll swap must return shards. */
    @Test
    fun `every scroll returns shards when traded in`() {
        val faults =
            SummoningScrollData.values()
                .filter { it.numSwapShards <= 0 }
                .map { "${it.name} returns ${it.numSwapShards} shards" }
        assertTrue(faults.isEmpty(), "${faults.size} scrolls return no shards on trade-in: $faults")
    }

    /**
     * How many scrolls one swap consumes. Zero would make the swap free and unbounded; a negative
     * value would hand scrolls back rather than take them.
     */
    @Test
    fun `every scroll swap consumes at least one scroll`() {
        val faults =
            SummoningScrollData.values()
                .filter { it.numNeededToSwap < 1 }
                .map { "${it.name} consumes ${it.numNeededToSwap} scrolls per swap" }
        assertTrue(faults.isEmpty(), "${faults.size} scroll swaps consume nothing: $faults")
    }

    /**
     * Every scroll must be reachable from a pouch the player can actually hold, or its trade-in
     * row is unreachable regardless of the numbers on it.
     */
    @Test
    fun `every scroll's trade-in row is reachable from a real pouch`() {
        val pouchItems = roster.map { it.pouch }.toSet()
        val faults =
            SummoningScrollData.values()
                .filter { scroll -> scroll.pouches.none { it in pouchItems } }
                .map { "${it.name} names no pouch on the roster" }
        assertTrue(faults.isEmpty(), "${faults.size} scrolls are unreachable from any pouch: $faults")
    }
}

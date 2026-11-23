package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.ArtioCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.CalvarionCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses
import gg.rsmod.plugins.content.areas.wilderness.bosses.VenenatisCombat
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids

/**
 * ZORYA-ASCENSION (owner 23-11 18:xx: "Drop wildy-bosses"): OSRS hands these out from the Fractured Archive, which does not
 * exist here, so the wilderness bosses drop them. Owner: tome + crossbow rare (~1/512), shards more often. One extra roll per
 * kill (its own table, like the bosses' tertiaries), so it never replaces the boss's own unique roll and shows in the Drop Viewer.
 */
object ArchiveDrops {
    const val TABLE = "archive"
    data class Rates(val tome: Int, val crossbow: Int, val shards: Int, val shardAmount: IntRange)

    /** The three classic bosses + Scorpia + Chaos Elemental. */
    val BIG = Rates(tome = 512, crossbow = 512, shards = 64, shardAmount = 3..8)
    /** The singles-plus bosses (Artio/Spindel/Calvar'ion) and the two demi-bosses: half the unique rate. */
    val SMALL = Rates(tome = 1024, crossbow = 1024, shards = 96, shardAmount = 2..5)

    fun rates(npcId: Int): Rates? = when (npcId) {
        OldWildernessBosses.CALLISTO, OldWildernessBosses.VETION, VenenatisCombat.NPC, Npcs.SCORPIA, Npcs.CHAOS_ELEMENTAL -> BIG
        ArtioCombat.NPC, VenenatisCombat.SPINDEL, CalvarionCombat.NORMAL, Npcs.CHAOS_FANATIC, Npcs.CRAZY_ARCHAEOLOGIST -> SMALL
        else -> null
    }

    /** Adds the archive table to [builder] for a wilderness boss; nothing when the items are not in this cache. */
    fun addTo(builder: DropTableBuilder, npcId: Int) {
        val r = rates(npcId) ?: return
        val tome = Dmm2Ids.item(34727) ?: return // uncharged; the player charges it with astral + nature runes (PoweredStaves.ZORYA).
        val crossbow = Dmm2Ids.item(34681) ?: return
        val shards = Dmm2Ids.item(34669) ?: return
        // Common denominator of the three lines: tome 1/t, crossbow 1/c, shards 1/s.
        val total = lcm(lcm(r.tome, r.crossbow), r.shards)
        builder.table(TABLE) {
            total(total)
            obj(tome, slots = total / r.tome)
            obj(crossbow, slots = total / r.crossbow)
            obj(shards, r.shardAmount, slots = total / r.shards)
            nothing(total - total / r.tome - total / r.crossbow - total / r.shards)
        }
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b
}

package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import kotlin.math.floor

/**
 * OSRS-IMPORT Voidwaker - Disrupt (OSRS Wiki "Voidwaker" raw wikitext; wiki DPS calculator weirdgloop/osrs-dps-calc
 * `PlayerVsNPCCalc.ts`, read 2026-09-14): 50% energy; a guaranteed hit (`PLAYER_ACCURACY_FINAL = 1.0`) dealing
 * "50-150% of the wielder's maximum melee hit" as magic damage: `minHit = trunc(max * 1/2)`, `maxHit = max + minHit`;
 * the damage is magic-type and grants Magic experience.
 */
object Voidwaker {
    const val SPECIAL_ENERGY = 50

    /** (minimum, maximum) Disrupt damage for the melee formula's [maxHit]. */
    fun disruptRange(maxHit: Double): Pair<Int, Int> {
        val max = floor(maxHit + 1e-9).toInt()
        val minimum = max / 2
        return minimum to max + minimum
    }

    fun minHitArgument(minimum: Int): Double = if (minimum > 0) minimum.toDouble() else DEFAULT_MIN_HIT
}

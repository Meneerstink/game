package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import gg.rsmod.game.model.combat.StyleType
import kotlin.math.floor

/**
 * OSRS-IMPORT Osmumten's fang passives (OSRS Wiki "Osmumten's fang" raw wikitext; the wiki DPS calculator
 * weirdgloop/osrs-dps-calc `PlayerVsNPCCalc.ts` + `BaseCalc.getFangAccuracyRoll`, read 2026-09-14).
 *
 * - Accuracy, stab styles only: outside Tombs of Amascut (not present in this server) the hit chance is
 *   `1 - (d+2)(2d+3) / (6(a+1)^2)` when a > d, otherwise `a(4a+5) / (6(a+1)(d+1))` (both branches agree at a = d).
 * - Damage, every style: `shrink = trunc(max * 3 / 20)`; a hit rolls between shrink and `max - shrink` ("between 15% and
 *   85% of the maximum hit"); Eviscerate keeps the true max, rolling between shrink and max.
 * - Eviscerate: 25% special energy, "a 50% increase to accuracy".
 */
object OsmumtensFang {
    const val SPECIAL_ENERGY = 25
    const val SPECIAL_ACCURACY = 1.5

    fun isWielding(player: Player): Boolean = player.hasEquipped(EquipmentType.WEAPON, Items.OSMUMTENS_FANG)

    fun usesFangAccuracy(player: Player): Boolean = isWielding(player) && CombatConfigs.getCombatStyle(player) == StyleType.STAB

    /** `BaseCalc.getFangAccuracyRoll` for the non-negative attack and defence rolls this server produces. */
    fun hitChance(
        attack: Double,
        defence: Double,
    ): Double =
        if (attack > defence) {
            1.0 - (defence + 2.0) * (2.0 * defence + 3.0) / (6.0 * (attack + 1.0) * (attack + 1.0))
        } else {
            attack * (4.0 * attack + 5.0) / (6.0 * (attack + 1.0) * (defence + 1.0))
        }

    /** The (minimum, maximum) damage of a successful fang hit for the formula's [maxHit]. */
    fun damageRange(
        maxHit: Double,
        special: Boolean,
    ): Pair<Int, Int> {
        val max = floor(maxHit + 1e-9).toInt()
        val shrink = max * 3 / 20
        return shrink to if (special) max else max - shrink
    }

    /** The `minHit` argument for `dealHit`: a zero minimum keeps the standard roll (a landed 0 shows as 1). */
    fun minHitArgument(minimum: Int): Double = if (minimum > 0) minimum.toDouble() else DEFAULT_MIN_HIT
}

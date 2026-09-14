package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import kotlin.math.floor

/**
 * OSRS-IMPORT Snapshot for the Magic shortbow and Magic shortbow (i) (OSRS Wiki "Magic shortbow" and "Magic shortbow (i)" raw
 * wikitext, fetched 2026-09-14):
 * - Magic shortbow: "Energy Cost: 55%" (22 February 2005: 35% -> 55%); Magic shortbow (i): "retains the standard magic shortbow's
 *   special attack, Snapshot, which fires two arrows in rapid succession but with lowered accuracy, using 50% of the player's
 *   special attack energy".
 * - Accuracy: an approximately 43% boost (10/7), despite the "lowered accuracy" tooltip.
 * - "Maximum Hit = ⌊0.5 + ((Visible Ranged Level + 10) × (Ammo Ranged Strength + 64))/640⌋", excluding offensive prayers, the
 *   ranged strength of armour and gear, and Slayer helmet (i) / Salve amulet (i)(ei) bonuses.
 * SHARED 667 ID CHANGED ON PURPOSE (owner: combat exactly OSRS): the 667 Magic shortbow Snapshot used ×0.9 accuracy and the normal
 * max hit (2009scape port). ADAPTED_TO_667: the 667 Snapshot animation 1074, graphic 250, arrow 249 and sound.
 */
object MagicShortbowSnapshot {
    const val MSB_ENERGY = 55
    const val MSB_I_ENERGY = 50
    const val ACCURACY = 10.0 / 7.0

    fun energy(weaponId: Int): Int? =
        when (weaponId) {
            Items.MAGIC_SHORTBOW -> MSB_ENERGY
            Items.MAGIC_SHORTBOW_I -> MSB_I_ENERGY
            else -> null
        }

    fun maxHit(
        visibleRangedLevel: Int,
        ammoRangedStrength: Int,
    ): Double = floor(0.5 + (visibleRangedLevel + 10) * (ammoRangedStrength + 64) / 640.0)
}

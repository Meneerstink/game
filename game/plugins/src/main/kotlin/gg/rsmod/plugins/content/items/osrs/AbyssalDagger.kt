package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT Abyssal dagger - Abyssal Puncture (OSRS Wiki "Abyssal dagger" raw wikitext; wiki DPS calculator
 * weirdgloop/osrs-dps-calc `PlayerVsNPCCalc.ts`, read 2026-09-14): "Hits twice in quick succession with a 25% increase in
 * accuracy and 15% reduced damage, consuming 25% of the player's special attack energy"; "A single attack roll is
 * performed, meaning that either both hits are successful, or both hits miss". Calculator: attack roll x 5/4, max hit
 * x 17/20, `defenceStyle = 'slash'`, the second hit is a separate damage roll over the same range. The poisoned
 * variants have identical stats; their cache-name poison markers are handled by the shared [WeaponPoison] roster.
 */
object AbyssalDagger {
    const val SPECIAL_ENERGY = 25
    const val SPECIAL_ACCURACY = 1.25
    const val SPECIAL_DAMAGE = 0.85

    val IDS: IntArray = intArrayOf(Items.ABYSSAL_DAGGER, Items.ABYSSAL_DAGGER_P, Items.ABYSSAL_DAGGER_P_PLUS, Items.ABYSSAL_DAGGER_P_PLUS_PLUS)
}

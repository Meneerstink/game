package gg.rsmod.plugins.content.combat.strategy.ranged

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items

/**
 * Chinchompa (grey, red, black) ranged mechanics, one shared model (OSRS-IMPORT step 3; OSRS Wiki "Black chinchompa" raw
 * wikitext 2026-09-14 and the wiki DPS calculator `PlayerVsNPCCalc.ts`):
 * - "Chinchompas' accuracy is based off of the distance from the closest tile of the target": 0-3 / 4-6 / 7+ squares ->
 *   Short fuse 100 / 75 / 50 %, Medium fuse 75 / 100 / 75 %, Long fuse 50 / 75 / 100 % (calculator: attack roll x n / 4).
 * - "In PvM, they can hit up to 12 (as opposed to 11 with other chinchompas) targets in a 3x3 area. In PvP, the cap is 10
 *   (as opposed to 9 with other chinchompas)."
 * - "if the primary target is hit, all secondary targets are hit automatically; if the primary target is missed, all secondary
 *   targets are missed automatically."
 * The 667 grey/red chinchompas had neither rule (ADJACENT FIX, same subsystem).
 */
object Chinchompas {
    val BASIC = setOf(Items.CHINCHOMPA_10033, Items.RED_CHINCHOMPA_10034)
    val BLACK = setOf(Items.BLACK_CHINCHOMPA)

    fun isChinchompa(weaponId: Int?): Boolean = weaponId in BASIC || weaponId in BLACK

    /** Chebyshev distance from [from] to the closest tile of a target of [size] standing on [target]. */
    fun distanceToClosestTile(
        from: Tile,
        target: Tile,
        size: Int,
    ): Int {
        val dx = maxOf(target.x - from.x, 0, from.x - (target.x + size - 1))
        val dz = maxOf(target.z - from.z, 0, from.z - (target.z + size - 1))
        return maxOf(dx, dz)
    }

    /** Numerator n of the n / 4 accuracy factor; [styleIndex] 0 = Short fuse, 1 = Medium fuse, 2 = Long fuse. */
    fun accuracyNumerator(
        styleIndex: Int,
        distance: Int,
    ): Int {
        val band = when {
            distance <= 3 -> 0
            distance <= 6 -> 1
            else -> 2
        }
        val table =
            when (styleIndex) {
                0 -> intArrayOf(4, 3, 2)
                1 -> intArrayOf(3, 4, 3)
                else -> intArrayOf(2, 3, 4)
            }
        return table[band]
    }

    /** Most targets one throw can hit, the primary target included. */
    fun maxTargets(
        weaponId: Int?,
        pvp: Boolean,
    ): Int {
        val black = weaponId in BLACK
        return if (pvp) (if (black) 10 else 9) else (if (black) 12 else 11)
    }
}

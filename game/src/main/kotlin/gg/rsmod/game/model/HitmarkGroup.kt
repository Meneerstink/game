package gg.rsmod.game.model

/**
 * An OSRS hitsplat group (owner 2026-10-09: hitsplats exactly like OSRS), ported from RSMod `HitmarkTypeGroup` /
 * `BaseHitmarkGroups` + `InternalPlayerHits` / `HitBuilder` (github rsmod/rsmod). The ids are OSRS hitsplat ids (cache
 * 2686 config group 32, RuneLite `HitsplatID`), imported 1:1 into our config group 46 by `OsrsHitsplatImportTool`.
 *
 * Who sees which splat (RSMod HitBuilder): the target and the hit's source see the [lit] splat, every other player the
 * [tint] splat (or [lit] when the group has none). A regular-damage hit of 0 is the block splat (12 lit / 13 tint). The
 * target and a player source see the [max] splat when the damage is at least max(10, the source's max hit); NPC sources
 * never show it.
 */
class HitmarkGroup(
    val lit: Int,
    val tint: Int = -1,
    val max: Int = -1,
    /** Part of OSRS `regular_damage`: a 0 shows the zero-damage (block) splat. */
    val regular: Boolean = false,
) {
    /** The OSRS hitsplat id for [damage], seen by the target / source ([privileged]) or by anyone else. */
    fun resolve(
        damage: Int,
        privileged: Boolean,
        maxThreshold: Int,
    ): Int {
        if (regular && damage == 0) return if (privileged) ZERO_DAMAGE.lit else ZERO_DAMAGE.tint
        if (privileged) return if (max >= 0 && damage >= maxThreshold) max else lit
        return if (tint >= 0) tint else lit
    }

    companion object {
        /** RSMod `constants.setting_lit_maxhit_min_threshold`: the max-hit splat never shows below 10. */
        const val MAX_HIT_MIN_THRESHOLD = 10

        val ZERO_DAMAGE = HitmarkGroup(lit = 12, tint = 13)
        val REGULAR_DAMAGE = HitmarkGroup(lit = 16, tint = 17, max = 43, regular = true)

        /** Ring of recoil / vengeance reflections: untinted for every viewer since 2020 (OSRS Wiki "Hitsplat"), no max splat. */
        val UNTINTED_DAMAGE = HitmarkGroup(lit = 16, regular = true)
        val POISON = HitmarkGroup(lit = 65, tint = 66)
        val DISEASE = HitmarkGroup(lit = 4)
        val VENOM = HitmarkGroup(lit = 5)
        val HEAL = HitmarkGroup(lit = 6)
        val BURN = HitmarkGroup(lit = 74)
    }
}

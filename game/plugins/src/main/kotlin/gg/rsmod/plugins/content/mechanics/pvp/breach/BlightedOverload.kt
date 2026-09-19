package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items

/**
 * Blighted overload (OSRS Wiki "Blighted overload", permanent Deadman; owner 2026-09-19 "fix everything" - Chitin's use).
 * Items imported by `OsrsItemImportTool blighted-overload` (tx-20260919-194428): upstream 29631/29634/29637/29640.
 *
 * "Players can make this potion at level 83 Herblore by combining a 4-dose super combat potion, ranging potion, and magic potion
 * with a piece of chitin" (125 xp, 2 ticks). "A dose ... can only be drunk in dangerous areas, will boost the player's offensive
 * skills, reduce the player's Defence, and cause the player to take 10 damage over the course of 6 seconds (2 damage every 2 ticks).
 * The stat boosts are re-applied every 15 seconds for five minutes ...; for Defence, the level will decay if it is over 90% of the
 * player's base level. The blighted overload effect will be removed if the player goes into a safezone with the effect active for
 * longer than 11 ticks."
 */
object BlightedOverload {
    /** Doses 4, 3, 2, 1 (the noted copy of each is id + 1). */
    val DOSES = intArrayOf(23830, 23832, 23834, 23836)

    val CHITIN = ItemIds.CHITIN
    val INGREDIENT_POTIONS = intArrayOf(Items.SUPER_COMBAT_POTION_4, Items.RANGING_POTION_4, Items.MAGIC_POTION_4)

    const val LEVEL = 83
    const val EXPERIENCE = 125.0
    const val MIX_TICKS = 2

    /** Five minutes of 0.6 s ticks, boosts re-applied every 15 seconds. */
    const val DURATION_TICKS = 500
    const val REAPPLY_TICKS = 25

    /** "2 damage every 2 ticks" for 6 seconds = 10 damage. */
    const val DAMAGE_PER_HIT = 2
    const val DAMAGE_HITS = 5
    const val DAMAGE_INTERVAL = 2

    /** "removed if the player goes into a safezone with the effect active for longer than 11 ticks". */
    const val SAFE_ZONE_GRACE_TICKS = 11

    fun attackStrengthBoost(level: Int) = (0.15 * level).toInt() + 8

    fun rangedBoost(level: Int) = level / 10 + 7

    fun magicBoost(level: Int) = level / 10 + 1

    fun defenceDrain(level: Int) = level / 10 + 1

    /** Boosted current levels from the base levels: Attack, Strength, Ranged, Magic. */
    fun boosted(skill: Int, base: Int): Int =
        when (skill) {
            Skills.ATTACK, Skills.STRENGTH -> base + attackStrengthBoost(base)
            Skills.RANGED -> base + rangedBoost(base)
            Skills.MAGIC -> base + magicBoost(base)
            else -> base
        }

    /**
     * The drained Defence: lowered by the drain, never below 90% of the base ("the level will decay if it is over 90% of the
     * player's base level"; January 2026: "decays down to 90% of its original value"). A level already at or below 90% stays.
     */
    fun drainedDefence(current: Int, base: Int): Int {
        val floor = (base * 9) / 10
        return if (current > floor) maxOf(floor, current - defenceDrain(base)) else current
    }

    fun doseOf(item: Int): Int = DOSES.indexOf(item).let { if (it < 0) 0 else 4 - it }

    /** The item left after drinking one dose of [item]: the next lower dose, or a vial after the last. */
    fun afterSip(item: Int): Int = DOSES.indexOf(item).let { if (it < 0 || it == DOSES.lastIndex) Items.VIAL else DOSES[it + 1] }
}

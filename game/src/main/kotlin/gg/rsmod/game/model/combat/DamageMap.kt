package gg.rsmod.game.model.combat

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Pawn
import java.util.*

/**
 * Represents a map of hits from different [Pawn]s and their information.
 *
 * Audit T-12: a hit is stamped with the game cycle it landed on ([currentCycle]) instead of the wall
 * clock, so a time window counts ticks: lag or a burst of catch-up ticks no longer stretches or shrinks
 * the kill-credit window.
 *
 * @param currentCycle the current game cycle; [Pawn] passes `world.currentCycle`. The default (always 0)
 * is only for maps built outside a world, such as in tests.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class DamageMap(
    private val currentCycle: () -> Int = { 0 },
) {
    private val map = WeakHashMap<Pawn, DamageStack>(0)

    operator fun get(pawn: Pawn): DamageStack? = map[pawn]

    fun add(
        pawn: Pawn,
        damage: Int,
    ) {
        val total = (map[pawn]?.totalDamage ?: 0) + damage
        map[pawn] = DamageStack(total, currentCycle(), System.currentTimeMillis())
    }

    /**
     * Get all [DamageStack]s dealt by [Pawn]s whom meets the criteria
     * [Pawn.entityType] == [type].
     *
     * @param timeFrameCycles when given, only stacks whose last hit landed fewer than this many
     * game cycles ago.
     */
    fun getAll(
        type: EntityType,
        timeFrameCycles: Int? = null,
    ): Collection<DamageStack> {
        val now = currentCycle()
        return map
            .filter { it.key.entityType == type && withinWindow(it.value.lastHitCycle, now, timeFrameCycles) }
            .values
    }

    /**
     * Get the total damage from a [pawn].
     *
     * @return
     * 0 if [pawn] has not dealt any damage.
     */
    fun getDamageFrom(pawn: Pawn): Int = map[pawn]?.totalDamage ?: 0

    /**
     * Gets the [Pawn] that has dealt the most damage in this map.
     *
     * @param timeFrameCycles
     * When given, only [Pawn]s whose most recent hit landed fewer than this many
     * game cycles ago are considered. Without it this map has no notion of "this
     * fight" at all - it accumulates for as long as the map lives - so an
     * attacker from a fight the victim escaped long ago can still outweigh
     * whatever actually landed the killing blow.
     */
    fun getMostDamage(timeFrameCycles: Int? = null): Pawn? {
        val now = currentCycle()
        return map
            .filter { withinWindow(it.value.lastHitCycle, now, timeFrameCycles) }
            .maxByOrNull { it.value.totalDamage }
            ?.key
    }

    /**
     * Gets the most damage dealt by a [Pawn] in our map whom meets the criteria
     * [Pawn.entityType] == [type].
     */
    fun getMostDamage(
        type: EntityType,
        timeFrameCycles: Int? = null,
    ): Pawn? {
        val now = currentCycle()
        return map
            .filter { it.key.entityType == type && withinWindow(it.value.lastHitCycle, now, timeFrameCycles) }
            .maxByOrNull { it.value.totalDamage }
            ?.key
    }

    fun reset() {
        map.clear()
    }

    /**
     * @param lastHitCycle the game cycle ([Pawn.world]'s `currentCycle`) the last hit landed on; every
     * window in this map uses it (Audit T-12).
     * @param lastHit the wall-clock time of the last hit, in milliseconds. Kept for callers that still
     * measure in wall-clock time (the D-07 fed-kill check in `ValidPkKill`); prefer [lastHitCycle].
     */
    data class DamageStack(
        val totalDamage: Int,
        val lastHitCycle: Int,
        val lastHit: Long,
    )

    companion object {
        /**
         * Audit T-12: true when a hit on [lastHitCycle] is fewer than [windowCycles] cycles before
         * [nowCycle] (always true without a window). A hit on cycle 0 still counts on cycle 99 of a
         * 100-cycle window, but no longer on cycle 100.
         */
        fun withinWindow(
            lastHitCycle: Int,
            nowCycle: Int,
            windowCycles: Int?,
        ): Boolean = windowCycles == null || nowCycle - lastHitCycle < windowCycles
    }
}

package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.filterableMessage

/**
 * Breach Points (owner 2026-09-23: a separate currency, never Deadman Points).
 *
 * OSRS Wiki "Deadman: Annihilation": "The top 100 damage dealers to breach NPCs will earn 1 point per damage dealt" and "After a
 * player earns 75,000 points from breaches, they will no longer be able to earn points there." The event-day cap ("5,000 x the
 * number of days since the start of the event") belongs to a time-boxed event this permanent world does not have and is not
 * applied (ADAPTED). An Archaic emblem (tier 5) trades in for [EMBLEM_TIER_5_VALUE] points ("Archaic emblem" table,
 * Annihilation points); emblem points are spending money and do not count towards the damage cap.
 */
object BreachPoints {
    const val EARNERS = 100
    const val DAMAGE_CAP = 75_000
    const val EMBLEM_TIER_5_VALUE = 1_500_000

    /** Spendable balance. */
    val BALANCE = AttributeKey<Int>("breach_points")

    /** Lifetime points earned from breach damage (the 75,000 cap). */
    val EARNED = AttributeKey<Int>("breach_points_earned")

    fun balance(player: Player): Int = player.attr[BALANCE] ?: 0

    fun earned(player: Player): Int = player.attr[EARNED] ?: 0

    /** Awards up to [damage] points within the lifetime cap; returns the points actually given. */
    fun award(
        player: Player,
        damage: Int,
    ): Int {
        val room = (DAMAGE_CAP - earned(player)).coerceAtLeast(0)
        val given = damage.coerceAtMost(room).coerceAtLeast(0)
        if (given == 0) return 0
        player.attr[EARNED] = earned(player) + given
        addBalance(player, given)
        val capNote = if (earned(player) >= DAMAGE_CAP) " You have reached the ${"%,d".format(DAMAGE_CAP)} breach point limit." else ""
        player.filterableMessage("You earn ${"%,d".format(given)} Breach Points (total ${"%,d".format(balance(player))}).$capNote")
        return given
    }

    fun addBalance(
        player: Player,
        amount: Int,
    ) {
        player.attr[BALANCE] = (balance(player).toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/**
 * Breach damage pays DEADMAN POINTS (DMM-ARMAGEDDON D3, owner 20-11-2026).
 *
 * The separate Breach Points currency is gone - owner 20-11: "deadmanpunten loyalty en donator punten zijn de enige punten in onze
 * rsps de rest verwijderen" (no Breach Trader, no balance, no emblem trade-in). OSRS Wiki Deadman: Armageddon/Points: "Breaches -
 * 1 point per damage dealt to Breach bosses - 42000" and "After earning a total of 42,000 Points from Breaches, no more points will
 * be earned from damaging Breach bosses, although loot will still be awarded"; only the rewarded group (MvP + 15,
 * [BreachContribution.ranking]) earns them ("will not receive loot or points for that kill").
 *
 * [LEGACY_BALANCE] / [LEGACY_EARNED] are the old save keys, read only by [gg.rsmod.plugins.content.mechanics.deadman.BreachRetirement]
 * to convert an old balance into Deadman Points once and clear it.
 */
object BreachPoints {
    /** Lifetime Deadman Points a player can earn from breach damage. */
    const val DP_CAP = 42_000

    /** Old Breach Points balance (save key), migration only. */
    val LEGACY_BALANCE = AttributeKey<Int>("breach_points")

    /** Old lifetime Breach Points earned (save key), migration only. */
    val LEGACY_EARNED = AttributeKey<Int>("breach_points_earned")

    /** Lifetime Deadman Points earned from breach damage (the [DP_CAP]). */
    val DP_EARNED = AttributeKey<Int>("breach_dp_earned")

    fun dpEarned(player: Player): Int = player.attr[DP_EARNED] ?: 0

    /** Pays up to [damage] Deadman Points within the lifetime cap; returns the points actually given. */
    fun awardDp(
        player: Player,
        damage: Int,
    ): Int {
        val room = (DP_CAP - dpEarned(player)).coerceAtLeast(0)
        val wanted = damage.coerceAtMost(room).coerceAtLeast(0)
        if (wanted == 0) return 0
        val key = StoreCatalogue.Currency.DEADMAN.attr
        val before = PointBalance.of(player.attr, key)
        val given = (PointBalance.add(player.attr, key, wanted) - before).toInt().coerceAtLeast(0)
        if (given == 0) return 0
        player.attr[DP_EARNED] = dpEarned(player) + given
        val capNote =
            if (dpEarned(player) >= DP_CAP) " You have reached the ${"%,d".format(DP_CAP)} point limit for breaches." else ""
        player.message("You earn ${"%,d".format(given)} Deadman Points for your breach damage.$capNote")
        return given
    }
}

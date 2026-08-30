package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.skull
import java.lang.ref.WeakReference

/**
 * Confirmed PvP skull policy (PROJECT_PLAN.md SS11/SS22):
 * - Only the player who starts an unprovoked attack receives the normal
 *   skull; the victim and a legitimate retaliator are never skulled for that
 *   exchange.
 * - Duration is 20 minutes; another unprovoked attack refreshes (does not
 *   stack) the timer.
 * - Skull state survives reconnect via the normal attribute/timer
 *   persistence pipeline ([SKULL_ICON_DURATION_TIMER] carries a
 *   persistence key).
 * - The skull feeds into [gg.rsmod.plugins.content.mechanics.death.DeathResolver]
 *   automatically, since it defaults to reading the player's live skull icon.
 *
 * Provisional/configurable: exact duration and the retaliation window below
 * are the confirmed-direction defaults from PROJECT_PLAN.md SS11; both are
 * plain constants here so they're trivial to retune later.
 */
object PvpSkull {
    /** Confirmed duration: 20 minutes, in 0.6s game cycles. */
    const val SKULL_DURATION_CYCLES = 2000

    /**
     * How long a player who was just attacked is allowed to attack back
     * without being treated as the initiator. Provisional: refreshed on
     * every hit received, so it stays open for as long as a fight is active.
     */
    const val AGGRESSOR_WINDOW_CYCLES = 100

    /**
     * Call when [attacker] player-initiates an attack against [victim] via
     * the explicit "Attack" option - never for auto-retaliation, which is
     * how a legitimate retaliation is told apart from a fresh attack (an
     * auto-retaliation never runs through this method).
     */
    fun onPlayerInitiatedAttack(
        attacker: Player,
        victim: Player,
    ) {
        if (!AreaState.canPlayersFight(attacker, victim)) {
            return
        }

        val isRetaliation = attacker.attr[PVP_AGGRESSOR_ATTR]?.get() == victim
        if (!isRetaliation) {
            attacker.skull(SkullIcon.RED, SKULL_DURATION_CYCLES)
        }
        markAggression(attacker, victim)
    }

    /**
     * Refreshes [victim]'s memory of [attacker] as their current PvP
     * aggressor, keeping retaliation recognized for as long as the fight
     * stays active. Safe to call on every landed PvP hit, not just attack
     * initiation.
     */
    fun markAggression(
        attacker: Player,
        victim: Player,
    ) {
        victim.attr[PVP_AGGRESSOR_ATTR] = WeakReference(attacker)
        victim.timers[PVP_AGGRESSOR_WINDOW_TIMER] = AGGRESSOR_WINDOW_CYCLES
    }
}

package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerKey
import java.lang.ref.WeakReference

/**
 * Deadman PvP guards plan PvP skull policy (owner-approved 2026-09-16, supersedes the previous
 * PROJECT_PLAN.md SS11/SS22 20-minute model):
 * - Only the player who starts an unprovoked attack receives the normal skull; the victim and a
 *   legitimate retaliator are never skulled for that exchange.
 * - Duration is 5 minutes. Attacking first always gives a fresh 5-minute skull, even if the
 *   target is already skulled; attacking again while already skulled always resets the timer
 *   back to 5 minutes (every non-retaliation "Attack" click calls [onPlayerInitiatedAttack],
 *   which sets - not adds to - the timer).
 * - The countdown pauses (see [SKULL_PAUSE_CHECK_TIMER]/[tickPauseTracking]) while the player is
 *   in an instanced area (07/client convention: instanced maps live at tile x >= 6400, per
 *   [gg.rsmod.game.model.instance.InstancedMapAllocator]'s own sourced comment) or has been
 *   standing on the same tile for about a minute (100 cycles).
 * - Skull state survives reconnect via the normal attribute/timer persistence pipeline
 *   ([SKULL_ICON_DURATION_TIMER] carries a persistence key); the pause-tracking driver itself is
 *   session-local and simply re-derives pause state from live player state on the first cycle
 *   after reconnect.
 * - The skull feeds into [gg.rsmod.plugins.content.mechanics.death.DeathResolver] automatically,
 *   since it defaults to reading the player's live skull icon.
 */
object PvpSkull {
    /** Owner spec (2026-09-16): 5 minutes, in 0.6s game cycles. */
    const val SKULL_DURATION_CYCLES = 500

    /**
     * How long a player who was just attacked is allowed to attack back
     * without being treated as the initiator. Provisional: refreshed on
     * every hit received, so it stays open for as long as a fight is active.
     */
    const val AGGRESSOR_WINDOW_CYCLES = 100

    /** ~1 minute of standing on the same tile pauses the skull countdown (owner spec, 2026-09-16). */
    const val SAME_TILE_STALL_CYCLES = 100

    /** Set on a pawn for the duration of an engine auto-retaliation `attack` call, so the shared
     * combat-start hook can tell it apart from a deliberate attack. */
    val AUTO_RETALIATING_ATTR = AttributeKey<Boolean>()

    /** Per-cycle driver for [tickPauseTracking]; session-local, cleared on death like the skull itself. */
    val SKULL_PAUSE_CHECK_TIMER = TimerKey(tickOffline = false, resetOnDeath = true)

    private val SKULL_STALL_TILE_ATTR = AttributeKey<Tile>()
    private val SKULL_STALL_CYCLES_ATTR = AttributeKey<Int>()

    /**
     * Whether [player] is PK-skulled. The skull STATE is the running (persisted, death-reset)
     * [SKULL_ICON_DURATION_TIMER]; the head ICON is derived from it by [RiskSkull.refresh] every
     * cycle in the risk-tier colour (owner 2026-09-17: "the skull above the head colour needs to be
     * updating ... when the risk changes of a player it needs to recalculate and change colors
     * depending on risk"). Nothing may test the icon id to learn whether a player is skulled.
     */
    fun isSkulled(player: Player): Boolean = player.timers.exists(SKULL_ICON_DURATION_TIMER)

    /** Starts (or restarts) the 5-minute skull and shows the risk-coloured icon at once. */
    private fun applySkull(player: Player) {
        player.timers[SKULL_ICON_DURATION_TIMER] = SKULL_DURATION_CYCLES
        RiskSkull.refresh(player)
        armPauseTracking(player)
    }

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

        // Owner 2026-09-17 (live retest): "when a player attacks another player it needs to always
        // give you a skull" - every deliberate attack of any style (melee, ranged, magic, special)
        // skulls and resets the timer; only the engine's automatic retaliation is exempt
        // ([AUTO_RETALIATING_ATTR], set by Combat.postAttack around the auto-retaliate call).
        applySkull(attacker)
        // Deadman PvP guards plan (2026-09-16): "attacking ... ends it early" - the attacker's
        // own post-kill grace period, if any, ends the moment they initiate a new attack.
        KillGrace.endEarly(attacker)
        markAggression(attacker, victim)
    }

    /** Owner retest helper (`skullme` command): the same 5-minute skull + pause tracking a real
     * unprovoked attack gives, without needing a second account. */
    fun applyTestSkull(player: Player) {
        applySkull(player)
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

    /**
     * (Re)starts the per-cycle pause/HUD driver for [player]'s active skull countdown. Resets
     * the same-tile stall counter so a fresh (or refreshed) skull never inherits a stale stall
     * count from before this attack, and un-pauses immediately since taking the "Attack" action
     * is itself activity.
     */
    private fun armPauseTracking(player: Player) {
        player.attr[SKULL_STALL_TILE_ATTR] = player.tile
        player.attr[SKULL_STALL_CYCLES_ATTR] = 0
        player.timers.resume(SKULL_ICON_DURATION_TIMER)
        player.timers[SKULL_PAUSE_CHECK_TIMER] = 1
    }

    /**
     * Evaluates and applies this cycle's pause state, and refreshes the HUD text on its own
     * 30-second cadence. Called every cycle for as long as [player] has an active red skull;
     * stops rescheduling itself (and clears its bookkeeping attributes) once the skull clears.
     */
    fun tickPauseTracking(player: Player) {
        if (!isSkulled(player)) {
            player.timers.resume(SKULL_ICON_DURATION_TIMER)
            player.attr.remove(SKULL_STALL_TILE_ATTR)
            player.attr.remove(SKULL_STALL_CYCLES_ATTR)
            return
        }

        val lastTile = player.attr[SKULL_STALL_TILE_ATTR]
        val stallCycles =
            if (lastTile == player.tile) {
                (player.attr[SKULL_STALL_CYCLES_ATTR] ?: 0) + 1
            } else {
                player.attr[SKULL_STALL_TILE_ATTR] = player.tile
                0
            }
        player.attr[SKULL_STALL_CYCLES_ATTR] = stallCycles

        val inInstance = player.tile.x >= 6400
        val stalled = stallCycles >= SAME_TILE_STALL_CYCLES
        if (inInstance || stalled) {
            player.timers.pause(SKULL_ICON_DURATION_TIMER)
        } else {
            player.timers.resume(SKULL_ICON_DURATION_TIMER)
        }

        // The remaining time itself is shown on the HUD ([DeadmanHud.skullText], rounded to the
        // half minute), not in the chatbox: no 30-second chat spam.
        player.timers[SKULL_PAUSE_CHECK_TIMER] = 1
    }
}

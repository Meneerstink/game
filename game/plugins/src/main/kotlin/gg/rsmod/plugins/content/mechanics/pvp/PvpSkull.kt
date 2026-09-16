package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.skull
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

    /** HUD remaining-time refresh cadence: 30 seconds (owner spec, 2026-09-16). */
    const val HUD_REFRESH_CYCLES = 50

    /** Per-cycle driver for [tickPauseTracking]; session-local, cleared on death like the skull itself. */
    val SKULL_PAUSE_CHECK_TIMER = TimerKey(tickOffline = false, resetOnDeath = true)

    private val SKULL_STALL_TILE_ATTR = AttributeKey<Tile>()
    private val SKULL_STALL_CYCLES_ATTR = AttributeKey<Int>()
    private val SKULL_HUD_CYCLES_ATTR = AttributeKey<Int>()

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
            armPauseTracking(attacker)
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

    /**
     * (Re)starts the per-cycle pause/HUD driver for [player]'s active skull countdown. Resets
     * the same-tile stall counter so a fresh (or refreshed) skull never inherits a stale stall
     * count from before this attack, and un-pauses immediately since taking the "Attack" action
     * is itself activity.
     */
    private fun armPauseTracking(player: Player) {
        player.attr[SKULL_STALL_TILE_ATTR] = player.tile
        player.attr[SKULL_STALL_CYCLES_ATTR] = 0
        player.attr[SKULL_HUD_CYCLES_ATTR] = 0
        player.timers.resume(SKULL_ICON_DURATION_TIMER)
        player.timers[SKULL_PAUSE_CHECK_TIMER] = 1
    }

    /**
     * Evaluates and applies this cycle's pause state, and refreshes the HUD text on its own
     * 30-second cadence. Called every cycle for as long as [player] has an active red skull;
     * stops rescheduling itself (and clears its bookkeeping attributes) once the skull clears.
     */
    fun tickPauseTracking(player: Player) {
        if (!player.hasSkullIcon(SkullIcon.RED) || !player.timers.exists(SKULL_ICON_DURATION_TIMER)) {
            player.timers.resume(SKULL_ICON_DURATION_TIMER)
            player.attr.remove(SKULL_STALL_TILE_ATTR)
            player.attr.remove(SKULL_STALL_CYCLES_ATTR)
            player.attr.remove(SKULL_HUD_CYCLES_ATTR)
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

        val hudCycles = (player.attr[SKULL_HUD_CYCLES_ATTR] ?: 0) + 1
        if (hudCycles >= HUD_REFRESH_CYCLES) {
            player.attr[SKULL_HUD_CYCLES_ATTR] = 0
            sendSkullTimeRemaining(player)
        } else {
            player.attr[SKULL_HUD_CYCLES_ATTR] = hudCycles
        }

        player.timers[SKULL_PAUSE_CHECK_TIMER] = 1
    }

    /**
     * SOURCE_BLOCKED (2026-09-16): the owner's reference screenshot ("skull timer and icon.png")
     * shows a dedicated "5:00"-style countdown display, but pinning the exact interface/component
     * id it lives on needs the same read-only cache-decode investigation that produced
     * `2011scape-client/2011scape-client/NIGHT_WILDERNESS_HANDOFF.md` for interface 381 - not yet
     * done for the skull timer. Until that investigation identifies the real component, the
     * remaining time is surfaced through the existing filterable chat message channel (a real,
     * always-safe mechanism already used throughout this codebase) instead of guessing a cache
     * component id. Replace this with the correct setComponentText call once sourced - see the M1
     * handoff for the open follow-up.
     */
    private fun sendSkullTimeRemaining(player: Player) {
        val cyclesLeft = if (player.timers.exists(SKULL_ICON_DURATION_TIMER)) player.timers[SKULL_ICON_DURATION_TIMER] else 0
        val secondsLeft = (cyclesLeft * 0.6).toInt().coerceAtLeast(0)
        val minutes = secondsLeft / 60
        val seconds = secondsLeft % 60
        player.filterableMessage("Your skull will disappear in %d:%02d.".format(minutes, seconds))
    }
}

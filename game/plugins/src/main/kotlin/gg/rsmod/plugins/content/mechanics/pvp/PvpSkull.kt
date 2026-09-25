package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.LEGACY_SKULL_ICON_DURATION_TIMER
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
 *   back to 5 minutes (every non-retaliation registered hit calls [onHitRegistered], which
 *   sets - not adds to - the timer). Owner 2026-09-18: the skull is granted when the hitsplat
 *   registers on the target, never when the attack merely starts.
 * - The countdown pauses (see [SKULL_PAUSE_CHECK_TIMER]/[tickPauseTracking]) while the player is
 *   in an instanced area (07/client convention: instanced maps live at tile x >= 6400, per
 *   [gg.rsmod.game.model.instance.InstancedMapAllocator]'s own sourced comment) or has been
 *   standing on the same tile for about a minute (100 cycles).
 * - Skull state survives reconnect via the normal attribute/timer persistence pipeline
 *   ([SKULL_ICON_DURATION_TIMER] carries a persistence key). Audit D-04: the timer does not tick
 *   while offline, and the session-local pause-tracking driver is re-armed at login
 *   ([resumeAfterLogin]) so the pause rule applies from the very first cycle back.
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
     *
     * Audit D-10: carrying a loot key also counts as skulled (OSRS Deadman: key carriers show the skull and are
     * treated as skulled), so the guards, BankSecurity, the death resolver and the teleport gate see the same
     * state the head icon already showed.
     */
    fun isSkulled(player: Player): Boolean = hasSkullTimer(player) || LootKeys.heldKeyIndexes(player).isNotEmpty()

    /**
     * Audit D-10: only the running 5-minute skull timer, without the loot-key rule of [isSkulled]. For the
     * countdown/pause bookkeeping and the HUD time, which have nothing to count for a key-only skull.
     * (Only reads [LootKeys]' public API; LootKeys never reads PvpSkull, so there is no init cycle.)
     */
    fun hasSkullTimer(player: Player): Boolean = player.timers.exists(SKULL_ICON_DURATION_TIMER)

    /** Starts (or restarts) the 5-minute skull and shows the risk-coloured icon at once. */
    private fun applySkull(player: Player) {
        player.timers[SKULL_ICON_DURATION_TIMER] = SKULL_DURATION_CYCLES
        RiskSkull.refresh(player)
        armPauseTracking(player)
    }

    /**
     * Whether [attacker] hitting [victim] is a retaliation: [victim] is the player currently on
     * record as [attacker]'s aggressor (set by [markAggression] on every hit [victim] lands on
     * [attacker], and kept alive by [PVP_AGGRESSOR_WINDOW_TIMER]).
     */
    fun isRetaliation(
        attacker: Player,
        victim: Player,
    ): Boolean = attacker.timers.has(PVP_AGGRESSOR_WINDOW_TIMER) && attacker.attr[PVP_AGGRESSOR_ATTR]?.get() === victim

    /**
     * Owner 2026-09-18 (live retest, "MAJOR"): a player is skulled only when a hitsplat actually
     * registers on the target - never at attack start. A player who could not attack at all (no
     * arrows, no runes, out of reach) was still skulled because the skull was granted from the
     * combat-start hook. This is now called from the one hit dispatch point every attack style
     * (melee, ranged, magic, special) passes through - `Pawn.dealHit` in content/combat/PawnExt.kt
     * - as a hit action, i.e. on the cycle the hitsplat is written (a 0 / blocked hit is a hitsplat
     * too: OSRS Wiki "Skull (status)": missing still skulls). A cancelled hit (target or attacker
     * dead, area no longer PvP) never skulls.
     *
     * OSRS Wiki "Skull (status)": "A player becomes skulled when they attack another player who
     * has not attacked them first" - a retaliation, whether the engine's auto-retaliate or a
     * manual Attack on the aggressor, never skulls ([isRetaliation]). Every other registered hit
     * on a player gives, or resets to, the full 5-minute skull.
     */
    fun onHitRegistered(
        attacker: Player,
        victim: Player,
    ) {
        if (attacker === victim || !AreaState.canPlayersFight(attacker, victim)) {
            return
        }
        // Deadman PvP guards plan (2026-09-16): "attacking ... ends it early". Audit D-05: EVERY attack the grace holder
        // makes ends it - a retaliation too, and the same victim again after their respawn. Only the trailing hits of the
        // attack that made the kill are exempt ([KillGrace.isTrailingHit]).
        if (!KillGrace.isTrailingHit(attacker, victim)) KillGrace.endEarly(attacker)
        if (!isRetaliation(attacker, victim)) {
            applySkull(attacker)
        }
        markAggression(attacker, victim)
    }

    /**
     * Effect-only spells (binds, Teleport Block, stat drains, ...) never write a hitsplat, but a
     * cast that actually fired at a player (runes consumed, projectile sent) is an attack in OSRS
     * and skulls exactly like a damaging hit. Called from the magic strategy at cast execution.
     */
    fun onEffectSpellCast(
        attacker: Player,
        victim: Player,
    ) = onHitRegistered(attacker, victim)

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
        // Audit D-11: remembered separately from the boss-overwritable LAST_HIT_BY, for the boss-area teleport gate.
        victim.attr[LAST_PVP_HIT_CYCLE_ATTR] = victim.world.currentCycle
    }

    /** Audit D-11: the world cycle this player was last attacked or hit by another player ([markAggression]; session-local). */
    val LAST_PVP_HIT_CYCLE_ATTR = AttributeKey<Int>()

    /** Audit D-11: cycles since [player] was last hit by a player, or null when never (this session). */
    fun cyclesSincePvpHit(player: Player): Int? = player.attr[LAST_PVP_HIT_CYCLE_ATTR]?.let { player.world.currentCycle - it }

    /**
     * Audit D-04: called at login. The skull no longer ticks offline, and this re-arms the session-local
     * pause driver at once with a fresh stall baseline, applying the instance pause immediately instead of
     * one cycle later. Also migrates a save written before D-04 (legacy `tickOffline = true` key, already
     * fast-forwarded by the deserialiser like before) onto the live key.
     */
    fun resumeAfterLogin(player: Player) {
        if (player.timers.exists(LEGACY_SKULL_ICON_DURATION_TIMER)) {
            val left = player.timers[LEGACY_SKULL_ICON_DURATION_TIMER]
            player.timers.remove(LEGACY_SKULL_ICON_DURATION_TIMER)
            if (left > 0 && !hasSkullTimer(player)) player.timers[SKULL_ICON_DURATION_TIMER] = left
        }
        if (!hasSkullTimer(player)) return
        player.attr[SKULL_STALL_TILE_ATTR] = player.tile
        player.attr[SKULL_STALL_CYCLES_ATTR] = 0
        if (player.tile.x >= 6400) player.timers.pause(SKULL_ICON_DURATION_TIMER) else player.timers.resume(SKULL_ICON_DURATION_TIMER)
        player.timers[SKULL_PAUSE_CHECK_TIMER] = 1
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
        // Audit D-10: a key-only skull has no countdown to pause.
        if (!hasSkullTimer(player)) {
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

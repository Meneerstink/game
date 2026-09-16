package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.filterableMessage

/**
 * Deadman PvP guards plan (owner-approved 2026-09-16), Batch 4: the shared 7-second countdown
 * used by logout, portal/POH entry, non-teleport transport (minecarts) and a skulled player's
 * teleports. "logging out, entering a portal (player-owned house) or using most non-teleport
 * transport (e.g. minecarts) opens a 7-second countdown in the chatbox; any action or being
 * attacked while it is open cancels it; after 7 uninterrupted seconds the action happens
 * automatically."
 *
 * Disclosed scope boundary (unhandled-route census): "any action" is wired for the two triggers
 * that matter for every use of this countdown - moving (tile change) and being attacked
 * ([Combat.postAttack] calls [cancel] on the target) - via the same per-cycle poll pattern
 * already used by [BankSecurity.monitor]/[CityGuards.onZoneCheck] (this revision has no generic
 * player-action event). Opening an interface, using an item, or other non-movement actions are
 * NOT exhaustively wired to cancellation; recorded here rather than silently claimed complete.
 */
object SevenSecondAction {
    /** 7 seconds, rounded up to the nearest 0.6s cycle - same rounding convention already used
     * elsewhere in this codebase (e.g. BankSecurity.BANK_TIMER_TICKS for "10 seconds"). */
    const val DURATION_CYCLES = 12

    val COUNTDOWN_TIMER = TimerKey(tickOffline = false, resetOnDeath = true)

    private val PENDING_ACTION_ATTR = AttributeKey<() -> Unit>()
    private val LAST_TILE_ATTR = AttributeKey<Tile>()

    /**
     * A skulled player's teleport (owner spec: "Skulled players always get this interface for
     * teleports too, even out of combat") is gated by [gg.rsmod.plugins.content.magic.canTeleport],
     * which every existing teleport entrypoint (spellbook, tabs, jewellery, portals, NPC teleports,
     * ...) already funnels through - see the class doc on that function. Disclosed simplification:
     * true "happens automatically" completion (owner spec) would need a completion-callback plumbed
     * through all ~15 call sites; instead, [canTeleport] returns false while the countdown runs and
     * sets this flag once it finishes, so the SAME action becomes an instant pass on the player's
     * next attempt (one extra click) rather than firing on its own. Recorded here, not silently
     * presented as the fully automatic behaviour the owner described.
     */
    val TELEPORT_CONFIRMED_ATTR = AttributeKey<Boolean>()

    /** Consumes (clears) the confirmation flag and reports whether it was set. */
    fun consumeTeleportConfirmation(player: Player): Boolean {
        val confirmed = player.attr[TELEPORT_CONFIRMED_ATTR] == true
        if (confirmed) {
            player.attr.remove(TELEPORT_CONFIRMED_ATTR)
        }
        return confirmed
    }

    enum class Kind(
        val verb: String,
    ) {
        LOGOUT("You will log out"),
        PORTAL("You will enter the portal"),
        TRANSPORT("You will board"),
        TELEPORT("You will teleport"),
    }

    val isActiveAttr = AttributeKey<Kind>()

    fun isActive(player: Player): Boolean = player.timers.exists(COUNTDOWN_TIMER)

    /** Starts the countdown, or is a no-op if one is already running. */
    fun start(
        player: Player,
        kind: Kind,
        onComplete: () -> Unit,
    ) {
        if (isActive(player)) {
            return
        }
        player.attr[PENDING_ACTION_ATTR] = onComplete
        player.attr[isActiveAttr] = kind
        player.attr[LAST_TILE_ATTR] = player.tile
        player.timers[COUNTDOWN_TIMER] = DURATION_CYCLES
        player.filterableMessage("${kind.verb} in 7 seconds. Any action or being attacked will cancel this.")
    }

    /** Cancels an in-progress countdown, if any. Safe/idempotent to call when none is active. */
    fun cancel(
        player: Player,
        reason: String? = null,
    ) {
        if (!isActive(player)) {
            return
        }
        player.timers.remove(COUNTDOWN_TIMER)
        player.attr.remove(PENDING_ACTION_ATTR)
        player.attr.remove(isActiveAttr)
        player.attr.remove(LAST_TILE_ATTR)
        if (reason != null) {
            player.filterableMessage(reason)
        }
    }

    /**
     * Runs and clears the pending action; called by the countdown timer's own expiry hook.
     * Explicitly clears [COUNTDOWN_TIMER] too (not just the attribute-backed state) so
     * [isActive] is correct immediately regardless of whether the real per-cycle timer engine
     * has also processed this key's own removeOnZero cleanup yet.
     */
    fun complete(player: Player) {
        val action = player.attr[PENDING_ACTION_ATTR]
        player.timers.remove(COUNTDOWN_TIMER)
        player.attr.remove(PENDING_ACTION_ATTR)
        player.attr.remove(isActiveAttr)
        player.attr.remove(LAST_TILE_ATTR)
        action?.invoke()
    }

    /** Movement-interruption poll, called every cycle from the same existing per-cycle timer
     * [bank_entry_guard.plugin.kts] already polls (this revision has no generic player-step
     * event). Cancels the moment the player's tile changes while a countdown is active. */
    fun onZoneCheck(player: Player) {
        if (!isActive(player)) {
            return
        }
        val lastTile = player.attr[LAST_TILE_ATTR]
        if (lastTile != null && lastTile != player.tile) {
            cancel(player, "Your action was cancelled.")
        }
    }
}

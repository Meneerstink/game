package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.closeComponent
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * Deadman 7-second timer interface (OSRS Wiki "Deadman Mode", owner instruction 2026-09-16):
 * "attempting to log out, enter a portal (Player-owned house), or use most forms of non-teleport
 * transportation such as minecarts will bring up a 7 second timer interface that will begin
 * counting down in the chatbox. While this interface is active, the player must not perform any
 * further actions or be attacked. After the timer interface stays open without interruption for 7
 * seconds, the player will automatically perform the requested action." Skulled players get the
 * same interface for every teleport ([gg.rsmod.plugins.content.magic.canTeleport]).
 *
 * The 667 cache has no Deadman-specific widget, so the countdown is shown in the chatbox on the
 * single-line message interface 210 (the same chatbox slot every dialogue uses, so it works in
 * fixed, resizable and fullscreen alike). The text is refreshed every cycle with the seconds left.
 *
 * Interruption: moving (tile change), being attacked ([gg.rsmod.plugins.content.combat.Combat.postAttack])
 * and any action that closes or replaces the chatbox interface (clicking it away, opening another
 * dialogue, a shop, the bank, ...) cancel the countdown; the pending action is dropped.
 */
object SevenSecondAction {
    /** 7 seconds, rounded up to the nearest 0.6s cycle. */
    const val DURATION_CYCLES = 12

    const val CHATBOX_INTERFACE = 210
    const val CHATBOX_TEXT_COMPONENT = 1

    /** Interface 210's baked "Click here to continue" line (cache layout probe 2026-09-17: component 2,
     * the pause button with onMouseOver/onMouseLeave hooks). Owner 2026-09-17: it must not show on
     * the countdown, so it is hidden every time the countdown interface opens. */
    const val CHATBOX_CONTINUE_COMPONENT = 2

    val COUNTDOWN_TIMER = TimerKey(tickOffline = false, resetOnDeath = true)

    private val PENDING_ACTION_ATTR = AttributeKey<() -> Unit>()
    private val LAST_TILE_ATTR = AttributeKey<Tile>()
    private val LAST_SHOWN_SECONDS_ATTR = AttributeKey<Int>()

    /**
     * Legacy one-click confirmation flag for the single-argument
     * [gg.rsmod.plugins.content.magic.canTeleport] overload; every teleport entrypoint now passes
     * its action to the two-argument overload, so the automatic completion path is the normal one.
     */
    val TELEPORT_CONFIRMED_ATTR = AttributeKey<Boolean>()

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
        LOGOUT("Logging out"),
        PORTAL("Entering the portal"),
        TRANSPORT("Boarding"),
        TELEPORT("Teleporting"),
    }

    val isActiveAttr = AttributeKey<Kind>()

    fun isActive(player: Player): Boolean = player.timers.exists(COUNTDOWN_TIMER)

    /** Whole seconds for a cycle count (12 cycles = 7.2 s -> 7; 1 cycle = 0.6 s -> 1). */
    fun secondsFor(cycles: Int): Int = Math.round(cycles * 0.6).toInt().coerceAtLeast(1)

    fun secondsLeft(player: Player): Int = if (isActive(player)) secondsFor(player.timers[COUNTDOWN_TIMER]) else 0

    fun countdownText(
        kind: Kind,
        seconds: Int,
    ): String = "${kind.verb} in $seconds second${if (seconds == 1) "" else "s"}..."

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
        showInterface(player, kind, DURATION_CYCLES)
        player.filterableMessage("Any action or being attacked will cancel this.")
    }

    private fun showInterface(
        player: Player,
        kind: Kind,
        cyclesLeft: Int,
    ) {
        val seconds = secondsFor(cyclesLeft)
        if (player.attr[LAST_SHOWN_SECONDS_ATTR] == seconds) return
        if (player.attr[LAST_SHOWN_SECONDS_ATTR] == null) {
            player.openInterface(interfaceId = CHATBOX_INTERFACE, parent = 752, child = 13)
            player.setComponentHidden(CHATBOX_INTERFACE, CHATBOX_CONTINUE_COMPONENT, hidden = true)
        }
        player.attr[LAST_SHOWN_SECONDS_ATTR] = seconds
        player.setComponentText(CHATBOX_INTERFACE, CHATBOX_TEXT_COMPONENT, countdownText(kind, seconds))
    }

    private fun hideInterface(player: Player) {
        if (player.attr.has(LAST_SHOWN_SECONDS_ATTR) && player.interfaces.isVisible(CHATBOX_INTERFACE)) {
            player.closeComponent(parent = 752, child = 13)
        }
        if (player.attr.has(LAST_SHOWN_SECONDS_ATTR)) {
            // Give ordinary message boxes their "Click here to continue" line back.
            player.setComponentHidden(CHATBOX_INTERFACE, CHATBOX_CONTINUE_COMPONENT, hidden = false)
        }
        player.attr.remove(LAST_SHOWN_SECONDS_ATTR)
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
        hideInterface(player)
        if (reason != null) {
            player.filterableMessage(reason)
        }
    }

    /** Runs and clears the pending action; called by the countdown timer's own expiry hook. */
    fun complete(player: Player) {
        val action = player.attr[PENDING_ACTION_ATTR]
        player.timers.remove(COUNTDOWN_TIMER)
        player.attr.remove(PENDING_ACTION_ATTR)
        player.attr.remove(isActiveAttr)
        player.attr.remove(LAST_TILE_ATTR)
        hideInterface(player)
        action?.invoke()
    }

    /**
     * Per-cycle poll (from the shared zone monitor): cancels the moment the player moves or the
     * chatbox interface is no longer the countdown (closed or replaced by another action), and
     * otherwise refreshes the seconds shown.
     */
    fun onZoneCheck(player: Player) {
        if (!isActive(player)) {
            return
        }
        val lastTile = player.attr[LAST_TILE_ATTR]
        if (lastTile != null && lastTile != player.tile) {
            cancel(player, "Your action was cancelled.")
            return
        }
        if (player.attr.has(LAST_SHOWN_SECONDS_ATTR) && !player.interfaces.isVisible(CHATBOX_INTERFACE)) {
            player.attr.remove(LAST_SHOWN_SECONDS_ATTR)
            cancel(player, "Your action was cancelled.")
            return
        }
        val kind = player.attr[isActiveAttr] ?: return
        showInterface(player, kind, player.timers[COUNTDOWN_TIMER])
    }
}

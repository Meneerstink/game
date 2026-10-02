package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.isBeingAttacked
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleportRefusal

/**
 * The one gate for everything a player reaches without walking to it: a typed command, an item option that opens a
 * shop, a minigame that later moves the players who signed up for it (ROOT_CAUSES class 21: escape restrictions belong
 * to the shared route boundary, never to each destination).
 *
 * Bug search 2026-10-02 - every player could:
 *  - type `dungeon` deep in the Wilderness, Tele Blocked and mid-fight, and land in Daemonheim at once (a raw
 *    `teleportTo`, no gate at all);
 *  - be moved out of any death zone by `duel` + `duelaccept`, Pest Control's game start, Practice PvP's match start or
 *    `clanwarenter` - all raw `moveTo`/`teleportTo`;
 *  - open the Deadman Store (Torture, Anguish, blowpipe ...) and the casino from the middle of a fight (commands and the
 *    Deadman's skull "Shop" option).
 *
 * The rules, all from rules this server already had:
 *  - [refusal] - the owner's Grand Exchange command rule (safe zone, out of combat, not dying or locked) for any remote
 *    service;
 *  - [travel] - a typed command that moves the player: [refusal], then the full teleport gate ([canTeleport]: Tele Block,
 *    Wilderness level, activity locks and the Deadman 7-second countdown), re-checked when the countdown completes;
 *  - [moveRefusal] / [escapeRefusal] - a move the server makes without a prompt: the teleport rules must hold *now* and the
 *    Deadman decision must be instant (not skulled, not hit in the last 7 seconds) - nobody is pulled out of a fight.
 */
object RemoteAccess {
    const val NOT_NOW = "You can't do that right now."

    /** Why [player] may not use a remote service right now, or null. [what] completes the sentence ("use the store"). */
    fun refusal(
        player: Player,
        what: String = "do that",
    ): String? =
        when {
            player.attr[DEATH_FLAG] == true || player.isDead() || player.isLocked() -> NOT_NOW
            AreaState.isDangerous(player.zoneTile()) -> "You can only $what from a safe zone."
            player.isBeingAttacked() -> "You can't $what while in combat."
            else -> null
        }

    /** [refusal], told to the player. True when refused. */
    fun refuse(
        player: Player,
        what: String = "do that",
    ): Boolean {
        val reason = refusal(player, what) ?: return false
        player.message(reason)
        return true
    }

    /**
     * A command that moves the player who typed it. [action] runs now, or when a skulled player's Deadman countdown
     * completes uninterrupted - and only if every rule still holds then.
     */
    fun travel(
        player: Player,
        what: String = "travel there",
        action: () -> Unit,
    ): Boolean {
        if (refuse(player, what)) return false
        return player.canTeleport(TeleportType.MODERN) {
            if (!refuse(player, what)) action()
        }
    }

    /** A server-made move of a player who signed up from a safe zone earlier (a minigame starting). */
    fun moveRefusal(
        player: Player,
        what: String = "be taken there",
    ): String? = refusal(player, what) ?: escapeRefusal(player)

    /**
     * The teleport rules alone, for a move that starts in a death zone by design (the Duel Arena lobby): Tele Block, the
     * Wilderness level, activity locks, and the Deadman decision - a skulled or recently hit player is never moved.
     */
    fun escapeRefusal(player: Player): String? {
        player.teleportRefusal(TeleportType.MODERN)?.let { return it.firstOrNull() ?: NOT_NOW }
        return when (DeadmanTimerGate.teleportDecision(player)) {
            DeadmanTimerGate.Teleport.INSTANT -> null
            DeadmanTimerGate.Teleport.COUNTDOWN -> "You can't do that while you are skulled."
            DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT -> DeadmanTimerGate.blockedMessage(player)
        }
    }
}

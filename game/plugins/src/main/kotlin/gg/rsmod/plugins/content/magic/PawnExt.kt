package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/**
 * Shared teleport restrictions. The 2011 ten-second combat wait belongs to Home Teleport
 * (Novite HomeTeleport.process), not to ordinary teleports.
 *
 * Compatibility check for tests and callers that only need a yes/no result. Production routes
 * use the callback overload so delayed teleports complete automatically. Delayed approval is
 * deliberately not cached: a reusable confirmation flag could survive a new hit or Tele Block
 * and become an escape bypass on the next click.
 */
fun Player.canTeleport(type: TeleportType): Boolean {
    return canTeleport(type) {}
}

/**
 * [onConfirmed] runs automatically once a skulled player's 7-second countdown finishes
 * uninterrupted - call sites that pass their whole post-check teleport action here get true
 * automatic completion instead of the legacy "click again" fallback the one-arg overload uses.
 * Returns false (and starts/continues the countdown) for as long as the countdown is running;
 * [onConfirmed] is invoked directly by [SevenSecondAction], not by this function's return value,
 * so a caller must not also run its teleport logic itself when this returns false.
 */
fun Player.canTeleport(
    type: TeleportType,
    onConfirmed: () -> Unit,
): Boolean {
    if (!validateTeleportRestrictions(type)) {
        return false
    }

    // Owner 2026-09-17 (supersedes the 2026-09-16 "blocked with a message when hit in the last 7
    // seconds" rule and, before that, RCV-005): the Deadman 7-second countdown interface opens
    // only when the player is PK-skulled or in combat with a player or a non-boss npc
    // (DeadmanTimerGate); a fight with any boss never delays a teleport. Otherwise the teleport is
    // instant. The deferred callback revalidates every server-side restriction at completion so a
    // same-tick Tele Block, movement into deeper Wilderness, activity lock, or death cannot race it.
    when (DeadmanTimerGate.teleportDecision(this)) {
        DeadmanTimerGate.Teleport.COUNTDOWN -> {
            if (!SevenSecondAction.isActive(this)) {
                SevenSecondAction.start(this, SevenSecondAction.Kind.TELEPORT) {
                    if (validateTeleportRestrictions(type)) {
                        onConfirmed()
                    }
                }
            }
            return false
        }
        DeadmanTimerGate.Teleport.BLOCKED_IN_COMBAT -> {
            message(DeadmanTimerGate.blockedMessage(this))
            return false
        }
        DeadmanTimerGate.Teleport.INSTANT -> {}
    }

    onConfirmed()
    return true
}

/** Restrictions that must hold both when a teleport is requested and when a delayed one fires. */
private fun Player.validateTeleportRestrictions(type: TeleportType): Boolean {
    val currWildLvl = tile.getWildernessLevel()
    val wildLvlRestriction = type.wildLvlRestriction
    val randomEvent = tile.regionId

    if (isDead() || !lock.canTeleport()) {
        return false
    }

    if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
            this, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.TELEPORT,
        )
    ) {
        return false
    }

    if (timers.has(TELEBLOCK_TIMER)) {
        message("A magical force has stopped you from teleporting.")
        return false
    }

    if (currWildLvl > wildLvlRestriction) {
        message("A mysterious force blocks your teleport spell!")
        message("You can't use this teleport after level $wildLvlRestriction wilderness.")
        return false
    }

    if (randomEvent == 12619) {
        message("You can't use this teleport here.")
        return false
    }

    return true
}

fun Pawn.prepareForTeleport() {
    stopMovement()
    resetInteractions()
    clearHits()
}

/**
 * @param startSound false when the caller already played the start sound (a spell's own cast sound).
 */
fun Pawn.teleport(
    endTile: Tile,
    type: TeleportType,
    startSound: Boolean = true,
) {
    lock = LockState.FULL_WITH_DAMAGE_IMMUNITY

    queue(TaskPriority.STRONG) {
        prepareForTeleport()

        if (startSound) type.startSound?.let { (this@teleport as? Player)?.playSound(it) }
        animate(type.animation)
        type.graphic?.let {
            graphic(it)
        }

        wait(type.teleportDelay)

        teleportTo(endTile)
        type.landSound?.let { (this@teleport as? Player)?.playSound(it) }

        type.endAnimation?.let {
            animate(it)
        }

        type.endGraphic?.let {
            graphic(it)
        }

        type.endAnimation?.let {
            val def = world.definitions.get(AnimDef::class.java, it)
            wait(def.cycleLength)
        }

        animate(Anims.RESET)
        unlock()
    }
}

package gg.rsmod.plugins.content.mechanics.freeze

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.FREEZE_IMMUNITY_TICKS
import java.lang.ref.WeakReference

/**
 * PvP freeze range. OSRS Wiki "Freeze": "all freeze effects applied to other players will only restrict movement whilst both
 * players remain within the 10-tile maximum player weapon range". Before this a frozen player stayed rooted for the full 32 ticks
 * of a barrage while the caster ran or teleported away (combat run 2026-09-29).
 *
 * A player-on-player freeze remembers its caster and re-checks the distance every tick. Once the caster is out of the 10-tile
 * square, on another plane or logged out, the freeze ends and the normal 5-tick refreeze immunity starts from that tick.
 * Freezes by npcs (and on npcs) are not leashed.
 */
object FreezeLeash {
    const val MAX_DISTANCE = 10

    val SOURCE_ATTR = AttributeKey<WeakReference<Player>>(resetOnDeath = true)

    /** One-tick re-arming check while a leashed freeze is active. */
    val CHECK_TIMER = TimerKey(resetOnDeath = true)

    fun start(
        target: Pawn,
        source: Pawn?,
    ) {
        if (target !is Player || source !is Player || source === target) {
            stop(target)
            return
        }
        target.attr[SOURCE_ATTR] = WeakReference(source)
        target.timers[CHECK_TIMER] = 1
    }

    fun inRange(
        target: Player,
        source: Player?,
    ): Boolean = source != null && source.isOnline && source.tile.isWithinRadius(target.tile, MAX_DISTANCE)

    /** Runs every tick of a leashed freeze. @return true when this tick broke the freeze. */
    fun check(target: Player): Boolean {
        if (!target.timers.has(FROZEN_TIMER)) {
            stop(target)
            return false
        }
        val source = target.attr[SOURCE_ATTR]?.get()
        if (inRange(target, source)) {
            target.timers[CHECK_TIMER] = 1
            return false
        }
        target.timers.remove(FROZEN_TIMER)
        target.timers[FREEZE_IMMUNITY_TIMER] = FREEZE_IMMUNITY_TICKS
        stop(target)
        gg.rsmod.game.model.BreachTrace.log("freeze.leash_break") {
            "player=${target.username} caster=${source?.username ?: "gone"} casterTile=${source?.tile} tile=${target.tile}"
        }
        return true
    }

    fun stop(target: Pawn) {
        target.attr.remove(SOURCE_ATTR)
        target.timers.remove(CHECK_TIMER)
    }
}

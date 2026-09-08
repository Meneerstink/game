import gg.rsmod.game.model.attr.ANTI_CHEAT_EVENT_ACTIVE
import gg.rsmod.game.model.attr.LAST_KNOWN_POSITION
import gg.rsmod.game.model.timer.ANTI_CHEAT_TIMER
import gg.rsmod.game.model.timer.LOGOUT_TIMER

/**
 * Random events are DISABLED on this server.
 *
 * 2026-09-06, owner-reported and confirmed still live: "Random Events are still active. The player
 * is still forcibly kidnapped/teleported." This file used to be the scheduler that did it - a
 * three-hour `ANTI_CHEAT_TIMER` that, on expiry, spawned Sergeant Damien, locked the player, and
 * teleported them into the Drill Demon instance at 3163,4821 with a 500-tick forced-logout timer
 * attached. That whole trigger is removed rather than merely made unlikely.
 *
 * Three things are needed, not one, because [ANTI_CHEAT_TIMER] is a **persisted** timer
 * (`persistenceKey = "anti_cheat"`): simply not scheduling it would leave every already-saved
 * account carrying a live countdown that still fires.
 *
 *  1. Every login clears the persisted timer and the stale in-event flag.
 *  2. The timer handler is kept, but only to defuse a timer that arrives from anywhere else
 *     (an old save loaded before this change, `Player.addXp`'s countdown accelerator, a future
 *     re-enable) - it never starts an event.
 *  3. A player already stranded inside the Drill Demon region when this lands is sent back, so
 *     the fix does not require them to relog out of a room they cannot leave.
 *
 * The Drill Demon content itself (`drill_demon.plugin.kts`) is deliberately left in place: its
 * dialogue, exercise mats and reward handling are unreachable with no trigger, and deleting
 * working content is not the same as disabling the forced event.
 */

/** The Drill Demon instance. A player logging in here got there through the removed event. */
val drillDemonRegion = 12619

on_login {
    player.timers.remove(ANTI_CHEAT_TIMER)
    player.timers.remove(LOGOUT_TIMER)
    if (player.attr[ANTI_CHEAT_EVENT_ACTIVE] == true) {
        player.attr[ANTI_CHEAT_EVENT_ACTIVE] = false
    }
    if (player.tile.regionId == drillDemonRegion) {
        val lastKnownPosition = player.attr[LAST_KNOWN_POSITION]
        player.moveTo(lastKnownPosition ?: world.gameContext.home)
        player.attr.remove(LAST_KNOWN_POSITION)
    }
}

/**
 * Defused. Anything that still manages to arm the timer is cancelled here instead of seizing the
 * player; this is the single choke point every forced random event went through.
 */
on_timer(ANTI_CHEAT_TIMER) {
    player.timers.remove(ANTI_CHEAT_TIMER)
    player.attr[ANTI_CHEAT_EVENT_ACTIVE] = false
}

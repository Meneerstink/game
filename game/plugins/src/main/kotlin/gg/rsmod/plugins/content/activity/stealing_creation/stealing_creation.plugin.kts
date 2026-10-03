package gg.rsmod.plugins.content.activity.stealing_creation

import gg.rsmod.plugins.content.skills.agility.climbOver
import gg.rsmod.plugins.content.skills.agility.obstacle

/**
 * Hook wiring for Q-053 Stealing Creation (lobby vertical slice only). See
 * StealingCreationLobbyData.kt for the full sourcing note and the disclosed instance-manager
 * limit on the actual match/arena.
 */

// Novite StealingCreationLobbyController.climOverStile: anim 1560 and a forced move over the stile, lock 3. Each stile is a 2x1
// loc across the pen fence (clip 2026-10-03: 39508 at 2965-2966,9703, 39509 at 2965-2966,9696, fence 39728 between x 2965 and 2966),
// the pen on its west end. The climb is the shared climbOver (the long-axis lane from the tile in front to the tile behind), so
// the player no longer jumps straight into the pen without a climb (sweep 2026-10-03: moveTo 2967,9703 -> 2965,9704 in one tick).
// Which way is decided by the side the player stands on, not by the queue: a player left in a pen after a queue reset climbs out.
fun stile(
    plugin: Plugin,
    joinRed: Boolean,
) {
    val player = plugin.player
    val stile = player.getInteractingGameObj()
    val inPen = player.tile.x <= stile.tile.x
    if (!inPen && !StealingCreationLobbyHandler.mayEnter(player, joinRed)) return
    plugin.obstacle { target ->
        if (!climbOver(player, target, anim = 1560)) return@obstacle
        if (inPen) StealingCreationLobbyHandler.leave(player, move = false) else StealingCreationLobbyHandler.enter(player, joinRed)
    }
}

on_obj_option(obj = StealingCreationLobbyData.BLUE_STILE, option = "Climb-over") { stile(this, joinRed = false) }

on_obj_option(obj = StealingCreationLobbyData.RED_STILE, option = "Climb-over") { stile(this, joinRed = true) }

world.timers[ScLobbyTick] = StealingCreationLobbyData.LOBBY_TICK_INTERVAL
on_timer(ScLobbyTick) {
    StealingCreationLobbyHandler.tick()
    world.timers[ScLobbyTick] = StealingCreationLobbyData.LOBBY_TICK_INTERVAL
}

// The two east stiles 49916 (2970,9696 / 2970,9703, rot 1) mirror the team stiles across the lobby; no donor or wiki gives them a
// team, so they are plain stile crossings (shared climbOver, the stile's own lane) - never a dead click (rest batch 12, 2026-10-03).
on_obj_option(obj = 49916, option = "Climb-over") {
    obstacle { target -> climbOver(player, target) }
}

// Entry portal 39515 (2967,9711): the way into the match arena, which this server does not have (see StealingCreationLobbyData);
// it answers with the lobby's own message instead of doing nothing.
on_obj_option(obj = 39515, option = "Enter") {
    player.message("Stealing Creation's arena isn't available on this server yet.")
}

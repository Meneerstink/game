package gg.rsmod.plugins.content.activity.stealing_creation

/**
 * Hook wiring for Q-053 Stealing Creation (lobby vertical slice only). See
 * StealingCreationLobbyData.kt for the full sourcing note and the disclosed instance-manager
 * limit on the actual match/arena.
 */

on_obj_option(obj = StealingCreationLobbyData.BLUE_STILE, option = "Climb-over") {
    StealingCreationLobbyHandler.join(player, joinRed = false)
}

on_obj_option(obj = StealingCreationLobbyData.RED_STILE, option = "Climb-over") {
    StealingCreationLobbyHandler.join(player, joinRed = true)
}

world.timers[ScLobbyTick] = StealingCreationLobbyData.LOBBY_TICK_INTERVAL
on_timer(ScLobbyTick) {
    StealingCreationLobbyHandler.tick()
    world.timers[ScLobbyTick] = StealingCreationLobbyData.LOBBY_TICK_INTERVAL
}

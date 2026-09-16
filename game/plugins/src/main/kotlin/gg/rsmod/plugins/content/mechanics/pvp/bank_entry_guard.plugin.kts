package gg.rsmod.plugins.content.mechanics.pvp

/** Per-cycle Deadman zone poll (this revision has no generic player-step event). */
val ZONE_MONITOR = TimerKey()

on_login {
    player.timers[ZONE_MONITOR] = 1
}

on_timer(ZONE_MONITOR) {
    // Reactive guard pair + Wizguard for a skulled player inside a guarded city, and their
    // despawn the moment the player leaves or unskulls.
    CityGuards.onZoneCheck(player)
    // Movement / interface-close interruption of an in-progress 7-second countdown.
    SevenSecondAction.onZoneCheck(player)
    player.timers[ZONE_MONITOR] = 1
}

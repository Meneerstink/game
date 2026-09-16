package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud

/**
 * Per-cycle driver of the Deadman zone/skull HUD ([DeadmanHud]) - Wilderness level inside the
 * Wilderness, "Guarded"/"Dangerous" everywhere else, and the PK-skull countdown. The overlay is
 * server-authoritative: the client never derives danger or a level from raw coordinates.
 */
val wildernessCheckTimer = TimerKey()

on_login {
    DeadmanHud.reset(player)
    player.timers[wildernessCheckTimer] = 1
}

on_timer(wildernessCheckTimer) {
    DeadmanHud.refresh(player)
    player.timers[wildernessCheckTimer] = 1
}

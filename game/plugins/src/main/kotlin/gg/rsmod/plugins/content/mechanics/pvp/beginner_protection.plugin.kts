package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR

/**
 * R14.24: drives [BeginnerProtection.tickAfkGuard] once per cycle for every online player,
 * same repeating-timer pattern `wilderness.plugin.kts` uses for its danger check.
 */
val protectionAfkTimer = TimerKey()

on_login {
    player.attr[LAST_ACTIVE_CYCLE_ATTR] = world.currentCycle
    player.timers[protectionAfkTimer] = 1
}

on_timer(protectionAfkTimer) {
    BeginnerProtection.tickAfkGuard(player, world)
    player.timers[protectionAfkTimer] = 1
}

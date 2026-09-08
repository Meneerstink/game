package gg.rsmod.plugins.content.mechanics.pvp

/**
 * Drives [RiskSkull.refresh] once per cycle for every online player - the same repeating-timer
 * idiom `beginner_protection.plugin.kts`'s AFK guard and `wilderness.plugin.kts`'s danger check
 * already use for periodic per-player upkeep, reused here rather than inventing a new
 * inventory-changed event to hook every pickup/drop/equip point in the codebase directly.
 */
val riskSkullTimer = TimerKey()

on_login {
    player.timers[riskSkullTimer] = 1
}

on_timer(riskSkullTimer) {
    RiskSkull.refresh(player)
    player.timers[riskSkullTimer] = 1
}

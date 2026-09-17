package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon

/**
 * The skull timer ran out: the skull STATE is the timer itself ([PvpSkull.isSkulled]), so drop the
 * key first (the timer is still present, at 0, while its expiry hook runs) and then let the risk
 * refresh derive the head icon again - no skull unless loot keys are carried.
 */
on_timer(SKULL_ICON_DURATION_TIMER) {
    player.timers.remove(SKULL_ICON_DURATION_TIMER)
    RiskSkull.refresh(player)
}

on_timer(PVP_AGGRESSOR_WINDOW_TIMER) {
    player.attr.remove(PVP_AGGRESSOR_ATTR)
}

on_timer(PvpSkull.SKULL_PAUSE_CHECK_TIMER) {
    PvpSkull.tickPauseTracking(player)
}

/**
 * Deadman PvP guards plan (2026-09-16): the pause/HUD driver is session-local (not persisted),
 * so a player who reconnects with a still-active persisted skull (restored/fast-forwarded by the
 * normal SKULL_ICON_DURATION_TIMER persistence pipeline) needs it re-armed here, otherwise the
 * countdown would tick down unprotected by the pause rule and the HUD refresh would never fire
 * again until the next fresh attack.
 */
on_login {
    if (PvpSkull.isSkulled(player)) {
        player.timers[PvpSkull.SKULL_PAUSE_CHECK_TIMER] = 1
    }
}

/**
 * Owner 2026-09-17: "Als een player doodgaat moet de skull volledig verdwijnen." PlayerDeathAction
 * only removes the SKULL_ICON_DURATION_TIMER (resetOnDeath) - silently, without its expiry hook -
 * so the head icon itself stayed red after a death. Clear it here, at the end of the death
 * lifecycle, and drop the loot-key count with it (the keys are lost on death); the guards let go
 * of the now unskulled player on the next zone poll.
 */
on_player_death {
    player.timers.remove(SKULL_ICON_DURATION_TIMER)
    player.setSkullIcon(SkullIcon.NONE)
    player.lootKeyIcons = 0
    CityGuards.release(player)
}

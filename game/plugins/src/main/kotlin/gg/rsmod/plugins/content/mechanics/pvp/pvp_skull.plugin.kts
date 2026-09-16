package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon

on_timer(SKULL_ICON_DURATION_TIMER) {
    player.setSkullIcon(SkullIcon.NONE)
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
    if (player.hasSkullIcon(SkullIcon.RED) && player.timers.exists(SKULL_ICON_DURATION_TIMER)) {
        player.timers[PvpSkull.SKULL_PAUSE_CHECK_TIMER] = 1
    }
}

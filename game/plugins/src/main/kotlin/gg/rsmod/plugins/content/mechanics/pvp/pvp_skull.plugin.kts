package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon

on_timer(SKULL_ICON_DURATION_TIMER) {
    player.setSkullIcon(SkullIcon.NONE)
}

on_timer(PVP_AGGRESSOR_WINDOW_TIMER) {
    player.attr.remove(PVP_AGGRESSOR_ATTR)
}

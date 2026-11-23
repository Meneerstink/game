package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.plugins.content.skills.farming.constants.Constants.playerFarmingTimer
import gg.rsmod.plugins.content.skills.farming.constants.Constants.worldFarmingTimer
import gg.rsmod.plugins.content.skills.farming.core.PlayerManager
import gg.rsmod.plugins.content.skills.farming.core.WorldFarmingManager

on_login {
    PlayerManager.onLogin(player)
}

on_world_init {
    WorldFarmingManager.onWorldInit(world)
}

on_timer(playerFarmingTimer) {
    PlayerManager.onFarmingTick(player)
}

// Owner 23-11 S62 Nature: one extra farming cycle per five minutes, separated from the normal cycle.
on_timer(PlayerManager.natureTimer) {
    PlayerManager.onNatureStep(player)
}

on_timer(worldFarmingTimer) {
    WorldFarmingManager.onFarmingTick(world)
}

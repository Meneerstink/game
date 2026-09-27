package gg.rsmod.plugins.content.mechanics.multi

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.plugins.api.ext.isMulti

val MULTIWAY_VARC = 616

load_service(MultiService())

// Owner decision 2026-09-27: the surface uses the tile-exact OSRS Deadman: Annihilation multicombat map (MultiCombatZones);
// multi.yml keeps covering dungeons and instances.
val zoneTiles = MultiCombatZones.load()
logger.info("Multicombat: {} surface tiles from the Deadman: Annihilation map ({}).", zoneTiles, MultiCombatZones.PATH)

/*
 * The crossed-swords icon. It used to be set by on_enter/on_exit region/chunk hooks of multi.yml; the surface zones are now
 * tile-exact, so every player's icon is checked each tick instead (one set lookup) and only sent when it changes. R03.4 (the
 * icon must also be right at login / reconnect) is kept by the first check right after login.
 */
val MULTI_ICON_TIMER = TimerKey()
val MULTI_ICON_STATE = AttributeKey<Boolean>()

fun refreshIcon(player: Player) {
    val multi = player.tile.isMulti(world)
    if (player.attr[MULTI_ICON_STATE] != multi) {
        player.attr[MULTI_ICON_STATE] = multi
        player.setVarc(MULTIWAY_VARC, if (multi) 1 else 0)
    }
}

on_login {
    player.attr.remove(MULTI_ICON_STATE)
    refreshIcon(player)
    player.timers[MULTI_ICON_TIMER] = 1
}

on_timer(MULTI_ICON_TIMER) {
    refreshIcon(player)
    player.timers[MULTI_ICON_TIMER] = 1
}

on_world_init {
    world.getService(MultiService::class.java)!!.let { service ->
        service.multiRegions.forEach { region -> set_multi_combat_region(region) }
        service.multiChunks.forEach { chunk -> set_multi_combat_chunk(chunk) }
    }
}

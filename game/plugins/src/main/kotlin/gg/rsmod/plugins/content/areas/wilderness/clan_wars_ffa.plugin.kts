package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.content.mechanics.death.SafeDeath

/**
 * Clan Wars free-for-all portals (2011): the white portal leads to the safe arena (items kept
 * on death), the red portal to the dangerous arena (normal death). Both require combat level 30,
 * show the warning screen (interface 793, "don't ask again" varbits 5294/5295, portal type
 * varbit 5279), display the Clan Wars overlay (789) inside, and respawn outside on death.
 *
 * Ported from the Void donor ClanWarsFreeForAll and clan_wars/warning data (634 ids = 667).
 */
val OVERLAY = 789
val WARNING_INTERFACE = 793
val PORTAL_TYPE_VARBIT = 5279
val SAFE_WARNING_VARBIT = 5294
val DANGEROUS_WARNING_VARBIT = 5295

val OUTSIDE_X = 3266..3270
val OUTSIDE_Z = 3679..3682
val SAFE_ARENA = Tile(2815, 5511, 0)
val DANGEROUS_ARENA = Tile(3007, 5511, 0)

val FFA_X = 2740..3090
val FFA_Z = 5490..5640
val SAFE_ARENA_X = 2756..2878
val SAFE_ARENA_Z = 5512..5630
val DANGEROUS_ARENA_X = 2948..3071
val DANGEROUS_ARENA_Z = 5512..5631

val IN_FFA = AttributeKey<Boolean>()
val FFA_TIMER = TimerKey()

fun inFfa(tile: Tile) = tile.height == 0 && tile.x in FFA_X && tile.z in FFA_Z
fun inSafeArena(tile: Tile) = tile.height == 0 && tile.x in SAFE_ARENA_X && tile.z in SAFE_ARENA_Z
fun inDangerousArena(tile: Tile) = tile.height == 0 && tile.x in DANGEROUS_ARENA_X && tile.z in DANGEROUS_ARENA_Z

on_world_init {
    SafeDeath.register { inSafeArena(it.tile) }


}

fun enterPortal(player: Player, dangerous: Boolean) {
    if (player.combatLevel < 30) {
        player.message("You need a combat level of at least 30 to enter this portal.")
        return
    }
    player.setVarbit(PORTAL_TYPE_VARBIT, if (dangerous) 1 else 0)
    val warningVarbit = if (dangerous) DANGEROUS_WARNING_VARBIT else SAFE_WARNING_VARBIT
    if (player.getVarbit(warningVarbit) == 1) {
        player.moveTo(if (dangerous) DANGEROUS_ARENA else SAFE_ARENA)
        return
    }
    player.openInterface(interfaceId = WARNING_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
}

on_obj_option(obj = Objs.FREEFORALL_SAFE, option = "enter") { enterPortal(player, dangerous = false) }
on_obj_option(obj = Objs.FREEFORALL_DANGEROUS, option = "enter") { enterPortal(player, dangerous = true) }

on_button(interfaceId = WARNING_INTERFACE, component = 15) {
    player.closeInterface(WARNING_INTERFACE)
    player.moveTo(if (player.getVarbit(PORTAL_TYPE_VARBIT) == 1) DANGEROUS_ARENA else SAFE_ARENA)
}

on_button(interfaceId = WARNING_INTERFACE, component = 14) {
    player.closeInterface(WARNING_INTERFACE)
}

on_button(interfaceId = WARNING_INTERFACE, component = 9) {
    val varbit = if (player.getVarbit(PORTAL_TYPE_VARBIT) == 1) DANGEROUS_WARNING_VARBIT else SAFE_WARNING_VARBIT
    player.setVarbit(varbit, if (player.getVarbit(varbit) == 1) 0 else 1)
}

on_obj_option(obj = Objs.PORTAL_38700, option = "leave") {
    val dangerous = player.getVarbit(PORTAL_TYPE_VARBIT) == 1
    player.moveTo(Tile(world.random(OUTSIDE_X), world.random(OUTSIDE_Z), 0))
    player.filterableMessage("You have left the Clan Wars Free-For-All (${if (dangerous) "Dangerous" else "Safe"}).")
}

on_obj_option(obj = Objs.PORTAL_28213, option = "enter") {
    player.message("The clan challenge portal only opens once two clans have agreed a war.")
}

on_login {
    player.timers[FFA_TIMER] = 1
}

on_timer(FFA_TIMER) {
    val inside = inFfa(player.tile)
    val was = player.attr[IN_FFA] ?: false
    if (inside && !was) {
        player.attr[IN_FFA] = true
        player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = OVERLAY)
    } else if (!inside && was) {
        player.attr[IN_FFA] = false
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
    }
    player.timers[FFA_TIMER] = 1
}

on_player_death {
    if (inFfa(player.tile) || player.attr[IN_FFA] == true) {
        player.moveTo(Tile(world.random(OUTSIDE_X), world.random(OUTSIDE_Z), 0))
    }
}

/* Multi-combat halves of both arenas (north of z 5571). */
for (x in 2756..2878 step 8) {
    for (z in 5571..5630 step 8) {
        set_multi_combat_chunk(gg.rsmod.game.model.region.ChunkCoords(x shr 3, z shr 3).hashCode())
    }
}
for (x in 2948..3071 step 8) {
    for (z in 5571..5631 step 8) {
        set_multi_combat_chunk(gg.rsmod.game.model.region.ChunkCoords(x shr 3, z shr 3).hashCode())
    }
}

package gg.rsmod.plugins.content.areas.voidoutpost

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.content.areas.portsarim.CharterType
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/*
 * Port Sarim <-> Void Knights' Outpost boat. Both squires offer "Travel" in the 667 cache but nothing was bound, the Port Sarim squire
 * (3801) was never spawned and the charter journeys 14/15 were never used - the outpost (bank booths, general store) could not be
 * reached by boat. Sources: 667 Void donor SquirePortSarim.kt (spawn 3041,3202; journey "port_sarim_to_ape_atoll" = 14, 10 ticks;
 * arrival Tile(2663, 2676, 1); "The ship arrives at the Void Knight outpost."). Void has no return trip: it uses journey 15 and lands
 * beside the Port Sarim squire (ADAPTED arrival tile). Free, like the Void trip. Routed through the Deadman transport gate.
 */
val CHARTER_INTERFACE = 299
val CHARTER_VARP = 75
val FADE_OUT_INTERFACE = 170

val TO_OUTPOST = Tile(2663, 2676, 1)
val TO_PORT_SARIM = Tile(3041, 3201, 0)

spawn_npc(npc = Npcs.SQUIRE_3801, x = 3041, z = 3202, height = 0, walkRadius = 0, direction = Direction.WEST)

on_npc_option(npc = Npcs.SQUIRE_3801, option = "travel") {
    sailVoid(player, CharterType.PORT_SARIM_TO_PEST_CONTROLL, TO_OUTPOST, "The ship arrives at the Void Knight outpost.")
}

on_npc_option(npc = Npcs.SQUIRE_3800, option = "travel") {
    sailVoid(player, CharterType.PEST_CONTROLL_TO_PORT_SARIM, TO_PORT_SARIM, "The ship arrives at Port Sarim.")
}

fun sailVoid(player: Player, charter: CharterType, destination: Tile, arrival: String) {
    player.interruptQueues()
    DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
        player.lockingQueue(TaskPriority.WEAK) {
            player.openInterface(CHARTER_INTERFACE, InterfaceDestination.MAIN_SCREEN_FULL)
            player.setVarp(CHARTER_VARP, charter.varpValue)
            wait(charter.delay)
            player.openInterface(FADE_OUT_INTERFACE, InterfaceDestination.MAIN_SCREEN_FULL)
            player.moveTo(destination)
            wait(3)
            player.closeMainInterface()
            player.setVarp(CHARTER_VARP, 0)
            player.queue(TaskPriority.WEAK) { messageBox(arrival) }
        }
    }
}

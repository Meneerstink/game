package gg.rsmod.plugins.content.areas.kandarin

import gg.rsmod.plugins.content.skills.agility.doorStep

/**
 * @author Harley<github.com/HarleyGilpin>
 */

// Fishing Guild Gate - Entrance
listOf(Objs.GATE_49014, Objs.GATE_49016).forEach {
    on_obj_option(obj = it, option = "open") {
        if (player.skills.getCurrentLevel(Skills.FISHING) < 68) {
            player.message("You need a Fishing level 68 to enter.")
            return@on_obj_option
        }
        handleGate(player)
    }
}

fun handleGate(player: Player) {
    val westClosedGate = DynamicObject(id = 49014, type = 0, rot = 1, tile = Tile(x = 2613, z = 3386))
    val eastClosedGate = DynamicObject(id = 49016, type = 0, rot = 1, tile = Tile(x = 2614, z = 3386))
    val westBlockedGate = DynamicObject(id = 0, type = 0, rot = 1, tile = Tile(x = 2613, z = 3386))
    val eastBlockedGate = DynamicObject(id = 0, type = 0, rot = 1, tile = Tile(x = 2614, z = 3386))
    val westOpenGate = DynamicObject(id = 49015, type = 0, rot = 0, tile = Tile(x = 2613, z = 3387))
    val eastOpenGate = DynamicObject(id = 49017, type = 0, rot = 2, tile = Tile(x = 2614, z = 3387))

    player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {
        world.remove(westClosedGate)
        world.remove(eastClosedGate)
        world.spawn(westBlockedGate)
        world.spawn(eastBlockedGate)
        world.spawn(westOpenGate)
        world.spawn(eastOpenGate)
        player.playSound(Sfx.GATE_OPEN)
        val x = if (player.tile.x == 2614) 2614 else 2613
        val z = if (player.tile.z <= 3386) 3387 else 3386
        doorStep(player, Tile(x = x, z = z))
        wait(3)
        world.remove(westOpenGate)
        world.remove(eastOpenGate)
        world.spawn(eastClosedGate)
        world.spawn(westClosedGate)
        player.playSound(Sfx.GATE_CLOSE)
    }
}

// Fishing Guild staircase 49031: no stub here - the generic stairs binding uses its object-teleports.json entry (2609,3398,0 ->
// 2608,3399,1; the stub answered "Nothing interesting happens." before the table, batch 15).

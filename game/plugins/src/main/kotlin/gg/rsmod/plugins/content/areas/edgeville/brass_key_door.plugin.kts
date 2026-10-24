package gg.rsmod.plugins.content.areas.edgeville

import gg.rsmod.plugins.content.skills.agility.doorStep

/**
 * @author Alycia <https://github.com/alycii>
 */

on_obj_option(obj = Objs.DOOR_1804, option = "open") {
    if (!player.inventory.contains(Items.BRASS_KEY)) {
        player.message("The door is locked.")
        return@on_obj_option
    }

    handleDoor(player)
}

// N362 (owner 2026-10-24): using the Brass key on the door opens it too (was "Nothing interesting happens").
on_item_on_obj(obj = Objs.DOOR_1804, item = Items.BRASS_KEY) {
    handleDoor(player)
}

on_obj_option(obj = Objs.LADDER_12389, option = "Climb-Down") {
    player.handleLadder(underground = true)
}

on_obj_option(obj = Objs.LADDER_29358, option = "Climb-Down") {
    // Void / object-teleports.json send this ladder to 3089,9971; handleLadder() kept the player's own tile (nothing moved).
    if (gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.tryTeleport(player, player.getInteractingGameObj(), player.attr[gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR] ?: 1)) return@on_obj_option
    player.handleLadder(underground = true)
}

fun handleDoor(player: Player) {
    val closedDoor = DynamicObject(id = 1804, type = 0, rot = 1, tile = Tile(x = 3115, z = 3449))
    val door =
        DynamicObject(
            id = 1804,
            type = 0,
            rot =
                if (player.tile.z ==
                    3450
                ) {
                    2
                } else {
                    0
                },
            tile = Tile(x = 3115, z = 3449),
        )
    player.lock = LockState.DELAY_ACTIONS
    world.remove(closedDoor)
    player.playSound(Sfx.DOOR_OPEN)
    world.spawn(door)

    // N363 (owner 2026-10-24, "soms blijft die open"): the door closes on the world's clock, so a walk that is interrupted or
    // cut short (the player's queue cleared) never leaves it open - and the player is never left locked.
    world.queue {
        wait(5)
        world.remove(door)
        world.spawn(closedDoor)
        player.playSound(Sfx.DOOR_CLOSE)
        // A walk queue that was dropped (not resumed) never runs its finally: unlock here too.
        if (player.lock == LockState.DELAY_ACTIONS) player.lock = LockState.NONE
    }
    player.queue {
        try {
            val x = 3115
            val z = if (player.tile.z == 3450) 3449 else 3450
            doorStep(player, Tile(x = x, z = z))
        } finally {
            player.lock = LockState.NONE
        }
    }
}

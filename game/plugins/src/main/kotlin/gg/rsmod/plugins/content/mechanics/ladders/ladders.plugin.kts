package gg.rsmod.plugins.content.mechanics.ladders

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState

on_obj_option(obj = Objs.LADDER_1747, option = "climb-up") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        2895, 2890 -> player.handleLadder(player.tile.x, player.tile.z, 2)
    }
}

on_obj_option(obj = Objs.LADDER_1746, option = "climb-down") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        2895, 2890 -> player.handleLadder(player.tile.x, player.tile.z, 1)
        2725, 2732 -> player.handleLadder(player.tile.x, player.tile.z, player.tile.height - 1)
    }
}

on_obj_option(obj = Objs.LADDER_1754, option = "climb-down") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        2594 -> player.handleLadder(2594, 9486, 0) // Wizards' Tower Basement
        2892 -> player.handleLadder(2893, 9907, 0) // Heroes' Guild Basement
    }
}

on_obj_option(obj = Objs.LADDER_1757, option = "climb-up") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        2594 -> player.handleLadder(2594, 3086, 0) // Wizards' Tower Basement
        2892 -> player.handleLadder(2892, 3508, 0) // Heroes' Guild Basement
    }
}

/*
 * Cache-derived fallback for every ladder option no hand-written handler above (or anywhere else)
 * claims - see GenericLadders for the evidence rule. Bound in the late world-init phase so every
 * ordinary binding is visible first; a destination is resolved at click time from the loaded map.
 */
on_world_init_late {
    var bound = 0
    var skipped = 0
    world.definitions.getAllKeys(ObjectDef::class.java).forEach { id ->
        val def = world.definitions.getNullable(ObjectDef::class.java, id) ?: return@forEach
        if (!GenericLadders.isLadder(def)) {
            return@forEach
        }
        def.options.forEachIndexed { slot, option ->
            val direction = GenericLadders.optionDirection(option) ?: return@forEachIndexed
            if ((slot + 1) in world.plugins.boundObjectOptions(id)) {
                skipped++
                return@forEachIndexed
            }
            on_obj_option(obj = id, option = option!!) {
                val obj = player.getInteractingGameObj()
                val destination = GenericLadders.destination(world, obj, player.tile, direction)
                if (destination == null) {
                    logger.info("Generic ladders: no counterpart for object {} ({}) at {} [{}]", obj.id, def.name, obj.tile, option)
                    return@on_obj_option
                }
                player.lockingQueue(lockState = LockState.FULL) {
                    wait(1)
                    player.animate(Anims.LADDER_CLIMB, idleOnly = true)
                    wait(2)
                    player.moveTo(destination)
                }
            }
            bound++
        }
    }
    logger.info("Generic ladders: bound {} cache-derived ladder options ({} already handled elsewhere).", bound, skipped)
}

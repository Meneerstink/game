package gg.rsmod.plugins.content.areas.varrock

// Cooking Guild stairs

// Floor 1
on_obj_option(obj = Objs.STAIRCASE_24073, option = "climb-up") {
    player.handleStairs(height = 1)
}
// Floor 2
on_obj_option(obj = Objs.STAIRCASE_24074, option = "climb") {
    player.queue {
        when (options("Climb up the staircase.", "Climb down the staircase.")) {
            1 -> player.moveTo(3144, 3446, 2)
            2 -> player.moveTo(player.tile.x, player.tile.z, player.tile.height - 1)
        }
    }
}

on_obj_option(obj = Objs.STAIRCASE_24074, option = "climb-up") {
    player.handleStairs(3144, 3446, 2)
}
on_obj_option(obj = Objs.STAIRCASE_24074, option = "climb-down") {
    player.handleStairs(height = 0)
}
// Floor 3
on_obj_option(obj = Objs.STAIRCASE_24075, option = "climb-down") {
    player.handleStairs(3144, 3449, 1)
}

// Juliet's House (and others, including Champion's Guild)
/*
 * 24357 / 24359 are shared by staircases all over Varrock (Juliet's house, the Champions' Guild, ...). The old handler matched
 * rotations 1..4 (real rotations are 0..3: rotation 0 did nothing at all) and moved the player 4 tiles from wherever he clicked,
 * collision-free. Now: the sourced Void table entry for this exact staircase, else the map-proven counterpart on the other floor
 * (GenericLadders: same anchor or up to 2 tiles off), landing beside it (conform traversal 2026-10-03, batch 6).
 */
fun climbVarrockStairs(player: Player, up: Boolean) {
    val obj = player.getInteractingGameObj()
    val slot = player.attr[gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR] ?: 1
    if (gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.tryTeleport(player, obj, slot)) return
    val dir = if (up) gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.Direction.UP else gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.Direction.DOWN
    val destination = gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.destination(world, obj, player.tile, dir)
    if (destination == null) {
        player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) { "staircase ${obj.id} at ${obj.tile} has no source and no counterpart" }
        return
    }
    player.lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.moveTo(destination)
    }
}

on_obj_option(obj = Objs.STAIRCASE_24357, option = "climb-up") { climbVarrockStairs(player, up = true) }

on_obj_option(obj = Objs.STAIRCASE_24359, option = "climb-down") { climbVarrockStairs(player, up = false) }

// Varrock Wall

// One floor from the ladder's own floor (24361 stands on floors 0 and 1, e.g. 3224,3472,1: a fixed height 1 went nowhere; batch 16).
on_obj_option(obj = Objs.LADDER_24361, option = "climb-up") {
    player.handleLadder(height = player.getInteractingGameObj().tile.height + 1)
}

on_obj_option(obj = Objs.LADDER_24362, option = "climb-down") {
    player.handleLadder(height = (player.getInteractingGameObj().tile.height - 1).coerceAtLeast(0))
}

// Varrock West Bank
on_obj_option(obj = Objs.STAIRCASE_24360, option = "climb-down") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        3189 -> player.handleStairs(x = 3190, underground = true)
        else -> gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
    }
}

on_obj_option(obj = Objs.STAIRCASE_24365, option = "climb-up") {
    val obj = player.getInteractingGameObj()
    when (obj.tile.x) {
        3187 -> player.handleStairs(3188, 3433, height = 0)
        else -> gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
    }
}

on_obj_option(obj = Objs.LADDER_24354, option = "climb-up") {
    player.handleLadder(height = 1)
}

on_obj_option(obj = Objs.LADDER_24355, option = "climb-down") {
    player.handleLadder(height = 0)
}

// Blue Moon Inn
on_obj_option(obj = Objs.LADDER_24363, option = "climb-down") {
    player.handleLadder(underground = true)
}

on_obj_option(obj = Objs.LADDER_24364, option = "climb-up") {
    player.handleLadder(underground = true)
}

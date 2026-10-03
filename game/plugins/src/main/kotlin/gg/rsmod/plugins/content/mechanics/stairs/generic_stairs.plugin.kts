import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
import gg.rsmod.plugins.content.mechanics.stairs.GenericStairs

fun climbGenericStairs(player: Player, slot: Int, direction: GenericStairs.Direction) {
    val obj = player.getInteractingGameObj()
    if (ObjectTeleports.tryTeleport(player, obj, slot)) return
    // Same-tile counterpart first; else the ladder rule (counterpart up to 2 tiles off on the next floor, or the mirrored dungeon
    // tile) - a staircase re-placed one tile off answered "Nothing interesting happens" (conform traversal 2026-10-03, batch 7:
    // 30+ placements in one batch; ~340 Stairs/Staircase rows sweep-wide).
    val destination =
        GenericStairs.destination(world, obj, player.tile, direction)
            ?: gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.destination(
                world,
                obj,
                player.tile,
                if (direction == GenericStairs.Direction.UP) gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.Direction.UP else gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.Direction.DOWN,
            )
    if (destination == null) {
        player.message(Entity.NOTHING_INTERESTING_HAPPENS)
        return
    }
    player.lockingQueue(lockState = LockState.FULL) {
        wait(2)
        player.moveTo(destination)
    }
}

on_world_init_late {
    var bound = 0
    var skipped = 0
    world.definitions.getAllKeys(ObjectDef::class.java).forEach { id ->
        val def = world.definitions.getNullable(ObjectDef::class.java, id) ?: return@forEach
        if (!GenericStairs.isStair(def)) return@forEach
        def.options.forEachIndexed { slot, option ->
            val direction = GenericStairs.optionDirection(option) ?: return@forEachIndexed
            if ((slot + 1) in world.plugins.boundObjectOptions(id)) {
                skipped++
                return@forEachIndexed
            }
            on_obj_option(obj = id, option = option!!) {
                climbGenericStairs(player, slot + 1, direction)
            }
            bound++
        }
    }
    logger.info("Generic stairs: bound {} cache-derived options ({} already handled elsewhere).", bound, skipped)
}

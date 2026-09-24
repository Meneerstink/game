package gg.rsmod.plugins.content.mechanics.ladders

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports

/*
 * Every ladder / trapdoor / vine (and, for Climb, staircase) the world places that no plugin handles (traversal census 2026-09-24: ~50 ladder ids such as 14747,
 * 14296, 28386, 47162, 1748 - clicks said "Nothing interesting happens"). One object FALLBACK - consulted only when no handler is
 * bound - so hand-written ladders always win:
 * - Climb-up / Climb-down: the sourced object-teleport table, else [GenericLadders] (a destination only when the map holds the
 *   counterpart ladder). No evidence -> not handled, so the unhandled-actions log keeps reporting it.
 * - Climb (middle floors): Void's 667 choice "What would you like to do?" - "Go up the stairs." / "Go down the stairs." / "Never mind."
 *   (content/entity/obj/Stairs.kt), then the object's own Climb-up / Climb-down route (its handler when one is bound).
 */
world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    val ladder = GenericLadders.isLadder(def)
    val stairs = def.name.contains("stair", ignoreCase = true)
    if (!ladder && !stairs) return@bindObjectFallback false
    val option = def.options.getOrNull(opt - 1)?.lowercase() ?: return@bindObjectFallback false
    val direction = GenericLadders.optionDirection(option)
    when {
        direction != null -> climb(player, obj, opt, direction, ladder)
        option == "climb" -> {
            val up = def.options.indexOfFirst { GenericLadders.optionDirection(it) == GenericLadders.Direction.UP }
            val down = def.options.indexOfFirst { GenericLadders.optionDirection(it) == GenericLadders.Direction.DOWN }
            val canUp = up >= 0 || (ladder && GenericLadders.destination(player.world, obj, player.tile, GenericLadders.Direction.UP) != null)
            val canDown = down >= 0 || (ladder && GenericLadders.destination(player.world, obj, player.tile, GenericLadders.Direction.DOWN) != null)
            if (!canUp && !canDown) return@bindObjectFallback false
            player.queue {
                when (options("Go up the stairs.", "Go down the stairs.", "Never mind.", title = "What would you like to do?")) {
                    1 -> climbVia(player, obj, up, GenericLadders.Direction.UP, ladder)
                    2 -> climbVia(player, obj, down, GenericLadders.Direction.DOWN, ladder)
                }
            }
            true
        }
        else -> false
    }
}

/** A Climb-up/-down slot of the same object: its own handler when bound, else the generic route. */
fun climbVia(player: Player, obj: GameObject, index: Int, direction: GenericLadders.Direction, ladder: Boolean) {
    val slot = index + 1
    if (index >= 0 && slot in player.world.plugins.boundObjectOptions(obj.getTransform(player))) {
        player.attr[INTERACTING_OPT_ATTR] = slot
        player.world.plugins.executeObject(player, obj.getTransform(player), slot)
        return
    }
    if (!climb(player, obj, slot, direction, ladder)) player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
}

fun climb(player: Player, obj: GameObject, slot: Int, direction: GenericLadders.Direction, ladder: Boolean): Boolean {
    if (slot > 0 && ObjectTeleports.tryTeleport(player, obj, slot)) return true
    if (!ladder) return false // stairs move only by the sourced teleport table
    val destination = GenericLadders.destination(player.world, obj, player.tile, direction) ?: return false
    player.lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.animate(Anims.LADDER_CLIMB, idleOnly = true)
        wait(2)
        player.moveTo(destination)
    }
    return true
}

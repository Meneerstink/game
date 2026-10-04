package gg.rsmod.plugins.content.mechanics.traversal.batch5

import gg.rsmod.plugins.content.skills.agility.*

/*
 * North-east Misthalin (obstacle batch 5, owner priority #1 2026-10-03).
 *
 * Lumber Yard fence 31149 "Squeeze-under" (3295,3498, a wall on the east edge of that tile - MapProbe): 2009scape
 * region/misthalin/varrock/handlers/LumberYardCratePlugin.java (from x > 3295: 3296,3498 -> 3295,3498, else 3295,3498 -> 3296,3498,
 * ForceMovement with animation 9221); the OSRS Wiki (Gertrude's Cat: squeeze under the fence into the Lumber Yard) agrees there is
 * this way in, with no requirement. One exact move across the fence edge from the tile in front of it.
 *
 * Bone stiles 44378 (3368,3513) and 44379 (3379,3513): the two ends of a fenced lane (MapProbe: 3369-3378,3513 walled in on both
 * sides). The stile class of Void Stiles.climbStile through the shared climbOver (walk to the tile in front, face, rocks_pile_climb
 * 839, one 94-cycle slide to the tile behind); no xp and no message for stiles in Void.
 */

val LUMBER_YARD_FENCE = 31149
val ANIM_FENCE_SQUEEZE = 9221
val BONE_STILES = listOf(44378, 44379)

on_world_init {
    // Darkan Varrock.java:254-257 (handleLumberYardFence): entering = player.x <= obj.x; useStairs(entering ? 9221 : 9220, (obj.x +
    // (entering ? 1 : 0), obj.z)) - the squeeze animation (9220 from the east) and the teleport across one tick later, no slide.
    shortcut(LUMBER_YARD_FENCE, "Squeeze-under") {
        obstacle { target ->
            val entering = player.tile.x <= target.tile.x
            player.animate(if (entering) ANIM_FENCE_SQUEEZE else 9220)
            wait(1)
            player.moveTo(Tile(target.tile.x + if (entering) 1 else 0, target.tile.z, target.tile.height))
        }
    }
    BONE_STILES.forEach { id ->
        shortcut(id, "Climb-over") { obstacle { climbOver(player, it) } }
    }
}

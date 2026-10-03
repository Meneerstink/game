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
    shortcut(LUMBER_YARD_FENCE, "Squeeze-under") {
        obstacle {
            val fromEast = player.tile.x > 3295
            val start = if (fromEast) Tile(3296, 3498) else Tile(3295, 3498)
            val landing = if (fromEast) Tile(3295, 3498) else Tile(3296, 3498)
            val direction = if (fromEast) Direction.WEST else Direction.EAST
            walkToTile(player, start)
            face(player, direction)
            player.animate(ANIM_FENCE_SQUEEZE)
            exactMove(player, landing, direction = direction)
        }
    }
    BONE_STILES.forEach { id ->
        shortcut(id, "Climb-over") { obstacle { climbOver(player, it) } }
    }
}

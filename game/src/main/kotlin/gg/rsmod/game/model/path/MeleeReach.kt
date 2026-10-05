package gg.rsmod.game.model.path

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionManager

/**
 * Darkan (rev 727) `World.checkMeleeStep` = rsmod2 `reached(..., objShape = -2)`: a body of any size "reaches" another when the two
 * share an edge (not overlapping, not only a corner) and at least one pair of tiles across that edge has no wall between them.
 * Used by Darkan `EntityFollow` (a familiar/pet stops there) and by every melee approach. Bodies are given by their south-west tile
 * and size.
 */
object MeleeReach {
    fun bordersWithoutWall(
        collision: CollisionManager,
        source: Tile,
        sourceWidth: Int,
        sourceLength: Int,
        target: Tile,
        targetWidth: Int,
        targetLength: Int,
    ): Boolean {
        if (source.height != target.height) return false
        val gapX = maxOf(0, target.x - (source.x + sourceWidth - 1), source.x - (target.x + targetWidth - 1))
        val gapZ = maxOf(0, target.z - (source.z + sourceLength - 1), source.z - (target.z + targetLength - 1))
        if (gapX + gapZ != 1) return false
        if (gapX == 1) {
            val east = source.x + sourceWidth - 1 < target.x
            val x = if (east) source.x + sourceWidth - 1 else source.x
            val direction = if (east) Direction.EAST else Direction.WEST
            for (z in maxOf(source.z, target.z)..minOf(source.z + sourceLength - 1, target.z + targetLength - 1)) {
                if (!collision.isBlocked(Tile(x, z, source.height), direction, projectile = false)) return true
            }
            return false
        }
        val north = source.z + sourceLength - 1 < target.z
        val z = if (north) source.z + sourceLength - 1 else source.z
        val direction = if (north) Direction.NORTH else Direction.SOUTH
        for (x in maxOf(source.x, target.x)..minOf(source.x + sourceWidth - 1, target.x + targetWidth - 1)) {
            if (!collision.isBlocked(Tile(x, z, source.height), direction, projectile = false)) return true
        }
        return false
    }
}

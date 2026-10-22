package gg.rsmod.game.model.path.strategy

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.path.PathFindingStrategy
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.Route
import java.util.ArrayDeque

/**
 * OSRS npc movement ("dumb" / follow-mode pathing), breach conformance 2026-09-27 (A2).
 *
 * OSRS Wiki "Pathfinding": follow mode, "identical to the pathing of NPCs", "naively paths diagonally to the end tile and then
 * straight if there's no diagonals left"; "Safespot": NPCs "always move directly toward their target and do not detour around
 * obstacles if that would mean moving away", and "larger monsters ... may also end up snagged on a corner".
 *
 * Every step: towards the target's footprint on both axes -> the diagonal step; blocked (or no longer needed on one axis) -> the
 * straight step on the x axis, else on the z axis; nothing traversable -> the npc stays (a safespot). The whole footprint must be
 * able to make the step. Arrival for an interaction (touch radius 1): the footprints border without overlapping and not only at a
 * corner, so a melee npc diagonal to its target still takes the straight step it needs. Arrival for a plain walk: the end tile.
 *
 * [SimplePathFindingStrategy] (the rsmod default) walked the whole z distance first and then the x distance - an L, never the
 * OSRS diagonal - which is why breach monsters "did not follow correctly".
 */
class NaivePathFindingStrategy(
    collision: CollisionManager,
    private val pawn: Pawn,
) : PathFindingStrategy(collision) {
    override fun calculateRoute(request: PathRequest): Route {
        val size = request.sourceWidth.coerceAtLeast(1)
        val end = request.end
        val interaction = request.touchRadius > 0 || request.targetWidth > 0
        val targetSize = request.targetWidth.coerceAtLeast(1)
        val path = ArrayDeque<Tile>()
        var tail = request.start
        var success = false
        for (i in 0 until MAX_STEPS) {
            if (arrived(tail, size, end, targetSize, interaction)) {
                success = true
                break
            }
            val direction = nextDirection(tail, size, end, targetSize, interaction) ?: break
            tail = tail.step(direction)
            path.add(tail)
        }
        if (!success) success = arrived(tail, size, end, targetSize, interaction)
        return Route(path, success, tail)
    }

    private fun arrived(
        tile: Tile,
        size: Int,
        end: Tile,
        targetSize: Int,
        interaction: Boolean,
    ): Boolean {
        if (!interaction) return tile.x == end.x && tile.z == end.z
        val gapX = gap(tile.x, size, end.x, targetSize)
        val gapZ = gap(tile.z, size, end.z, targetSize)
        // Bordering on one axis and overlapping (gap -1 .. inside) on the other: side by side, not corner to corner.
        return (gapX == 0 && overlaps(tile.z, size, end.z, targetSize)) || (gapZ == 0 && overlaps(tile.x, size, end.x, targetSize))
    }

    /** Tiles between two spans on one axis: 0 = touching, > 0 = apart, -1 = overlapping. */
    private fun gap(
        a: Int,
        aSize: Int,
        b: Int,
        bSize: Int,
    ): Int =
        when {
            a + aSize - 1 < b -> b - (a + aSize - 1) - 1
            b + bSize - 1 < a -> a - (b + bSize - 1) - 1
            else -> -1
        }

    private fun overlaps(
        a: Int,
        aSize: Int,
        b: Int,
        bSize: Int,
    ): Boolean = a <= b + bSize - 1 && b <= a + aSize - 1

    private fun nextDirection(
        tile: Tile,
        size: Int,
        end: Tile,
        targetSize: Int,
        interaction: Boolean,
    ): Direction? {
        var dx: Int
        var dz: Int
        if (!interaction) {
            dx = Integer.signum(end.x - tile.x)
            dz = Integer.signum(end.z - tile.z)
        } else {
            dx = if (tile.x + size - 1 < end.x) 1 else if (tile.x > end.x + targetSize - 1) -1 else 0
            dz = if (tile.z + size - 1 < end.z) 1 else if (tile.z > end.z + targetSize - 1) -1 else 0
            if (dx == 0 && dz == 0) {
                // Standing on (under) the target: step out on the first free side.
                return CARDINALS.firstOrNull { canStep(tile, size, it) }
            }
            // Diagonally adjacent (touching only at a corner): a diagonal step would land on the target - go straight instead.
            val gapX = gap(tile.x, size, end.x, targetSize)
            val gapZ = gap(tile.z, size, end.z, targetSize)
            if (dx != 0 && dz != 0 && gapX == 0 && gapZ == 0) {
                dz = if (canStep(tile, size, direction(dx, 0)!!)) 0 else dz
                if (dz != 0) dx = 0
            }
        }
        if (dx != 0 && dz != 0) {
            val diagonal = direction(dx, dz)!!
            if (canStep(tile, size, diagonal)) return diagonal
        }
        if (dx != 0) {
            val straight = direction(dx, 0)!!
            if (canStep(tile, size, straight)) return straight
        }
        if (dz != 0) {
            val straight = direction(0, dz)!!
            if (canStep(tile, size, straight)) return straight
        }
        return null
    }

    private fun canStep(
        tile: Tile,
        size: Int,
        direction: Direction,
    ): Boolean {
        val water = (pawn.walkMask and 0x4) != 0
        // Owner 2026-09-27 "breachmonsters kunnen allemaal noclippen": the entered tile is asked too (clipLink). N169: the same
        // body rule as BFSPathFindingStrategy and MovementQueue.
        return collision.canBodyStep(tile, size, size, direction, water = water)
    }

    private fun direction(
        dx: Int,
        dz: Int,
    ): Direction? = Direction.values().firstOrNull { it != Direction.NONE && it.getDeltaX() == dx && it.getDeltaZ() == dz }

    companion object {
        /** Steps planned ahead; npc combat plans again every tick, so a short plan is enough. */
        private const val MAX_STEPS = 32

        private val CARDINALS = listOf(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH)
    }
}

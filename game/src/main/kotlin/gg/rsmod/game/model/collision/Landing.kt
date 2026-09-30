package gg.rsmod.game.model.collision

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile

/**
 * Where a teleport puts the player. Owner 2026-09-30 (bug report "I teleport to trollheim i end up bugged in this exact position"):
 * every teleport picked a random tile of its destination area, or used its fixed tile, without asking the map; the Trollheim
 * Teleport landed on 2888,3675 - a rock - where the player could not step in any direction. The same holds for 21 tiles of the
 * spellbook areas alone (Falador, Ardougne, Kharyrll, Annakarl, Moonclan, Ourania, Waterbirth, ...; TeleportDestinationTests).
 *
 * The rule: the landing is a tile of the largest walkable pocket among the destination's tiles (flood fill over
 * [CollisionManager.canStep], counted up to [OPEN_GROUND]). A tile inside scenery forms a pocket of one and is never chosen
 * over open ground; a small room the destination deliberately lies in is still the largest pocket there.
 */
object Landing {
    /** Pocket size that counts as open ground (flood fill stops here). */
    const val OPEN_GROUND = 64

    /** How far around a fixed destination tile a better landing is looked for. */
    const val SEARCH_RADIUS = 4

    /** Tiles reachable by walking from [start], up to [limit]. */
    fun pocket(
        collision: CollisionManager,
        start: Tile,
        limit: Int = OPEN_GROUND,
    ): Set<Tile> {
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty() && seen.size < limit) {
            val tile = queue.removeFirst()
            for (dir in Direction.NESW) {
                if (!collision.canStep(tile, dir)) continue
                val next = tile.step(dir)
                if (seen.add(next)) queue.addLast(next)
            }
        }
        return seen
    }

    /** The candidates that lie in the largest pocket among them (ties: the first candidate's order decides). */
    fun bestPocket(
        collision: CollisionManager,
        candidates: List<Tile>,
    ): List<Tile> {
        var best = emptyList<Tile>()
        var bestSize = -1
        val done = HashSet<Tile>()
        for (tile in candidates) {
            if (tile in done) continue
            val pocket = pocket(collision, tile)
            val members = candidates.filter { it in pocket || it == tile }
            done += members
            // A capped flood fill may not contain every candidate of the same open ground; they then form their own group of
            // the same (capped) size, which is equally good.
            if (pocket.size > bestSize) {
                best = members
                bestSize = pocket.size
            } else if (pocket.size == bestSize) {
                best = best + members
            }
        }
        return best
    }

    /** A random landing tile of [area] on [level]. */
    fun pick(
        collision: CollisionManager,
        area: Area,
        level: Int = 0,
        random: kotlin.random.Random = kotlin.random.Random,
    ): Tile {
        val tiles = (area.bottomLeftX..area.topRightX).flatMap { x -> (area.bottomLeftZ..area.topRightZ).map { z -> Tile(x, z, level) } }.shuffled(random)
        return bestPocket(collision, tiles).first()
    }

    /** [tile] when it lies in the largest pocket around it, else the nearest tile that does. */
    fun resolve(
        collision: CollisionManager,
        tile: Tile,
    ): Tile {
        val around =
            (0..SEARCH_RADIUS).flatMap { r ->
                (-r..r).flatMap { dx -> (-r..r).map { dz -> dx to dz } }.filter { (dx, dz) -> maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r }
                    .map { (dx, dz) -> Tile(tile.x + dx, tile.z + dz, tile.height) }
            }
        val best = bestPocket(collision, around)
        return if (tile in best) tile else best.minByOrNull { maxOf(kotlin.math.abs(it.x - tile.x), kotlin.math.abs(it.z - tile.z)) } ?: tile
    }
}

package gg.rsmod.plugins.content.mechanics.ladders

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject

/**
 * Cache-derived ladder travel for every ladder the hand-written handlers do not cover.
 *
 * The landscape files carry no destinations, so a destination is only accepted when the map
 * itself proves it: a counterpart ladder must exist either on the same tile one floor up/down
 * (buildings), or on the mirrored dungeon tile (`z +/- 6400`, the RuneScape convention every
 * hand-written dungeon ladder in this codebase already follows) within [DUNGEON_SEARCH_RADIUS]
 * tiles. Ladders with no such counterpart are left untouched so the unhandled-action diagnostic
 * keeps reporting them instead of a guessed destination silently sending players somewhere wrong.
 */
object GenericLadders {
    private const val DUNGEON_OFFSET = 6400
    private const val DUNGEON_SEARCH_RADIUS = 2

    enum class Direction { UP, DOWN }

    fun isLadder(def: ObjectDef): Boolean = def.name.contains("ladder", ignoreCase = true)

    fun optionDirection(option: String?): Direction? =
        when (option?.lowercase()) {
            "climb-up", "climb up" -> Direction.UP
            "climb-down", "climb down" -> Direction.DOWN
            else -> null
        }

    /**
     * Where a player standing on [from] who climbs [ladder] in [direction] should arrive, or null
     * when the map holds no counterpart ladder for it.
     */
    fun destination(
        world: World,
        ladder: GameObject,
        from: Tile,
        direction: Direction,
    ): Tile? {
        val opposite = if (direction == Direction.UP) Direction.DOWN else Direction.UP

        // 1. Same tile, one floor up or down (buildings, towers).
        val floor = ladder.tile.height + if (direction == Direction.UP) 1 else -1
        if (floor in 0..3) {
            val above = Tile(ladder.tile.x, ladder.tile.z, floor)
            if (ladderAt(world, above, opposite) != null) {
                return standTile(world, Tile(from.x, from.z, floor), above)
            }
        }

        // 2. Mirrored dungeon tile.
        val mirroredZ =
            when {
                direction == Direction.DOWN && ladder.tile.z < DUNGEON_OFFSET -> ladder.tile.z + DUNGEON_OFFSET
                direction == Direction.UP && ladder.tile.z >= DUNGEON_OFFSET -> ladder.tile.z - DUNGEON_OFFSET
                else -> return null
            }
        for (dx in -DUNGEON_SEARCH_RADIUS..DUNGEON_SEARCH_RADIUS) {
            for (dz in -DUNGEON_SEARCH_RADIUS..DUNGEON_SEARCH_RADIUS) {
                val tile = Tile(ladder.tile.x + dx, mirroredZ + dz, 0)
                val counterpart = ladderAt(world, tile, opposite) ?: continue
                val mirroredFrom = Tile(from.x, from.z + (mirroredZ - ladder.tile.z), 0)
                return standTile(world, mirroredFrom, counterpart.tile)
            }
        }
        return null
    }

    private fun ladderAt(
        world: World,
        tile: Tile,
        direction: Direction,
    ): GameObject? {
        val chunk = world.chunks.get(tile, createIfNeeded = true) ?: return null
        return chunk
            .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .firstOrNull { obj ->
                val def = obj.getDef(world.definitions)
                isLadder(def) && def.options.any { optionDirection(it) == direction }
            }
    }

    /**
     * The mirrored standing tile when it is walkable, otherwise the first walkable tile adjacent
     * to the counterpart ladder.
     */
    private fun standTile(
        world: World,
        preferred: Tile,
        counterpart: Tile,
    ): Tile? {
        if (preferred.isWithinRadius(counterpart, 1) && !world.collision.isClipped(preferred)) {
            return preferred
        }
        val candidates =
            listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
                .map { (dx, dz) -> Tile(counterpart.x + dx, counterpart.z + dz, counterpart.height) }
        return candidates.firstOrNull { !world.collision.isClipped(it) }
    }
}

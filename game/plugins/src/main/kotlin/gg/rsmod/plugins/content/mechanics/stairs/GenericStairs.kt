package gg.rsmod.plugins.content.mechanics.stairs

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionManager
import gg.rsmod.game.model.entity.GameObject

/**
 * Cache-derived fallback for staircases not covered by a hand-written route.
 *
 * A landscape only proves a staircase transition when the opposite-direction staircase is
 * present at the same tile on the adjacent plane.  Anything else is deliberately left
 * unhandled: a nearby staircase is not sufficient evidence for a destination.
 */
object GenericStairs {
    enum class Direction { UP, DOWN }

    fun isStair(def: ObjectDef): Boolean =
        def.name.contains("stair", ignoreCase = true)

    fun optionDirection(option: String?): Direction? =
        when (option?.trim()?.lowercase()?.replace(' ', '-')) {
            "climb-up", "walk-up", "ascend" -> Direction.UP
            "climb-down", "walk-down", "descend" -> Direction.DOWN
            else -> null
        }

    fun destination(
        world: World,
        stair: GameObject,
        from: Tile,
        direction: Direction,
    ): Tile? {
        val opposite = if (direction == Direction.UP) Direction.DOWN else Direction.UP
        val floor = stair.tile.height + if (direction == Direction.UP) 1 else -1
        if (floor !in 0..3) return null

        val counterpartTile = Tile(stair.tile.x, stair.tile.z, floor)
        val counterpart = stairAt(world, counterpartTile, opposite) ?: return null
        val preferred = Tile(from.x, from.z, floor)
        return standTile(world, preferred, counterpart.tile)
    }

    private fun stairAt(world: World, tile: Tile, direction: Direction): GameObject? {
        val chunk = world.chunks.get(tile, createIfNeeded = true) ?: return null
        return chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .firstOrNull { obj ->
                val def = obj.getDef(world.definitions)
                isStair(def) && def.options.any { optionDirection(it) == direction }
            }
    }

    private fun standTile(world: World, preferred: Tile, counterpart: Tile): Tile? {
        if (preferred.isWithinRadius(counterpart, 1) && !world.collision.isClipped(preferred)) {
            return preferred
        }
        return listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
            .map { (dx, dz) -> Tile(counterpart.x + dx, counterpart.z + dz, counterpart.height) }
            .firstOrNull { !world.collision.isClipped(it) }
    }
}

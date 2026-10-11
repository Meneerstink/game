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

    /**
     * Ladders, open trapdoors whose "Climb-down" leads to a ladder on the mirrored dungeon tile,
     * and vines: confirmed with `ObjectPlacementProbeTool` (2026-09-18) that this cache's vine ids
     * follow the exact same "same tile, id changes with height, Climb-up/Climb-down" shape as
     * ladders (e.g. obj 27126/27151/27129/27130 all sit at (2894,2980), one per floor) - the same
     * evidence rule below already covers them correctly, so no separate vine-specific logic exists.
     */
    fun isLadder(def: ObjectDef): Boolean =
        def.name.contains("ladder", ignoreCase = true) ||
            def.name.contains("trapdoor", ignoreCase = true) ||
            def.name.contains("vine", ignoreCase = true) ||
            isStairs(def)

    /**
     * Staircases follow the same evidence rule (conform traversal 2026-10-03: ~480 staircase options answered "Nothing interesting
     * happens"; the map probe found their counterpart on the other floor on the same anchor or one or two tiles off, e.g. 10017 /
     * 10018 at (1893,4620) floors 1 / 2, 10525 / 10526 one tile apart).
     */
    fun isStairs(def: ObjectDef): Boolean =
        def.name.contains("stair", ignoreCase = true) || def.name.equals("steps", ignoreCase = true) ||
            def.name.contains("trapdoor steps", ignoreCase = true)

    /** How far from the clicked loc's anchor the counterpart on the other floor may sit (ladders are re-placed a tile off). */
    private const val FLOOR_SEARCH_RADIUS = 2

    fun optionDirection(option: String?): Direction? =
        when (option?.lowercase()) {
            "climb-up", "climb up", "ascend", "go-up", "walk-up" -> Direction.UP
            "climb-down", "climb down", "descend", "go-down", "walk-down" -> Direction.DOWN
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

        // 1. One floor up or down, on the same anchor or up to FLOOR_SEARCH_RADIUS tiles off (buildings, towers); nearest first.
        val floor = ladder.tile.height + if (direction == Direction.UP) 1 else -1
        if (floor in 0..3) {
            val candidates =
                (-FLOOR_SEARCH_RADIUS..FLOOR_SEARCH_RADIUS).flatMap { dx -> (-FLOOR_SEARCH_RADIUS..FLOOR_SEARCH_RADIUS).map { dz -> dx to dz } }
                    .sortedBy { (dx, dz) -> dx * dx + dz * dz }
            for ((dx, dz) in candidates) {
                val counterpart = ladderAt(world, Tile(ladder.tile.x + dx, ladder.tile.z + dz, floor), opposite) ?: continue
                return standTile(world, Tile(from.x + dx, from.z + dz, floor), counterpart.tile, counterpart)
            }
        }

        // 2. Mirrored dungeon tile - only between ground floors: a roof ladder (Sophanem 20357 at 3279,2771,3) with no counterpart on
        // the floor below dropped the player into the dungeon at 3278,9171 (batch 16).
        if (ladder.tile.height != 0) return null
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
                return standTile(world, mirroredFrom, counterpart.tile, counterpart)
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
     * Where the climber stands on the other floor: the mirror of his own tile when that is a usable tile right beside the
     * counterpart, else the usable tile beside the counterpart's whole footprint nearest to that mirror - never on the counterpart
     * itself (a staircase is 2x2 and more) and never in a walled-in pocket ([isUsableLanding]).
     */
    private fun standTile(
        world: World,
        preferred: Tile,
        counterpart: Tile,
        loc: GameObject? = null,
    ): Tile? {
        val def = loc?.getDef(world.definitions)
        val w = if (loc != null && def != null) def.getRotatedWidth(loc) else 1
        val l = if (loc != null && def != null) def.getRotatedLength(loc) else 1
        fun inside(t: Tile) = t.x >= counterpart.x && t.x < counterpart.x + w && t.z >= counterpart.z && t.z < counterpart.z + l
        val around =
            (counterpart.x - 1..counterpart.x + w).flatMap { x -> (counterpart.z - 1..counterpart.z + l).map { z -> Tile(x, z, counterpart.height) } }
                .filter { !inside(it) }
        if (around.any { it.sameAs(preferred) } && gg.rsmod.plugins.content.skills.agility.isUsableLanding(world, preferred)) return preferred
        // A small floor (a ship's deck, a tower top) has no "usable landing" room anywhere; then any standable tile beside the
        // counterpart, the way back down being that ladder (batch 16: 245 at 2062,5516 / 2062,5518).
        val pool =
            around.filter { gg.rsmod.plugins.content.skills.agility.isUsableLanding(world, it) }
                .ifEmpty { around.filter { gg.rsmod.game.model.collision.Landing.pocket(world.collision, it).size >= 2 } }
        return pool
            .minByOrNull { (it.x - preferred.x) * (it.x - preferred.x) + (it.z - preferred.z) * (it.z - preferred.z) }
    }
}

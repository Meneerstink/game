package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Danger signs at the guarded -> dangerous transitions (owner "deadmanmode vervijning" 2026-09-17:
 * "we already have danger signs in our rsps so use the existing ... place it smoothly so it doesnt
 * bug or glitch into a wall or building"). The sign is the cache's own skull-and-crossbones
 * signpost [Objs.DANGER_SIGN] (examine "Danger!").
 *
 * Placement is derived from the very same zone polygons the HUD, PvP gate and guards use
 * ([GuardedZones]), once at boot, over each zone's bounding box only:
 * 1. a *crossing* is a walkable ground-floor tile inside the zone with a walkable tile directly
 *    outside the zone that can be stepped to and from (so walls, fences and water never count);
 * 2. crossings are grouped by adjacency: a gate or a road opening becomes one short group, an open
 *    field edge one long group;
 * 3. a short group ([MAX_OPENING_TILES] or fewer crossings - a door, gate or road) gets one sign on
 *    a free tile *beside* the opening, one step inside the zone, so the opening itself is never
 *    narrowed; a long group gets a sign every [FIELD_SIGN_SPACING] tiles along the edge, one step
 *    inside;
 * 4. a sign tile must be walkable, unroofed (not inside a building), free of every object, not a
 *    crossing itself and not adjacent to another sign.
 *
 * The chat warning "Warning: You are entering a dangerous zone." is sent by [DeadmanHud] on the
 * transition itself, so the sign and the message can never disagree about where the edge is.
 */
object DangerSigns {
    const val OBJECT_ID = Objs.DANGER_SIGN
    const val OBJECT_TYPE = 10

    /** Openings up to this many crossing tiles are treated as a gate/road and get one sign. */
    const val MAX_OPENING_TILES = 12

    /** Along an open field edge, one sign every this many crossing tiles. */
    const val FIELD_SIGN_SPACING = 24

    /** Safety cap per zone so a mis-shaped polygon can never spam a city. */
    const val MAX_SIGNS_PER_ZONE = 30

    class Crossing(
        val inside: Tile,
        /** Direction from [inside] to the outside tile. */
        val outward: Direction,
    )

    private val placedTiles = ArrayList<Tile>()

    fun placed(): List<Tile> = placedTiles

    /** Boot entry point: returns the summary line for the log. */
    fun place(world: World): String {
        placedTiles.clear()
        val summary = StringBuilder()
        var total = 0
        GuardedZones.ZONES.forEach { zone ->
            if (0 !in zone.heights) return@forEach
            val crossings = findCrossings(world, zone)
            val groups = group(crossings)
            val signs = ArrayList<Pair<Tile, Direction>>()
            groups.forEach { group ->
                if (signs.size >= MAX_SIGNS_PER_ZONE) return@forEach
                val wanted = if (group.size <= MAX_OPENING_TILES) listOf(group) else group.chunked(FIELD_SIGN_SPACING)
                wanted.forEach { run ->
                    if (signs.size >= MAX_SIGNS_PER_ZONE) return@forEach
                    val sign = pickSignTile(world, zone, run, crossings, signs.map { it.first }) ?: return@forEach
                    signs += sign
                }
            }
            signs.forEach { (tile, outward) ->
                world.spawn(DynamicObject(OBJECT_ID, OBJECT_TYPE, rotationFacing(outward), tile))
                placedTiles += tile
            }
            total += signs.size
            if (signs.isNotEmpty()) summary.append(" ${zone.name}=${signs.size}")
        }
        return "DangerSigns: placed $total danger signs at guarded-zone edges;$summary"
    }

    private fun walkable(
        world: World,
        tile: Tile,
    ): Boolean {
        val chunk = world.chunks.get(tile, createIfNeeded = false) ?: return false
        return !chunk.isClipped(tile)
    }

    fun findCrossings(
        world: World,
        zone: GuardedZones.Zone,
    ): List<Crossing> {
        val result = ArrayList<Crossing>()
        for (x in zone.minX..zone.maxX) {
            for (z in zone.minZ..zone.maxZ) {
                val tile = Tile(x, z, 0)
                if (!zone.contains(tile) || GuardedZones.zoneAt(tile) !== zone) continue
                if (!walkable(world, tile)) continue
                for (direction in Direction.NESW) {
                    val outside = tile.step(direction)
                    if (GuardedZones.contains(outside) || !walkable(world, outside)) continue
                    if (!world.collision.canTraverse(tile, direction, projectile = false, water = false)) continue
                    if (!world.collision.canTraverse(outside, direction.getOpposite(), projectile = false, water = false)) continue
                    result += Crossing(tile, direction)
                    break
                }
            }
        }
        return result
    }

    /** Groups crossings that touch each other (8-neighbourhood) into openings. */
    fun group(crossings: List<Crossing>): List<List<Crossing>> {
        val byTile = crossings.associateBy { it.inside }
        val seen = HashSet<Tile>()
        val groups = ArrayList<List<Crossing>>()
        crossings.forEach { start ->
            if (!seen.add(start.inside)) return@forEach
            val group = ArrayList<Crossing>()
            val queue = ArrayDeque<Crossing>()
            queue += start
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                group += current
                for (dx in -1..1) for (dz in -1..1) {
                    if (dx == 0 && dz == 0) continue
                    val next = byTile[current.inside.transform(dx, dz)] ?: continue
                    if (seen.add(next.inside)) queue += next
                }
            }
            groups += group
        }
        return groups
    }

    private fun isFree(
        world: World,
        zone: GuardedZones.Zone,
        tile: Tile,
        crossingTiles: Set<Tile>,
        taken: List<Tile>,
    ): Boolean {
        if (!zone.contains(tile) || !walkable(world, tile) || tile in crossingTiles) return false
        val chunk = world.chunks.get(tile, createIfNeeded = false) ?: return false
        if (chunk.isRoofed(tile)) return false
        if (chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).isNotEmpty()) return false
        if (taken.any { it.getDistance(tile) <= 1 }) return false
        return true
    }

    /**
     * A free tile beside the run, one step inside the zone: for the end crossings of the run, step
     * inward (against [Crossing.outward]) and then sideways away from the run; fall back to the tile
     * straight inward from the run's middle for wide openings.
     */
    private fun pickSignTile(
        world: World,
        zone: GuardedZones.Zone,
        run: List<Crossing>,
        all: List<Crossing>,
        taken: List<Tile>,
    ): Pair<Tile, Direction>? {
        val crossingTiles = all.mapTo(HashSet()) { it.inside }
        val ends = if (run.size == 1) listOf(run.first()) else listOf(run.first(), run.last())
        ends.forEach { end ->
            val inward = end.inside.step(end.outward.getOpposite())
            val sideways = listOf(perpendicular(end.outward), perpendicular(end.outward).getOpposite())
            sideways.forEach { side ->
                val candidate = inward.step(side)
                if (isFree(world, zone, candidate, crossingTiles, taken)) {
                    return candidate to end.outward
                }
            }
        }
        if (run.size > 2) {
            val middle = run[run.size / 2]
            val inward = middle.inside.step(middle.outward.getOpposite())
            if (isFree(world, zone, inward, crossingTiles, taken)) return inward to middle.outward
        }
        return null
    }

    private fun perpendicular(direction: Direction): Direction =
        when (direction) {
            Direction.NORTH, Direction.SOUTH -> Direction.EAST
            else -> Direction.NORTH
        }

    /** Object rotation so the sign face looks out of the zone (revision-667 rotation 0 = south). */
    fun rotationFacing(outward: Direction): Int =
        when (outward) {
            Direction.SOUTH -> 0
            Direction.WEST -> 1
            Direction.NORTH -> 2
            else -> 3
        }
}

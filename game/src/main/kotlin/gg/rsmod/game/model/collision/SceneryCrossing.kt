package gg.rsmod.game.model.collision

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject

/**
 * Owner hard rule 2026-10-03 (buglist E17 "EEN PLAYER MAG NOOIT NOCLIPPEN", priority #1 obstacles/noclip, OB7): a crossing - a
 * forced movement or a collision-free step - may pass only the collision of the obstacle being crossed. Every step it takes that
 * the map refuses is explained here: by the obstacle itself ([GameObject] `own`, or a loc of the same name - a cliff is several
 * "Rocks" locs), by another loc (a wall, a fence, a house: that is noclip) or by the floor alone (water under a log, an
 * unwalkable cliff face: no loc to pass through).
 *
 * The movement code calls [stepBlock] / [lineBlocks] for every player crossing and counts the foreign ones on the pawn
 * (Pawn.sceneryNoclips) with a PROBLEM line; `conform traversal` fails a crossing on any of them.
 */
object SceneryCrossing {
    enum class Cause { OWN, FOREIGN_LOC, FLOOR }

    data class Block(
        val from: Tile,
        val direction: Direction,
        val cause: Cause,
        val detail: String,
        /** Every loc refusing the step is a straight wall piece (shapes 0-3): a doorway, a gate in a fence line. */
        val wallOnly: Boolean = false,
    ) {
        override fun toString(): String = "${from.x},${from.z}->${direction.name.first()} ${cause.name.lowercase()} $detail"
    }

    /**
     * Sequences that draw the player out of sight (Void tunnel_invisible 2590: the stretch of an underwall tunnel spent under the
     * ground). A slide while one of them plays passes under the scenery, not through it.
     */
    val HIDDEN_SEQUENCES = setOf(2590)

    /** How far south-west of a tile a loc's anchor may lie and still cover it (the biggest scenery in the 667 map is 5x5 and up). */
    private const val MAX_LOC_SIZE = 6

    /**
     * The cardinal steps of the straight line from [from] to [to] (a supercover walk: a diagonal is split into its two cardinal
     * parts, x first while x lags behind), so a slide is judged edge by edge the way the client draws it.
     */
    fun line(
        from: Tile,
        to: Tile,
    ): List<Pair<Tile, Direction>> {
        val steps = ArrayList<Pair<Tile, Direction>>()
        var x = from.x
        var z = from.z
        val dx = to.x - from.x
        val dz = to.z - from.z
        val nx = Math.abs(dx)
        val nz = Math.abs(dz)
        val sx = Integer.signum(dx)
        val sz = Integer.signum(dz)
        var ix = 0
        var iz = 0
        while (ix < nx || iz < nz) {
            // Advance along the axis whose next crossing comes first ((ix + 0.5) / nx vs (iz + 0.5) / nz).
            val stepX = iz >= nz || (ix < nx && (1 + 2 * ix) * nz <= (1 + 2 * iz) * nx)
            val tile = Tile(x, z, from.height)
            if (stepX) {
                steps += tile to if (sx > 0) Direction.EAST else Direction.WEST
                x += sx
                ix++
            } else {
                steps += tile to if (sz > 0) Direction.NORTH else Direction.SOUTH
                z += sz
                iz++
            }
        }
        return steps
    }

    /**
     * Every refused step on the straight line from [from] to [to]; empty when the line is open. The refused steps form runs
     * (consecutive steps of the line). A run that touches the obstacle - a step in or right next to its footprint, or a step its
     * own collision refuses - is the barrier the obstacle exists to cross: the cliff face around a cliffside, the wall over an
     * underwall tunnel, the fence a stile stands in. Any other run lies beyond open ground: a wall, fence or house the crossing
     * had no business passing ([Cause.FOREIGN_LOC]).
     */
    fun lineBlocks(
        world: World,
        from: Tile,
        to: Tile,
        own: GameObject?,
    ): List<Block> = pathBlocks(world, listOf(from, to), own)

    /** [lineBlocks] over a walked path ([points] = where it starts, then every waypoint), judged as one line. */
    fun pathBlocks(
        world: World,
        points: List<Tile>,
        own: GameObject?,
    ): List<Block> {
        val steps = points.zipWithNext().flatMap { (a, b) -> line(a, b) }
        val blocks = steps.map { (tile, dir) -> stepBlock(world, tile, dir, own) }
        val result = ArrayList<Block>()
        var i = 0
        while (i < blocks.size) {
            if (blocks[i] == null) {
                i++
                continue
            }
            var j = i
            while (j < blocks.size && blocks[j] != null) j++
            val run = blocks.subList(i, j).filterNotNull()
            val touches = run.any { it.cause == Cause.OWN } || steps.subList(i, j).any { (t, d) -> atObstacle(world, t, t.step(d), own) }
            run.forEach { b -> result += if (touches && b.cause == Cause.FOREIGN_LOC) b.copy(cause = Cause.OWN) else b }
            i = j
        }
        return result
    }

    /** True when [a] or [b] lies in or right next to [own]'s footprint. */
    private fun atObstacle(
        world: World,
        a: Tile,
        b: Tile,
        own: GameObject?,
    ): Boolean {
        if (own == null) return false
        val def = world.definitions.getNullable(ObjectDef::class.java, own.id) ?: return false
        return listOf(a, b).any { t ->
            t.height == own.tile.height &&
                t.x >= own.tile.x - 1 && t.x <= own.tile.x + def.getRotatedWidth(own) &&
                t.z >= own.tile.z - 1 && t.z <= own.tile.z + def.getRotatedLength(own)
        }
    }

    /**
     * Null when the map lets a player take the cardinal step [direction] from [from]; else what refuses it: the obstacle itself
     * (or a loc of its name), another loc, or the floor alone.
     */
    fun stepBlock(
        world: World,
        from: Tile,
        direction: Direction,
        own: GameObject?,
    ): Block? {
        if (world.collision.canStep(from, direction)) return null
        val to = from.step(direction)
        val blockers = blockers(world, from, direction)
        if (blockers.isEmpty()) return Block(from, direction, Cause.FLOOR, "floor")
        val wallOnly = blockers.all { it.type in ObjectType.LENGTHWISE_WALL.value..ObjectType.RECTANGULAR_CORNER.value }
        val ownName = own?.let { world.definitions.getNullable(ObjectDef::class.java, it.id)?.name?.lowercase() }
        val foreign =
            blockers.filterNot { obj ->
                (own != null && obj.id == own.id && obj.tile.sameAs(own.tile)) ||
                    (ownName != null && ownName.isNotBlank() && ownName != "null" &&
                        world.definitions.getNullable(ObjectDef::class.java, obj.id)?.name?.lowercase() == ownName)
            }
        return if (foreign.isEmpty()) {
            Block(from, direction, Cause.OWN, blockers.joinToString(" ") { describe(world, it) }, wallOnly)
        } else {
            Block(from, direction, Cause.FOREIGN_LOC, "into ${to.x},${to.z}: " + foreign.joinToString(" ") { describe(world, it) }, wallOnly)
        }
    }

    private fun describe(
        world: World,
        obj: GameObject,
    ): String {
        val name = world.definitions.getNullable(ObjectDef::class.java, obj.id)?.name ?: "?"
        return "${obj.id}'$name'@${obj.tile.x},${obj.tile.z}(shape ${obj.type})"
    }

    /** The locs whose own collision refuses the step: they flag leaving [from] towards [direction] or entering the next tile from behind. */
    private fun blockers(
        world: World,
        from: Tile,
        direction: Direction,
    ): List<GameObject> {
        val to = from.step(direction)
        val back = direction.getOpposite()
        val found = ArrayList<GameObject>()
        val seen = HashSet<GameObject>()
        for (anchorX in minOf(from.x, to.x) - MAX_LOC_SIZE..maxOf(from.x, to.x) + 1) {
            for (anchorZ in minOf(from.z, to.z) - MAX_LOC_SIZE..maxOf(from.z, to.z) + 1) {
                val anchor = Tile(anchorX, anchorZ, from.height)
                val chunk = world.chunks.get(anchor, createIfNeeded = false) ?: continue
                for (obj in chunk.getEntities<GameObject>(anchor, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)) {
                    if (!seen.add(obj)) continue
                    val builder = CollisionUpdate.Builder()
                    builder.setType(CollisionUpdate.Type.ADD)
                    builder.putObject(world.definitions, obj)
                    val flags = builder.build().flags
                    val leaving = flags.entries.any { (t, dirs) -> t.sameAs(from) && dirs.any { it.direction == direction } }
                    val entering = flags.entries.any { (t, dirs) -> t.sameAs(to) && dirs.any { it.direction == back } }
                    if (leaving || entering) found += obj
                }
            }
        }
        return found
    }
}

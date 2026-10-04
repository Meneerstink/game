package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject

/**
 * 2026-10-04: the handlers in agility_pyramid.plugin.kts are now Darkan's (AgilityPyramidController.java: player- / loc-relative
 * landings, no lanes). Of this file they still use STONE_BLOCKS, MOVING_BLOCKS, stairsFront, fallLanding (a Darkan drop tile that is
 * not standable here moves to the nearest usable one), TOP_VARBIT, BASE and the region ids; the lane tables (LEDGES, GAP_CROSSINGS,
 * PLANKS, JUMP_FALLS, laneOver, stairsLanding) are kept as AgilityPyramidLaneTests' map checks of the course's end and fall tiles.
 *
 * Agility Pyramid data (agility_pyramid.plugin.kts), ported 2026-10-03 from 2009scape
 * `content/global/skill/agility/pyramid/{AgilityPyramidCourse,RollingBlock,MovingBlockNPC}` (Void has no course code) and checked
 * against the OSRS Wiki pages "Agility Pyramid", "Ledge/Gap/Plank/Low wall/Stone block/Pyramid block/Doorway (Agility Pyramid)".
 *
 * Every tile below is a donor tile re-checked on this 667 map (collision dump of planes 0-3, regions 13356 and 12105, 2026-10-03;
 * guarded by AgilityPyramidLaneTests). Where the donor tile is a blocked tile of this map the nearest usable tile of the same
 * line is used and the line says so. Levels 4 and 5 of the pyramid are the top-floor copy in region 12105 (planes 2 and 3);
 * a fall from there lands in the main pyramid (2009scape transformLevel: +320, -1856, plane 3).
 */
object AgilityPyramid {
    const val REGION_MAIN = 13356
    const val REGION_TOP = 12105

    /** OSRS Wiki "Skilling success chart" low/high (out of 256, interpolated over levels 1-99) per obstacle. */
    val STONE_BLOCK_CHANCE = 168 to 320
    val LOW_WALL_CHANCE = 245 to 255
    val LEDGE_CHANCE = 181 to 320
    val PLANK_CHANCE = 166 to 340
    val GAP_CROSS_CHANCE = 180 to 308
    val GAP_JUMP_CHANCE = 185 to 329

    /** Varbits of this cache's pyramid locs (the varbit field of 10869 / 10872 / 10877-10881). */
    const val TOP_VARBIT = 1556
    const val MOVING_BLOCK_VARBIT = 1550

    /**
     * A two-ended crossing made of one or more locs: [a] and [b] are the tiles the player stands on at either end (the donor's end
     * tiles), [fall] the tile one floor down a failure lands on (the donor's fail tile, OSRS "fall down one level").
     */
    data class Lane(
        val locs: List<Tile>,
        val a: Tile,
        val b: Tile,
        val fall: Tile,
    )

    /** 2009scape LEDGE_LOCATIONS: ledges 10860 / 10886-10889. */
    val LEDGES =
        listOf(
            Lane(listOf(Tile(3364, 2851, 1), Tile(3366, 2851, 1)), Tile(3363, 2851, 1), Tile(3368, 2851, 1), Tile(3366, 2853, 0)),
            Lane(listOf(Tile(3360, 2831, 1), Tile(3362, 2831, 1)), Tile(3364, 2832, 1), Tile(3359, 2832, 1), Tile(3361, 2830, 0)),
            Lane(listOf(Tile(3372, 2837, 2), Tile(3372, 2839, 2)), Tile(3372, 2841, 2), Tile(3372, 2836, 2), Tile(3374, 2838, 1)),
            Lane(listOf(Tile(3358, 2843, 3), Tile(3358, 2845, 3)), Tile(3359, 2842, 3), Tile(3359, 2847, 3), Tile(3357, 2845, 2)),
        )

    /** 2009scape GAP_CROSS_LOCATIONS: the hand-over-hand gaps 10861-10864 / 10882-10885 (10863 / 10864 sit on the end tiles). */
    val GAP_CROSSINGS =
        listOf(
            Lane(
                listOf(Tile(3368, 2831, 1), Tile(3370, 2831, 1), Tile(3372, 2832, 1), Tile(3367, 2832, 1)),
                Tile(3372, 2832, 1), Tile(3367, 2832, 1), Tile(3370, 2830, 0),
            ),
            Lane(
                listOf(Tile(3356, 2837, 2), Tile(3356, 2839, 2), Tile(3357, 2836, 2), Tile(3357, 2841, 2)),
                Tile(3357, 2836, 2), Tile(3357, 2841, 2), Tile(3355, 2838, 1),
            ),
            Lane(
                listOf(Tile(3360, 2849, 2), Tile(3362, 2849, 2), Tile(3359, 2849, 2), Tile(3364, 2849, 2)),
                Tile(3359, 2849, 2), Tile(3364, 2849, 2), Tile(3362, 2851, 1),
            ),
        )

    /**
     * Planks 10867 / 10868: the plank's two end locs stand on walkable tiles and the plank runs five tiles between them
     * (2009scape handlePlank, "object.transform(dir, 5)"). The region-12105 plane-1 pair is an unreachable copy of the map.
     */
    val PLANKS =
        listOf(
            Lane(listOf(Tile(3375, 2845, 1), Tile(3375, 2840, 1)), Tile(3375, 2845, 1), Tile(3375, 2840, 1), Tile(3377, 2843, 0)),
            Lane(listOf(Tile(3370, 2835, 3), Tile(3365, 2835, 3)), Tile(3370, 2835, 3), Tile(3365, 2835, 3), Tile(3368, 2833, 2)),
        )

    /** Jump gaps 10859 (2x2): the crossing is the straight lane over the loc; the fail tile is 2009scape handleJumpGap's. */
    val JUMP_FALLS =
        mapOf(
            Tile(3356, 2847, 2) to Tile(3355, 2848, 1),
            Tile(3364, 2833, 2) to Tile(3364, 2832, 1),
            Tile(3370, 2841, 3) to Tile(3372, 2841, 2),
            Tile(3040, 4697, 2) to Tile(3359, 2842, 3),
            Tile(3048, 4695, 2) to Tile(3370, 2839, 3),
            Tile(3046, 4697, 3) to Tile(3048, 4697, 2),
        )

    /**
     * Stone blocks (2009scape RollingBlock.BlockSets): the 2x2 block a player rolls over and the course direction across it. On
     * this 667 map the block tiles are blocked, so the roll starts on the tile right in front of the block and lands right
     * behind it (three tiles), forwards along [forward] or backwards against it. [varbit] = the block loc's varbit (rolled = 1).
     */
    data class StoneBlock(
        val corner: Tile,
        val forward: Direction,
        val varbit: Int,
    ) {
        private val axisX get() = forward == Direction.EAST || forward == Direction.WEST

        /** The two lanes over the block, each as (near tile, far tile) in the forward direction. */
        val lanes: List<Pair<Tile, Tile>>
            get() =
                (0..1).map { i ->
                    val x = if (axisX) 0 else i
                    val z = if (axisX) i else 0
                    // The tile right before the 2x2 block: one step back from its near edge (north/east) or past its far edge.
                    val inFront = corner.transform(x, z).add(forward, if (forward == Direction.EAST || forward == Direction.NORTH) -1 else -2)
                    inFront to inFront.add(forward, 3)
                }
    }

    val STONE_BLOCKS =
        listOf(
            StoneBlock(Tile(3354, 2841, 1), Direction.NORTH, 1551),
            StoneBlock(Tile(3374, 2835, 1), Direction.SOUTH, 1553),
            StoneBlock(Tile(3368, 2849, 2), Direction.EAST, 1552),
            StoneBlock(Tile(3048, 4699, 2), Direction.SOUTH, 1554),
            StoneBlock(Tile(3044, 4699, 3), Direction.EAST, 1555),
        )

    /**
     * The two moving pyramid blocks (NPCs 3124 / 3125, spawned in the recess beside the walkway): they slide two tiles across the
     * walkway ([push]) and back. OSRS Wiki "Pyramid block": a 15-tick cycle, obstructing for 5 ticks; the two blocks are not
     * synchronised. A player on the walkway tiles while the block is out is pushed off to [fall] one floor down (2009scape
     * MovingBlockNPC.checkBlock destinations, transformLevel).
     */
    data class MovingBlock(
        val spawn: Tile,
        val push: Direction,
        val offset: Int,
    ) {
        val out: Tile get() = spawn.add(push, 2)

        /** The walkway tiles the extended block covers. */
        val tiles: List<Tile> get() = (0..1).flatMap { dx -> (0..1).map { dz -> out.transform(dx, dz) } }

        fun fall(from: Tile): Tile =
            if (push == Direction.NORTH) Tile(from.x, out.z + 2, from.height - 1) else Tile(out.x + 2, from.z, from.height - 1)
    }

    val MOVING_BLOCKS =
        listOf(
            MovingBlock(Tile(3366, 2845, 3), Direction.NORTH, 0),
            MovingBlock(Tile(3372, 2847, 1), Direction.EAST, 7),
        )

    /** Where the doorway at the top leads (2009scape: "It leads to the base of the pyramid", 3364,2830). */
    val BASE = Tile(3364, 2830, 0)

    /** Level 3 -> 4: the main pyramid's top staircase leads into the region-12105 copy and back (2009scape handleStairs). */
    val TOP_STAIRS_UP = Tile(3360, 2837, 3) to Tile(3040, 4695, 2)
    val TOP_STAIRS_DOWN = Tile(3040, 4693, 2) to Tile(3360, 2836, 3)

    fun lane(
        lanes: List<Lane>,
        loc: Tile,
    ): Lane? = lanes.firstOrNull { l -> l.locs.any { it.sameAs(loc) } }

    /** A tile a player may land on: not blocked itself and opening onto walkable ground ([isUsableLanding]). */
    fun usable(
        world: World,
        tile: Tile,
    ): Boolean = !world.collision.isClipped(tile) && isUsableLanding(world, tile)

    /**
     * The straight lane over a low wall / jump gap: across the loc's short axis (both axes for a square loc: the side the player
     * stands on), lanes nearest the player first, both ends [usable]. Null when there is none.
     */
    fun laneOver(
        world: World,
        obj: GameObject,
        from: Tile,
    ): Crossing? {
        val def = obj.getDef(world.definitions)
        val w = def.getRotatedWidth(obj)
        val l = def.getRotatedLength(obj)
        val x0 = obj.tile.x
        val z0 = obj.tile.z
        val h = obj.tile.height
        val outX = if (from.x < x0) x0 - from.x else if (from.x >= x0 + w) from.x - (x0 + w - 1) else 0
        val outZ = if (from.z < z0) z0 - from.z else if (from.z >= z0 + l) from.z - (z0 + l - 1) else 0
        val alongX =
            when {
                w < l -> true
                l < w -> false
                else -> outX >= outZ && outX > 0
            }
        if (alongX) {
            val east = from.x * 2 < x0 * 2 + w - 1
            for (z in (z0 until z0 + l).sortedBy { Math.abs(it - from.z) }) {
                val start = Tile(if (east) x0 - 1 else x0 + w, z, h)
                val landing = Tile(if (east) x0 + w else x0 - 1, z, h)
                if (usable(world, start) && usable(world, landing)) return Crossing(start, landing, if (east) Direction.EAST else Direction.WEST)
            }
        } else {
            val north = from.z * 2 < z0 * 2 + l - 1
            for (x in (x0 until x0 + w).sortedBy { Math.abs(it - from.x) }) {
                val start = Tile(x, if (north) z0 - 1 else z0 + l, h)
                val landing = Tile(x, if (north) z0 + l else z0 - 1, h)
                if (usable(world, start) && usable(world, landing)) return Crossing(start, landing, if (north) Direction.NORTH else Direction.SOUTH)
            }
        }
        return null
    }

    /** The landing of a fall: the sourced tile when usable, else the nearest usable tile within 4 on that plane, else null. */
    fun fallLanding(
        world: World,
        tile: Tile,
    ): Tile? {
        if (usable(world, tile)) return tile
        for (r in 1..4) {
            val ring =
                (-r..r).flatMap { dx -> (-r..r).map { dz -> tile.transform(dx, dz) } }
                    .filter { Math.max(Math.abs(it.x - tile.x), Math.abs(it.z - tile.z)) == r }
                    .sortedBy { (it.x - tile.x) * (it.x - tile.x) + (it.z - tile.z) * (it.z - tile.z) }
            ring.firstOrNull { usable(world, it) }?.let { return it }
        }
        return null
    }

    /** Stairs 10857 (up) / 10858 (down), 2x2: the landing two tiles past the north side one floor up, or right south one floor down. */
    fun stairsLanding(
        world: World,
        obj: GameObject,
        from: Tile,
        up: Boolean,
    ): Tile? {
        if (up && obj.tile.sameAs(TOP_STAIRS_UP.first)) return TOP_STAIRS_UP.second
        if (!up && obj.tile.sameAs(TOP_STAIRS_DOWN.first)) return TOP_STAIRS_DOWN.second
        val h = obj.tile.height + if (up) 1 else -1
        if (h !in 0..3) return null
        val z = if (up) obj.tile.z + 2 else obj.tile.z - 1
        // The player's own column first (2009scape: the loc's x, which is a blocked tile below 10858 at 3356,2833,2).
        val xs = listOf(from.x.coerceIn(obj.tile.x, obj.tile.x + 1), obj.tile.x, obj.tile.x + 1).distinct()
        return xs.map { Tile(it, z, h) }.firstOrNull { usable(world, it) }
    }

    /** The tile in front of a 2x2 staircase the climb starts from (south for 10857 going up, north for 10858 going down). */
    fun stairsFront(
        world: World,
        obj: GameObject,
        from: Tile,
        up: Boolean,
    ): Tile? {
        val h = obj.tile.height
        val z = if (up) obj.tile.z - 1 else obj.tile.z + 2
        val xs = listOf(from.x.coerceIn(obj.tile.x, obj.tile.x + 1), obj.tile.x, obj.tile.x + 1).distinct()
        return xs.map { Tile(it, z, h) }.firstOrNull { usable(world, it) }
    }
}

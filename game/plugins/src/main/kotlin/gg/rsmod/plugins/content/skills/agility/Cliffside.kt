package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.message

/**
 * The 2011 Trollheim "Cliffside" boulders (9303 easy, 9304 medium, 9305 advanced, 9306 hard, 3803), owner priority #1 report
 * 154847 "character still seems to noclip when crossing obstacles" at 9305 (2894,3672).
 *
 * Root cause (measured 2026-10-04 in the agent's own client, frame sheets in the handoff): every earlier fix slid or walked the body
 * through the boulder at ground height - climb-down 1148 (no body lift at all) over a 4-tile slide, or the "climbing" walk - while
 * the boulder (model 64352/64353, 3x4) rises up to 711 units above the high side. Only the head stayed above the rock. No donor has
 * this crossing: in revision 634 (Void, 2009scape) these ids are 1x1 "Rocks" ground decorations on other tiles, and no 667/718 donor
 * implements the Cliffsides.
 *
 * The revision-667 map itself fixes the crossing (OpenRS2 #1473, identical to our cache): each Cliffside has a 4-tile lane between
 * two wall pieces, 11449 "Rocks" on the low end and 11450 "Rocks" on the high end, one storey (960 units) apart - the same on all five
 * placements. Jagex's climbs for exactly that lane carry the body over the rock themselves (whole-body translation measured with
 * tools/cache667/seqlift.py): 3393 rises 960 while moving 2048 (4 tiles) in 180 cycles, 15239 descends 975 over 4.5 tiles in 120
 * cycles. Seen in the agent's own client: the body climbs the stepped face and crosses the top, never inside the rock. The tile
 * moves in the animation's last cycle (one exact move held to that cycle), so the server tile and the drawn body meet at the end.
 */
object Cliffside {
    const val ANIM_UP = 3393
    const val UP_CYCLES = 180
    const val ANIM_DOWN = 15239
    const val DOWN_CYCLES = 120

    /** The wall piece on the lane's low end / high end. */
    const val LANE_LOW = 11449
    const val LANE_HIGH = 11450

    /** Every Cliffside loc of this map with its Agility level (RuneScape Wiki infobox per object; 3803 has no requirement). */
    val LEVELS = mapOf(9303 to 41, 9304 to 43, 9305 to 44, 9306 to 47, 3803 to 0)

    /** Experience per climb (RuneScape Wiki: 8 for every Trollheim Cliffside). */
    const val XP = 8.0

    data class Lane(
        val low: Tile,
        val high: Tile,
    )

    /** The lane of [obj]: the 11449 / 11450 wall pieces on the ring of tiles around its footprint. Null when the map has none. */
    fun lane(
        world: gg.rsmod.game.model.World,
        obj: GameObject,
    ): Lane? {
        val def = obj.getDef(world.definitions)
        val w = def.getRotatedWidth(obj)
        val l = def.getRotatedLength(obj)
        var low: Tile? = null
        var high: Tile? = null
        for (x in obj.tile.x - 1..obj.tile.x + w) {
            for (z in obj.tile.z - 1..obj.tile.z + l) {
                val inside = x >= obj.tile.x && x < obj.tile.x + w && z >= obj.tile.z && z < obj.tile.z + l
                if (inside) continue
                val t = Tile(x, z, obj.tile.height)
                if (low == null && locAt(world, t, LANE_LOW) != null) low = t
                if (high == null && locAt(world, t, LANE_HIGH) != null) high = t
            }
        }
        return if (low != null && high != null) Lane(low, high) else null
    }

    /** True when [from] is nearer the high end of [lane]: the climb goes down. */
    fun goesDown(
        lane: Lane,
        from: Tile,
    ): Boolean = from.getDistance(lane.high) < from.getDistance(lane.low)
}

/**
 * One Cliffside climb: walk to the lane end on the player's side, face across, then Jagex's climb animation carries the body over
 * the boulder and the tile follows in its last cycle. Returns false (with a PROBLEM trace and a message) when the map has no lane.
 */
suspend fun QueueTask.climbCliffside(
    player: Player,
    target: GameObject,
): Boolean {
    val lane = Cliffside.lane(player.world, target)
    if (lane == null) {
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
            "cliffside ${target.id} at ${target.tile.x},${target.tile.z}: no 11449/11450 lane walls on this map"
        }
        player.message("You can't climb the rocks from here.")
        return false
    }
    val down = Cliffside.goesDown(lane, player.tile)
    val start = if (down) lane.high else lane.low
    val landing = if (down) lane.low else lane.high
    walkToTile(player, start)
    val direction = Direction.between(start, landing)
    face(player, direction)
    wait(1)
    val cycles = if (down) Cliffside.DOWN_CYCLES else Cliffside.UP_CYCLES
    player.animate(if (down) Cliffside.ANIM_DOWN else Cliffside.ANIM_UP)
    exactMove(player, landing, delay = cycles, direction = direction, startDelay = cycles - 1)
    return true
}

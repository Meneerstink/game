package gg.rsmod.plugins.content.mechanics.traversal

import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.skills.agility.*

/*
 * N462 (owner 2026-11-07 'om daar naar toe te komen ... net zoals osrs'): the Necropolis stepping stones between the fairy ring AKP
 * island and the Necropolis path. OSRS Wiki Stepping stone (Necropolis): option "Cross", "It requires 62 Agility to use the
 * shortcut", ids 43989/43990; Wiki Shortcuts: Agility 62, 0 XP. Placement (cache 2686 square 13098, imported 'necropolis-west'):
 * 43989 at 3291,2700 + 3293,2700 inside an east-west lane of invisible walls (z 2699/2701, x 3290-3294), 43990 at 3295,2706 inside a
 * north-south lane (x 3294/3296, z 2705-2707); agility_shortcut_icon at 3292,2700 and 3294,2706 = two crossings.
 * ONZEKER: the landing tiles (lane ends, walkable in the source terrain) and the per-stone jump; the jump is the shared stepping-stone
 * jump 741 + exact move of the other stone shortcuts (muspah_arposandra.plugin.kts) - no OSRS trace of this crossing.
 */
val NECROPOLIS_STONE_JUMP = 741
val NECROPOLIS_STONE_LEVEL = 62

/** A crossing: the two lane ends and the stones between them, west->east / south->north. */
data class StoneLane(val a: Tile, val b: Tile, val stones: List<Tile>)

val NECROPOLIS_LANES = listOf(
    StoneLane(Tile(3289, 2700, 0), Tile(3295, 2700, 0), listOf(Tile(3291, 2700, 0), Tile(3293, 2700, 0))),
    StoneLane(Tile(3295, 2704, 0), Tile(3295, 2708, 0), listOf(Tile(3295, 2706, 0))),
)

listOfNotNull(ToaIds.loc(43989), ToaIds.loc(43990)).distinct().forEach { stone ->
    shortcut(stone, "Cross", lineOfSightDistance = 3) {
        obstacle { target ->
            val lane = NECROPOLIS_LANES.firstOrNull { l -> l.stones.any { it.sameAs(target.tile) } } ?: return@obstacle
            if (!player.hasAgility(NECROPOLIS_STONE_LEVEL)) return@obstacle
            val fromA = player.tile.getDistance(lane.a) <= player.tile.getDistance(lane.b)
            val start = if (fromA) lane.a else lane.b
            val path = (if (fromA) lane.stones else lane.stones.reversed()) + (if (fromA) lane.b else lane.a)
            walkToTile(player, start)
            var here = start
            for (next in path) {
                val direction = Direction.between(here, next)
                face(player, direction)
                wait(1)
                player.animate(NECROPOLIS_STONE_JUMP)
                exactMove(player, next, delay = 45, direction = direction, startDelay = 30)
                here = next
            }
        }
    }
}

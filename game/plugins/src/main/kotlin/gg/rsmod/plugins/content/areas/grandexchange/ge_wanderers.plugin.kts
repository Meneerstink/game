package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.plugins.content.mechanics.npcwalk.NpcPatrol

/*
 * Owner 2026-09-24: "importeer deze 3 npcs zet ze in grandexchange kijk mischien hebben we ze al ... laat ze rondjes lopen":
 * Workman (wheelbarrow), Jalal the Drunk and the Tramp. All three are already in the revision-667 cache:
 * - Workman 5952: OSRS "Workman (wheelbarrow)" (OSRS ids 1929-1932, 667 ids 5952-5955 - size 2 with the wheelbarrow walk set,
 *   BAS 532, no options: "players cannot interact"). OSRS: he "moves at running speed", so he runs his loop.
 * - Jalal the Drunk 1863: OSRS renamed Drunken Ali to "Jalal the Drunk" (NPC ids 3534, 11872); the 667 definition is renamed in
 *   the cache (NpcRenameTool) and keeps his transcript dialogue.
 * - Tramp 11: OSRS "Tramp" (Talk-to, "A man down on his luck", wanders), with his transcript dialogue.
 *
 * They walk loops around the Grand Exchange's centre (3164,3490) through the open floor between the central counter (x 3157-3172,
 * z 3486-3497: the four 3x3 booth corners 47119) and the outer colonnade, each on its own ring so they do not bunch up.
 */

/** Eight points on a ring of [r] tiles around the exchange centre, starting at [start] and going [clockwise] or not. */
fun ring(
    r: Int,
    start: Int,
    clockwise: Boolean,
): List<Tile> {
    val cx = 3164
    val cz = 3490
    val d = (r * 7) / 10
    val points = listOf(cx to cz - r, cx - d to cz - d, cx - r to cz, cx - d to cz + d, cx to cz + r, cx + d to cz + d, cx + r to cz, cx + d to cz - d)
    // south -> west -> north -> east is clockwise seen from above (north up)
    val ordered = if (clockwise) points else points.reversed()
    return (ordered.indices).map { ordered[(it + start) % ordered.size] }.map { (x, z) -> Tile(x, z, 0) }
}

data class Wanderer(val npc: Int, val route: List<Tile>, val run: Boolean)

val WANDERERS =
    listOf(
        Wanderer(5952, ring(r = 11, start = 0, clockwise = true), run = true), // Workman (wheelbarrow)
        Wanderer(11, ring(r = 9, start = 4, clockwise = false), run = false), // Tramp
        Wanderer(1863, ring(r = 8, start = 2, clockwise = true), run = false), // Jalal the Drunk
    )

WANDERERS.forEach { w ->
    val spawn = w.route.first()
    NpcPatrol.register(spawn, NpcPatrol.Route(w.route, run = w.run))
    spawn_npc(npc = w.npc, x = spawn.x, z = spawn.z, walkRadius = 0)
}

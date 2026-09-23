package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile

/**
 * RCV-012 B1 evidence (reports only, never aborts boot): owner live - the whole Nex room and the area around the Nex bank are
 * stuck without noclip. Flood-fills the REAL server collision from the Nex entry tile and Ashuelot Reis (the Nex bank) with the
 * same per-step rule players use (MovementQueue.canStep: canTraverse out of the tile) and prints a map of level 0:
 * 'S' seed, '.' reachable, '#' every cardinal step blocked, 'o' open but unreachable from the seeds.
 */
on_world_init {
    val minX = 2860
    val maxX = 2950
    val minZ = 5180
    val maxZ = 5240
    val seeds = listOf(NexEncounter.ENTRY_TILE, Tile(2902, 5206, 0), NexEncounter.NEX_SPAWN)
    val reachable = hashSetOf<Tile>()
    val queue = ArrayDeque<Tile>()
    seeds.forEach { if (reachable.add(it)) queue.add(it) }
    while (queue.isNotEmpty()) {
        val tile = queue.removeFirst()
        Direction.NESW.forEach { dir ->
            val next = tile.step(dir)
            if (next.x in minX..maxX && next.z in minZ..maxZ && next !in reachable &&
                world.collision.canTraverse(tile, dir, projectile = false, water = false)
            ) {
                reachable.add(next)
                queue.add(next)
            }
        }
    }
    val solid = { t: Tile -> Direction.NESW.all { world.collision.isBlocked(t, it, projectile = false) } }
    println("nex_collision_probe: reachable=${reachable.size} from seeds=$seeds (x $minX-$maxX, z $minZ-$maxZ, level 0)")
    for (z in maxZ downTo minZ) {
        val row = StringBuilder()
        for (x in minX..maxX) {
            val t = Tile(x, z, 0)
            row.append(
                when {
                    seeds.any { it.x == x && it.z == z } -> 'S'
                    t in reachable -> '.'
                    solid(t) -> '#'
                    else -> 'o'
                },
            )
        }
        println("nex_collision_probe: z=$z $row")
    }
}

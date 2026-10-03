package gg.rsmod.plugins.content.mechanics.traversal.batch2

import gg.rsmod.plugins.content.skills.agility.Crossing
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.isUsableLanding
import gg.rsmod.plugins.content.skills.agility.locAt
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortAxisCrossing
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkToTile

/**
 * Isafdar forest obstacles (Tirannwn - reachable: the Port Tyras charter ship, areas/portsarim/Ports.kt PORT_TYRAS_TILE), obstacle
 * batch 2, owner priority #1 2026-10-03: Dense forest 3937 (climb over) and 3938 / 3939 / 3998 / 3999 (squeeze through), the stick
 * traps 3922 ("Pass") and the leaf pits 3923 / 3924 / 3925 ("Jump").
 *
 * Source: 2009scape RovingElvesObstacles.java (no Void / Novite port; the OSRS Wiki lists the same Isafdar dense forest, leaf trap and
 * stick trap obstacles): animations OVER 839 (3937), THROUGH 1237 (other dense forest), STICK_TRAP 819, LEAF_TRAP 1115; messages
 * "You manage to skillfully pass the trap." and "You safely jump across.". No level, no experience in the source.
 *
 * The donor slid from fixed offsets of the loc (forceWalk from a teleported start). Here every crossing is a straight lane read from
 * this cache's collision (ShortcutSupport.shortAxisCrossing: Sticks 3x1, Dense forest 3x2), from the tile right in front of the loc to
 * the usable tile right behind it, after a normal walk to that start tile (owner E17: never a slide through scenery). A leaf pit is a
 * 3x3 of 1x1 leaf locs around one centre 3924: the jump clears the whole pit, from the tile in front of it to the tile behind it.
 */

val ANIM_OVER = 839
val ANIM_THROUGH = 1237
val ANIM_STICK_TRAP = 819
val ANIM_LEAF_TRAP = 1115

fun crossLoc(
    id: Int,
    option: String,
    anim: Int,
    success: String?,
) = shortcut(id, option) {
    obstacle { target ->
        val crossing = shortAxisCrossing(world, target, player.tile)
        if (crossing == null) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "isafdar $id at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
            }
            player.message("You can't get past from here.")
            return@obstacle
        }
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        wait(1)
        player.animate(anim)
        exactMove(player, crossing.landing, delay = maxOf(60, crossing.start.getDistance(crossing.landing) * 30), direction = crossing.direction)
        success?.let { player.message(it) }
    }
}

crossLoc(3937, "Enter", ANIM_OVER, null)
listOf(3938, 3939, 3998, 3999).forEach { crossLoc(it, "Enter", ANIM_THROUGH, null) }
crossLoc(3922, "Pass", ANIM_STICK_TRAP, "You manage to skillfully pass the trap.")

/** The leaf pit around [clicked]: its centre 3924 (the loc itself or one tile away), else null. */
fun leafPitCentre(clicked: GameObject): Tile? {
    if (clicked.id == 3924) return clicked.tile
    for (dx in -1..1) for (dz in -1..1) {
        val t = clicked.tile.transform(dx, dz)
        if (locAt(world, t, 3924) != null) return t
    }
    return null
}

/** Straight over the 3x3 pit along the axis the player stands off, in his own lane clamped to the pit. */
fun leafCrossing(centre: Tile, from: Tile): Crossing? {
    val outX = Math.abs(from.x - centre.x) - 1
    val outZ = Math.abs(from.z - centre.z) - 1
    val alongX = outX >= outZ && outX > 0
    if (!alongX && outZ <= 0) return null
    val lanes =
        if (alongX) (-1..1).sortedBy { Math.abs(centre.z + it - from.z) } else (-1..1).sortedBy { Math.abs(centre.x + it - from.x) }
    for (lane in lanes) {
        val crossing =
            if (alongX) {
                val east = from.x < centre.x
                Crossing(Tile(centre.x + if (east) -2 else 2, centre.z + lane, centre.height), Tile(centre.x + if (east) 2 else -2, centre.z + lane, centre.height), if (east) Direction.EAST else Direction.WEST)
            } else {
                val north = from.z < centre.z
                Crossing(Tile(centre.x + lane, centre.z + if (north) -2 else 2, centre.height), Tile(centre.x + lane, centre.z + if (north) 2 else -2, centre.height), if (north) Direction.NORTH else Direction.SOUTH)
            }
        if (isUsableLanding(world, crossing.start) && isUsableLanding(world, crossing.landing)) return crossing
    }
    return null
}

listOf(3923, 3924, 3925).forEach { id ->
    shortcut(id, "Jump") {
        obstacle { target ->
            val centre = leafPitCentre(target)
            val crossing = centre?.let { leafCrossing(it, player.tile) }
            if (crossing == null) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                    "isafdar leaves $id at ${target.tile.x},${target.tile.z}: no lane over the pit from ${player.tile.x},${player.tile.z}"
                }
                player.message("You can't jump across from here.")
                return@obstacle
            }
            walkToTile(player, crossing.start)
            face(player, crossing.direction)
            wait(1)
            player.animate(ANIM_LEAF_TRAP)
            exactMove(player, crossing.landing, delay = 90, direction = crossing.direction)
            player.message("You safely jump across.")
        }
    }
}

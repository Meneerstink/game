package gg.rsmod.plugins.content.mechanics.traversal.batch5

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.content.activity.soul_wars.SW_TEAM
import gg.rsmod.plugins.content.activity.soul_wars.SoulWarsTeam
import gg.rsmod.plugins.content.skills.agility.*

/*
 * Soul Wars barriers (obstacle batch 5, owner priority #1 2026-10-03). The match (activity/soul_wars) starts every player in his
 * team's spawn room (SoulWarsData.BLUE_RESPAWN_AREA 1817-1822,3222-3228 / RED_RESPAWN_AREA 1952-1957,3236-3242), whose only way
 * into the base is the team barrier - unbound until now, so a player could not leave the spawn room at all.
 *
 * Each barrier is a wall loc (type 0) on one tile edge of this 667 map (MapProbe 2026-10-03):
 *  - 42015 Blue barrier 1815,3225 rot 2 (east edge): 1816,3225 (spawn room) <-> 1815,3225 (base);
 *  - 42018 Red barrier 1959,3239 rot 0 (west edge): 1958,3239 (spawn room) <-> 1959,3239 (base);
 *  - 42019 Neutral barrier 1933,3243 rot 1 (north edge): 1933,3244 (east graveyard pen) <-> 1933,3243;
 *  - 42020 Neutral barrier 1842,3220 rot 3 (south edge): 1842,3219 (west graveyard pen) <-> 1842,3220.
 * The pass is the one bounded step across that edge (the shared door step: walk with collision to the tile on the player's side,
 * then one step), never a slide from wherever the click left the player. The team barriers let only their own team through
 * (SW_TEAM, set when the match starts); the graveyard barriers have no ownership here (this server respawns at the base only).
 * Not sourced, so not invented: the refusal message for the enemy team (traced as a PROBLEM line instead).
 */

val BLUE_BARRIER = 42015
val RED_BARRIER = 42018
val EAST_GRAVEYARD_BARRIER = 42019
val WEST_GRAVEYARD_BARRIER = 42020

/** The tile on the other side of a wall loc's edge: rotation 0 west, 1 north, 2 east, 3 south (the 667 wall convention). */
fun beyond(wall: GameObject): Tile =
    when (wall.rot) {
        0 -> wall.tile.add(Direction.WEST)
        1 -> wall.tile.add(Direction.NORTH)
        2 -> wall.tile.add(Direction.EAST)
        else -> wall.tile.add(Direction.SOUTH)
    }

suspend fun QueueTask.passEdge(
    player: Player,
    wall: GameObject,
) {
    val own = Tile(wall.tile.x, wall.tile.z, wall.tile.height)
    val other = beyond(wall)
    // The side the player stands on is the start; the other tile of the edge is the landing.
    val (start, landing) = if (player.tile.getDistance(own) <= player.tile.getDistance(other)) own to other else other to own
    walkToTile(player, start)
    player.faceTile(landing)
    walkOverTile(player, landing, Render.NONE)
}

on_world_init {
    mapOf(BLUE_BARRIER to SoulWarsTeam.BLUE, RED_BARRIER to SoulWarsTeam.RED).forEach { (id, team) ->
        shortcut(id, "Pass") {
            if (player.attr[SW_TEAM] != team) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                    "soul wars barrier $id: ${player.username} (team ${player.attr[SW_TEAM]}) refused - not the $team team"
                }
                return@shortcut
            }
            obstacle { passEdge(player, it) }
        }
    }
    listOf(EAST_GRAVEYARD_BARRIER, WEST_GRAVEYARD_BARRIER).forEach { id ->
        shortcut(id, "Pass") { obstacle { passEdge(player, it) } }
    }
}

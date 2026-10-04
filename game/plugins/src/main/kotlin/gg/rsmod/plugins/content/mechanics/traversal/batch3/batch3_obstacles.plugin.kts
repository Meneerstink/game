package gg.rsmod.plugins.content.mechanics.traversal.batch3

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Trace
import gg.rsmod.plugins.content.mechanics.ladders.GenericLadders
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.climbOver
import gg.rsmod.plugins.content.skills.agility.darkanHandleObstacle
import gg.rsmod.plugins.content.skills.agility.darkanTransform
import gg.rsmod.plugins.content.skills.agility.exactMove
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.hasAgility
import gg.rsmod.plugins.content.skills.agility.locAt
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortAxisCrossing
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.straightCrossing
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

/**
 * Obstacle batch 3 (owner priority #1 2026-10-03, agent-handoffs/obstacle-batches/open-batch-3.tsv): the reachable traversal locs of
 * the batch that are more than a plain teleport. Every crossing starts with a normal walk to the tile in front of the loc and is a
 * forced move to the usable tile behind it (owner E17: never a collision-free walk through scenery). Sources per block.
 */

// ---- God Wars Dungeon, Ancient Prison obstacle pipe (region 11345) ------------------------------------------------------------
// The 667 map's Obstacle pipe 57234 (2860,5219, 2x1) is not interactive; the clickable parts are its two "Tunnel entrance" wall locs
// 31221 at (2860,5219) and (2861,5219) (census placements_667.tsv), so godwars_dungeon.plugin.kts's binding on 57234 is never reached.
// Novite 667 (GodWars.java, ported in godwars_dungeon.plugin.kts): 70 Agility, animation 1133. The lane is the pipe's own row read
// from this cache's collision (straightCrossing over the pipe loc), from the tile in front of one end to the tile beyond the other.
// 31221 is a generic 2011 tunnel-entrance wall loc used in several regions (TzHaar, region 8280, region 15961): every other placement
// falls through to the object-teleport table.
val PRISON_PIPE = Tile(2860, 5219, 0)

shortcut(31221, "Go-through-wall") {
    val clicked = player.getInteractingGameObj()
    // The three wall locs on the front tiles of the Fight Caves entrance 9356 (batch 8 map probe): the same entry as the cave.
    if (clicked.tile.height == 0 && clicked.tile.z == 5167 && clicked.tile.x in 2437..2439) {
        world.plugins.executeObject(player, Objs.CAVE_ENTRANCE_9356, 1)
        return@shortcut
    }
    // The arena-side faces of Nex's landslide (2910,5201-5205, batch 12): the prison has no way out but death or a teleport - Novite
    // NexEntrance.java's own words, no movement.
    if (clicked.tile.height == 0 && clicked.tile.x == 2910 && clicked.tile.z in 5201..5205) {
        player.queue {
            messageBox("The room beyond this point is a prison!", "There is no way out other than death or teleport.", "Only those who endure dangerous encounters should proceed.")
        }
        return@shortcut
    }
    if (clicked.tile.height != 0 || clicked.tile.z != 5219 || clicked.tile.x !in 2860..2861) {
        ObjectTeleports.fallback(player)
        return@shortcut
    }
    if (!player.hasAgility(70, "You need an Agility level of 70 to squeeze through the pipe.")) return@shortcut
    obstacle {
        val pipe = locAt(world, PRISON_PIPE, 57234)
        val crossing = pipe?.let { straightCrossing(world, it, player.tile) }
        if (crossing == null) {
            Trace.problem(Trace.Cat.PLUGIN) { "ancient prison pipe: no lane from ${player.tile.x},${player.tile.z}" }
            player.message("You can't squeeze through from here.")
            return@obstacle
        }
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        wait(1)
        player.animate(1133)
        exactMove(player, crossing.landing, delay = crossing.start.getDistance(crossing.landing) * 30, direction = crossing.direction)
    }
}

// ---- Clan Wars free-for-all arenas: ladders 38761 "Climb" (regions 11351 / 12119) -------------------------------------------------
// Reachable through the FFA portals (areas/wilderness/clan_wars_ffa.plugin.kts). The only option is "Climb", which the generic ladder
// fallback does not take as a direction; the 667 map has the counterpart 38762 "Climb-down" on the same tile one floor up at all six
// placements (census), so the landing is GenericLadders' map-proven stand tile beside it. Void Stairs.kt: climb_up 828, two ticks.
shortcut(38761, "Climb") {
    val ladder = player.getInteractingGameObj()
    val destination = GenericLadders.destination(world, ladder, player.tile, GenericLadders.Direction.UP)
    if (destination == null) {
        Trace.problem(Trace.Cat.PLUGIN) { "ffa ladder 38761 at ${ladder.tile.x},${ladder.tile.z}: no counterpart on the floor above" }
        player.message("Nothing interesting happens.")
        return@shortcut
    }
    player.lockingQueue(lockState = LockState.FULL) {
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.moveTo(destination)
    }
}

// ---- Kuradal's dungeon low wall: the side segments 47234 (1634,5293) / 47235 (1632,5293) -------------------------------------------
// The wall is three 1x1 locs; sourced_shortcuts.plugin.kts binds the middle one, 47233 (1633,5293), from Void KuradalsDungeon.kt. Its
// two neighbours are the same wall: same rule - 86 Agility, the climb from (1633,5292) to (1633,5294) or back, rocks_pile_climb 839,
// a 30-cycle slide. Here the start tile is reached by a normal walk.
listOf(47234, 47235).forEach { id ->
    shortcut(id, "Climb-over") {
        if (!player.hasAgility(86)) return@shortcut
        obstacle { target ->
            val north = player.tile.z < target.tile.z
            val start = if (north) Tile(1633, 5292) else Tile(1633, 5294)
            val end = if (north) Tile(1633, 5294) else Tile(1633, 5292)
            val direction = if (north) Direction.NORTH else Direction.SOUTH
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            player.animate(839)
            exactMove(player, end, 30, direction)
        }
    }
}

// ---- Fremennik Province log balance, middle piece 9323 (2722,3594) --------------------------------------------------------------
// The log is 9324 (2722,3593) - 9323 - 9322 (2722,3595); sourced_shortcuts.plugin.kts binds the two ends (2009scape
// LogBalanceShortcut: 48 Agility, 4 xp, from (2722,3592) to (2722,3596) or back, rope-balance render, the same two messages). The
// middle piece is the same log.
shortcut(9323, "Walk-across") {
    if (!player.hasAgility(48, "You need an Agility level of 48 to cross this log.")) return@shortcut
    obstacle {
        val north = player.tile.z <= 3594
        val start = if (north) Tile(2722, 3592) else Tile(2722, 3596)
        val dest = if (north) Tile(2722, 3596) else Tile(2722, 3592)
        walkToTile(player, start)
        player.filterableMessage("You walk carefully across the slippery log...")
        walkOverTile(player, dest, Render.ROPE_BALANCE)
        player.resetRenderAnimation()
        player.addXp(Skills.AGILITY, 4.0)
        player.filterableMessage("... and make it safely to the other side.")
    }
}

// ---- Isafdar: the trees 8742 "Pass" (2305,3191 r0) / (2305,3195 r2) -----------------------------------------------------------
// Tirannwn is reachable (Port Tyras charter). Darkan Tirannwn.java:158-162 (handleLletyaTreePass): Agility.handleObstacle(3303, 1,
// player.transform(x < obj.x ? +2 : -2, 0), 0) from the route tile - 3303 plays on the start tile and the player is teleported past the
// tree one tick later (was a 60-cycle slide on a shortAxisCrossing lane). Darkan's Roving Elves quest gate is not applied (no quest
// system for it here, like every other Isafdar obstacle).
shortcut(8742, "Pass") {
    obstacle { target ->
        darkanHandleObstacle(player, 3303, 1, player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0))
    }
}

// ---- Taverley house portal 15477 ------------------------------------------------------------------------------------------------
// Every house here is in Rimmington (areas/poh/PlayerHouse.kt). 2009scape PortalOptionPlugin: a portal of another town answers
// "Your house is in <location>.".
shortcut(15477, "Enter") {
    player.message("<col=FF0000>Your house is in Rimmington.")
}

// ---- West Ardougne sewer pipe 2542 (2514,9737) -----------------------------------------------------------------------------------
// Void PlagueCity.kt: without the opened grill (Plague City progress - no quest system here) the pipe answers the statement
// "There is a grill blocking your way.".
shortcut(2542, "Climb-up") {
    player.queue { messageBox("There is a grill blocking your way.") }
}

// ---- Karamja: Dragon Slayer wall 25161 (2846,9635 / 2846,9636) ----------------------------------------------------------------------
// 2009scape DragonSlayerPlugin case 25161: a force movement two tiles over the wall (x 2845 <-> 2847) with animation 839. Quest stage
// checks are not applicable (no Dragon Slayer progress here; Elvarg is spawned, spawns_11414). climbOver = the same 839 climb over the
// 1x1 wall loc from the tile in front of it to the tile behind it.
// Darkan Karamja.java:220-255 (handleElvargEntrance; its Dragon Slayer stage gate is not applied here): only from x 2845 (face east)
// or x 2847 (face west), anim 839, the next tick tele((2847 | 2845, player.z)) - the body stays in front of the wall during the climb
// (was the shared lane + a 94-cycle slide over the wall). From any other tile the shared lane's start is walked to first.
shortcut(25161, "Climb-over") {
    obstacle { target ->
        if (player.tile.x != 2845 && player.tile.x != 2847) {
            walkToTile(player, Tile(if (player.tile.x < target.tile.x) 2845 else 2847, player.tile.z, player.tile.height))
        }
        val east = player.tile.x == 2845
        face(player, if (east) Direction.EAST else Direction.WEST)
        player.animate(839)
        wait(1)
        player.moveTo(Tile(if (east) 2847 else 2845, player.tile.z, player.tile.height))
    }
}

// ---- Karamja: Jungle Potion cave hand holds 2585 (2827,9522) --------------------------------------------------------------------
// Void Karamja.kt (jp_caverocksout): statement "You attempt to climb the rocks back out." and tele(2823, 3120).
shortcut(2585, "Climb") {
    player.queue {
        messageBox("You attempt to climb the rocks back out.")
        player.moveTo(Tile(2823, 3120, 0))
    }
}

// ---- Karamja: Jadinko Lair hole 12328 (2946,2955) -------------------------------------------------------------------------------
// Novite ObjectHandler id 12328: useStairs(3527, WorldTile(3012, 9275, 0), 5, 6), then animation 11043 on arrival. The way back is
// the lair's Cave exit 12327 -> (2948,2955) (Novite JadinkoLair.java, batch-3.json).
shortcut(12328, "Squeeze-through") {
    obstacle { target ->
        player.faceTile(target.tile)
        player.animate(3527)
        wait(5)
        player.moveTo(Tile(3012, 9275, 0))
        player.animate(11043)
    }
}

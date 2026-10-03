package gg.rsmod.plugins.content.mechanics.traversal.batch3

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Trace
import gg.rsmod.plugins.content.mechanics.ladders.GenericLadders
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.climbOver
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

// ---- Isafdar: the trees 8742 "Pass" (2305,3191) / (2305,3195) -----------------------------------------------------------------
// Tirannwn is reachable (Port Tyras charter). 2009scape RovingElvesObstacles case 8742: the player passes the tree two tiles east or
// west (a teleport, no animation; Mourning's End Part I requirement - there is no quest system here, like every other Isafdar obstacle
// in batch2/isafdar.plugin.kts). Here: a forced move over the tree's short (x) axis from the tile in front of it to the tile behind.
shortcut(8742, "Pass") {
    obstacle { target ->
        val crossing = shortAxisCrossing(world, target, player.tile)
        if (crossing == null) {
            Trace.problem(Trace.Cat.PLUGIN) { "isafdar tree 8742 at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}" }
            player.message("You can't get past from here.")
            return@obstacle
        }
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        wait(1)
        exactMove(player, crossing.landing, delay = 60, direction = crossing.direction)
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
shortcut(25161, "Climb-over") {
    obstacle { target -> climbOver(player, target, 839) }
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

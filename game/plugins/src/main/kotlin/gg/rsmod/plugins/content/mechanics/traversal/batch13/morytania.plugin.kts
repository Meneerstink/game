package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.skills.agility.*

/*
 * Obstacle batch 13 (owner priority #1, 2026-10-03), agent-handoffs/obstacle-batches/rest-batch-13.tsv: Morytania rows (Burgh de Rott,
 * Mort Myre). Every tile was checked on this 667 map with MapProbe (walkable, connected) on 2026-10-03. Quest gates follow QuestStatus:
 * In Aid of the Myreque and In Search of the Myreque count as completed (not choice quests).
 */

// ---- Burgh de Rott north gate 17757 (east) / 17760 (west) at 3485,3244 / 3484,3244, option Jump ----------------------------------------
// RS3 Wiki "Gate (Burgh de Rott)": ids 17757 / 17760, "the northern entrance to Burgh de Rott. To jump over it and access the town, the In
// Aid of the Myreque quest must be started" (completed here). Both are wall locs on the south face of their tile (rotation 3): MapProbe
// shows z 3244 (outside) and z 3243 (the town) blocked only by that face. The jump is the town's own fence jump, Void
// BurghDeRottLowFence.kt: face across, low_fence_jump 11574 and an exact move of 60 cycles - here from the tile in front of the gate to
// the tile behind it (DoorCrossing.nearSide / destination: the far side of the wall face), never through anything else.
val ANIM_LOW_FENCE_JUMP_13 = 11574

for (gate in intArrayOf(17757, 17760)) {
    shortcut(gate, "Jump") {
        obstacle { target ->
            val near = DoorCrossing.nearSide(player.tile, target)
            val far = near?.let { DoorCrossing.destination(it, target) }
            if (near == null || far == null) {
                player.message(gg.rsmod.game.model.entity.Entity.YOU_CANT_REACH_THAT)
                return@obstacle
            }
            walkToTile(player, near)
            val direction = Direction.between(near, far)
            face(player, direction)
            wait(1)
            player.animate(ANIM_LOW_FENCE_JUMP_13)
            exactMove(player, far, delay = 60, direction = direction)
        }
    }
}

// ---- Burgh de Rott derelict inn: broken down wall 12737 (3491,3230) ------------------------------------------------------------------
// RS3 Wiki "Broken down wall": inside the derelict inn, "It must be climbed over to allow access to the Myreque hideout" (In Aid of the
// Myreque, completed here). The wall is the north face of 3491,3230 and a pile of bricks (12844) fills that tile, so the climb runs from
// 3491,3229 (the inn floor) over both to 3491,3231 (the hideout corner behind it - the mirror of the hideout ladder 12779 at 3490,9632,
// batch-13.json) and back. No donor gives the 2011 animation; this is the shared low-wall climb (Void Stiles.climbStile: rocks_pile_climb
// 839, one slide over the loc, 94 cycles, start 30).
shortcut(12737, "Climb-over") {
    obstacle { target ->
        val h = target.tile.height
        val inn = Tile(target.tile.x, target.tile.z - 1, h)
        val corner = Tile(target.tile.x, target.tile.z + 1, h)
        val fromInn = player.tile.z < target.tile.z
        val start = if (fromInn) inn else corner
        val landing = if (fromInn) corner else inn
        val direction = if (fromInn) Direction.NORTH else Direction.SOUTH
        walkToTile(player, start)
        face(player, direction)
        wait(1)
        player.animate(839, delay = 30)
        exactMove(player, landing, delay = 94, direction = direction, startDelay = 30)
    }
}

// ---- Mort Myre rope bridge trees 5005 (3502,3426 south / 3502,3431 north) -----------------------------------------------------------
// Void content/quest/member/myreque/SearchMyreque.kt climbTree (swamp_bridge_tree 5005): "You climb the tree.", climb_up 828 /
// climb_down 827, the south tree from SOUTH_BANK (3502,3425) up onto the bridge (3502,3427), the bridge rungs 3502,3427-3430, the north
// tree down to NORTH_BANK (3503,3431). In Search of the Myreque counts as completed here, so the bridge is repaired (Void bridgeRepaired)
// and the tree climb carries the player over it: 828 at the bank, the slide along the rungs to the far tree, 827 and the step down onto
// the far bank. The rung tiles are not walkable on this map (MapProbe: 3502,3427-3430 blocked) and their own Walk-here / Repair options
// are not ported, so the crossing never stops on them.
val MORT_MYRE_SOUTH_BANK = Tile(3502, 3425, 0)
val MORT_MYRE_NORTH_BANK = Tile(3503, 3431, 0)
val MORT_MYRE_BRIDGE_SOUTH_END = Tile(3502, 3426, 0)
val MORT_MYRE_BRIDGE_NORTH_END = Tile(3502, 3430, 0)

shortcut(5005, "Climb") {
    obstacle {
        val north = player.tile.z <= MORT_MYRE_SOUTH_BANK.z + 1
        val start = if (north) MORT_MYRE_SOUTH_BANK else MORT_MYRE_NORTH_BANK
        walkToTile(player, start)
        face(player, if (north) Direction.NORTH else Direction.SOUTH)
        wait(1)
        player.message("You climb the tree.")
        player.animate(828)
        wait(1)
        if (north) {
            exactMove(player, MORT_MYRE_BRIDGE_NORTH_END, delay = 150, direction = Direction.NORTH)
            player.animate(827)
            // Void climbTree: the climb down the tree is a tele to the bank, not a slide past the bridge corner (sweep 18:54: noclip).
            wait(1)
            player.moveTo(MORT_MYRE_NORTH_BANK)
        } else {
            exactMove(player, MORT_MYRE_BRIDGE_NORTH_END, delay = 30, direction = Direction.SOUTH)
            exactMove(player, MORT_MYRE_BRIDGE_SOUTH_END, delay = 120, direction = Direction.SOUTH)
            player.animate(827)
            // Void climbTree: the climb down the tree is a tele to the bank, not a slide past the bridge corner (sweep 18:54: noclip).
            wait(1)
            player.moveTo(MORT_MYRE_SOUTH_BANK)
        }
        player.animate(-1)
    }
}

// ---- Fenkenstrain graveyard memorials 5167 'Push' / 'Search' (3578,3527 -> experiment cave; 3505,3571 -> the memorial cave) ------------
// 2009scape content/region/morytania/quest/creatureoffenkenstrain/CreatureOfFenkenstrainListeners.kt (MEMORIAL_5167 "push" and
// "search", identical): once the memorial is unlocked or Creature of Fenkenstrain is past stage 2 (completed here - QuestStatus), the
// memorial plays loc animation 1620 and three ticks later the player is moved to 3577,9927 (the easternmost memorial, OSRS Wiki
// "Experiment cave": "By pushing the easternmost memorial, players may enter the experiment caves below") or 3504,9969 (the memorial at
// 3505,3571). Both landings are walkable beside the caves' ladders 1757 (MapProbe). The way back up from the experiment cave is the
// ladder 1757 at 3578,9927 (batch-13.json); 2009scape addClimbDest(3504,9970 -> 3504,3571) is the other cave's ladder (batch-13.json).
val MEMORIAL_DESTINATIONS =
    mapOf(
        (3578 to 3527) to Tile(3577, 9927, 0),
        (3505 to 3571) to Tile(3504, 9969, 0),
    )
val ANIM_MEMORIAL_OPEN = 1620

for (option in listOf("Push", "Search")) {
    shortcut(5167, option) {
        val memorial = player.getInteractingGameObj()
        val dest = MEMORIAL_DESTINATIONS[memorial.tile.x to memorial.tile.z]
        if (dest == null) {
            player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
            return@shortcut
        }
        obstacle { target ->
            player.faceTile(target.tile)
            animateLoc(world, target, ANIM_MEMORIAL_OPEN)
            wait(3)
            player.moveTo(dest)
        }
    }
}

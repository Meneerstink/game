package gg.rsmod.plugins.content.mechanics.traversal.batch11

import gg.rsmod.game.model.entity.Entity
import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
import gg.rsmod.plugins.content.skills.agility.Render
import gg.rsmod.plugins.content.skills.agility.face
import gg.rsmod.plugins.content.skills.agility.hasAgility
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkOverTile
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * Obstacle batch 11 (owner priority #1, 2026-10-03): the hand-written rows of agent-handoffs/obstacle-batches/rest-batch-11.tsv.
 * Pure teleports of the same rows are data: data/cfg/object-teleports/batch-11.json; NOT_APPLICABLE rows:
 * reference/traversal/not_applicable-batch11.tsv. Every crossing below starts from the tile in front of the loc on the player's own
 * side (shared DoorCrossing.enter / walkToTile) and is one bounded step or one bounded climb - never a slide from elsewhere (E17).
 *
 * Quest gates: QuestStatus counts every non-choice quest as completed, so the post-quest branch of a source applies (Clock Tower).
 */

// ---- Clock Tower dungeon: the push wall to the blue cog room 1586 (2576,9631) -------------------------------------------------------
// 2009scape ClockTowerListeners: on(WALL_1586, "push") -> DoorActionHandler.handleAutowalkDoor (the one step through the wall, as a
// door); OSRS Wiki 'Clock Tower': "you get to a wall that you can push. Push the wall to get inside and pick up the blue cog."
// Map (MapProbe 2026-10-03): the wall is the west face of 2576,9631 (type 0, rot 0) between the cog pocket 2571-2575,9630-9633 and the
// dungeon corridor. The id's other placement (2643,9892) is in batch-earlier NOT_APPLICABLE ground and keeps the table / generic answer.
shortcut(1586, "Push") {
    val wall = player.getInteractingGameObj()
    if (wall.tile.x != 2576 || wall.tile.z != 9631 || wall.tile.height != 0) {
        ObjectTeleports.fallback(player)
        return@shortcut
    }
    obstacle { target -> DoorCrossing.enter(this, player, target) }
}

// ---- Clock Tower dungeon: the rat-cage gate to the white cog 39 (2579,9656) ----------------------------------------------------------
// 2009scape ClockTowerListeners: once the rats are poisoned, "The death throws of the rats seem to have shaken the door loose of" /
// "its hinges. You pick it up and go through." and the walk through the gate (handleAutowalkDoor); OSRS Wiki 'Clock Tower': the gate
// "has become unhinged" once the rats have died. Clock Tower counts as completed here (QuestStatus), so the gate is passable.
// Map: west face of 2579,9656 (type 0, rot 0) between the rat area and the white cog nook 2574-2578,9654-9656.
shortcut(39, "Go-through") {
    obstacle { target ->
        if (DoorCrossing.enter(this, player, target)) {
            // The remark is its own unlocked task once the step has ended (an obstacle holds the player FULL-locked; batch 9).
            player.queue {
                messageBox("The death throws of the rats seem to have shaken the door loose of", "its hinges. You pick it up and go through.")
            }
        }
    }
}

// ---- Rellekka: Lalli's cave entrance 4147 (2774,3619) ------------------------------------------------------------------------------
// OSRS Wiki 'Cave Entrance (Lalli)': it is not possible to enter the cave; Lalli says "Hey human! You not go in my house! It where me
// keep all my stuff!" (2009scape object_configs examine: "This must be Lalli's home."). Lalli 1270 stands at 2770,3624 (spawns_11064).
shortcut(4147, "Enter") {
    player.queue {
        chatNpc("Hey human! You not go in my house! It where me keep all my stuff!", npc = Npcs.LALLI)
    }
}

// ---- Yanille: the climbing rocks over the north-western town wall 2298 (2561,3108) --------------------------------------------------
// OSRS Wiki 'Agility shortcuts' / 'Climbing rocks (Yanille)': Agility 5, 25 xp, north-west of Frenita's Cookery Shop, one-way out of
// Yanille ("can't be used to enter the town"). Map (MapProbe 2026-10-03): the rocks are a wall decoration on the town wall rows
// 3108-3111 at x 2561; the start 2561,3107 (inside, reach 49, connected to the town) and the landing 2561,3112 (outside, reach 54,
// connected to the Watchtower side) are the tiles straight in front of and behind the wall. The climb is the shared rock climb up:
// the "climbing" render (BAS 0, Void Trollheim renderEmote) over the wall, as ShortcutSupport.climbCliff does upwards.
val YANILLE_ROCKS_START = Tile(2561, 3107, 0)
val YANILLE_ROCKS_LANDING = Tile(2561, 3112, 0)

shortcut(2298, "Climb-up") {
    if (player.tile.z >= 3108) {
        // One-way: from outside the town the rocks cannot be climbed.
        player.message(Entity.YOU_CANT_REACH_THAT)
        return@shortcut
    }
    if (!player.hasAgility(5)) return@shortcut
    obstacle {
        walkToTile(player, YANILLE_ROCKS_START)
        face(player, Direction.NORTH)
        wait(1)
        walkOverTile(player, YANILLE_ROCKS_LANDING, Render.CLIMBING)
        player.resetRenderAnimation()
        player.addXp(Skills.AGILITY, 25.0)
    }
}

// ---- Mountain Camp: the large tent's door flaps 5891 / 5893 (2781-2782,3656) -------------------------------------------------------
// 2009scape DoorManagingPlugin: "go-through" on a loc named ...door -> DoorActionHandler.handleDoor, i.e. the one step through it
// (no open/close pair in its door config). Map: north faces of 2781,3656 / 2782,3656 (type 0, rot 1) between the camp (comp 2180
// tiles) and the tent interior 2779-2784,3649-3656 (Ancient Rock 5895; OSRS Wiki Mountain Daughter: "enter the large tent").
for (flap in intArrayOf(5891, 5893)) {
    shortcut(flap, "Go-through") {
        obstacle { target -> DoorCrossing.enter(this, player, target) }
    }
}

package gg.rsmod.plugins.content.mechanics.traversal.batch13

import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.skills.agility.*

/*
 * Obstacle batch 13 (owner priority #1, 2026-10-03), agent-handoffs/obstacle-batches/rest-batch-13.tsv: Misthalin / Al Kharid /
 * Lair of Tarn rows. Pure teleports of the same batch are data: data/cfg/object-teleports/batch-13.json. Every tile below was checked
 * on this 667 map with MapProbe (walkable, connected) on 2026-10-03.
 *
 * Quest gates follow QuestStatus (batch 9 note): every quest that is not one of the eight choice quests counts as completed here, so
 * the post-quest branch of a source applies (The Tourist Trap).
 */

// ---- Edgeville Dungeon odd-looking wall 29360 / 29362 (3093,9895 / 3093,9896) --------------------------------------------------------
// RS3 Wiki "Odd-looking wall (Edgeville Dungeon)": ids 29360 / 29362 at 3092-3093,9895-9896, option Push - "Pushing the wall, players
// can enter or exit the small room" (OSRS Wiki: the chest / crate room north-west of the entrance, the same wall as 1734/1736). Both are
// wall locs (shape 0, rotation 2 = the east face of x 3093): MapProbe 2026-10-03 shows the room 3089-3093,9893-9899 (42 tiles) is
// reachable only across that face. The push is the one bounded step through the wall as through a door (DoorCrossing.enter: walk to
// the tile in front of the wall on the player's side, then the single step across) - no donor gives a message, so none is invented.
for (wall in intArrayOf(29360, 29362)) {
    shortcut(wall, "Push") {
        obstacle { target -> DoorCrossing.enter(this, player, target) }
    }
}

// ---- Bedabin Camp tent door 2700 (3169,3046) -------------------------------------------------------------------------------------
// 2009scape content/region/desert/bedabin/handlers/BedabinPlugin.java "walk-through" 2700: from inside (z >= 3046) the player walks
// back out to 3169,3045 with "You walk back out the tent."; from outside the Bedabin nomad guard (834, spawned at 3170,3045 by
// spawns_12591) answers. BedabinNomadDialogue.java, quest stage 100 (The Tourist Trap completed - QuestStatus): "Sorry, but you can't
// use the tent without permission. But" / "thanks for all your help with the Bedabin people." The door is a wall loc on the south
// face of 3169,3046, so the way out is the bounded door step.
val BEDABIN_NOMAD_GUARD = 834

shortcut(2700, "Walk-through") {
    val door = player.getInteractingGameObj()
    if (player.tile.z >= door.tile.z) {
        obstacle { target ->
            if (DoorCrossing.enter(this, player, target)) player.message("You walk back out the tent.")
        }
    } else {
        player.queue {
            chatNpc(
                "Sorry, but you can't use the tent without permission. But",
                "thanks for all your help with the Bedabin people.",
                npc = BEDABIN_NOMAD_GUARD,
            )
        }
    }
}

// ---- Lair of Tarn: passageway 20871 (3185,4602) and the Tarn-room passageway 20467 (3186,4627) ------------------------------------
// 2009scape content/region/morytania/quest/... LairOfTarnListeners.kt "Enter":
//  - 20871 "Door to Tarn's lair. Needs 40 slayer": hasLevelStat(SLAYER, 40) else "You need at least 40 Slayer to attempt this."; a
//    player who has killed Tarn goes to the terror dog room (3149,4644), else to the boss room (3186,4612) and the Tarn cutscene.
//    Nothing on this server records a Tarn kill (no Tarn Razorlor plugin, no kill attribute), so the not-killed branch applies.
//  - 20467 "the passageway in the Tarn fight room": only after killing Tarn (teleport y + 6), else "You need to have killed Tarn
//    Razorlor to enter this passageway." - always the refusal here, for the same reason.
// The rooms are static on this map (batch-2.json binds their exits 20465 / 20466 back to 3185,4601). Landings walkable (MapProbe).
val TARN_BOSS_ROOM = Tile(3186, 4612, 0)

shortcut(20871, "Enter") {
    if (player.skills.getMaxLevel(Skills.SLAYER) < 40) {
        player.message("You need at least 40 Slayer to attempt this.")
        return@shortcut
    }
    obstacle {
        wait(1)
        player.moveTo(TARN_BOSS_ROOM)
    }
}

shortcut(20467, "Enter") {
    player.message("You need to have killed Tarn Razorlor to enter this passageway.")
}

// ---- River Lum grapple: strong tree 241 (Lumbridge, 3246,3180) ------------------------------------------------------------------------
// The crossing (sources, tiles, sequence) is RiverLumGrapple.kt, shared with the Al Kharid tree 244 (3256,3180). 244 is bound by
// batch12_obstacles.plugin.kts for its Karamja placements and must hand this placement to RiverLumGrapple.cross (batch 13 report);
// clicking 241 from either bank crosses either way.
shortcut(241, "Grapple") {
    if (!RiverLumGrapple.ready(player)) return@shortcut
    obstacle { RiverLumGrapple.cross(this, player) }
}

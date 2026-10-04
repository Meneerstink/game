package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.ADVANCED_GNOME_AGILITY_STAGE
import gg.rsmod.game.model.attr.GNOME_AGILITY_STAGE

/**
 * Gnome Stronghold agility course, basic and advanced - ported from Darkan on 2026-10-04 (owner: "port everything from Darkan";
 * agent-handoffs/darkan-obstacles-map.md section 1a). Donor: import-source/donors/darkan/world-server-dev/.../skills/agility/
 * gnomeagility/GnomeAgility.java (revision 727). Darkan's ids are the re-numbered 727 locs (69526 log, 69383 net, 69508 tree,
 * 69507 branch, 69384 net, 69377/69378 pipes, 69506 tree, 69514 board, 69389 barrier); this 667 cache has the same course on the
 * same tiles under the old ids, mapped by role below (placement probe 2026-10-04).
 *
 * Mechanism, as Darkan: climbs are useStairs (animation, teleport one tick later, no slide); the log, rope and pipes are one
 * collision-free walk with a render (BAS 155 / 295); the board, pole and barrier are forced moves with Darkan's own client cycles
 * (darkanForceMove: no SlideTiming, no extra waits). Levels, experience and messages are Darkan's.
 *
 * Tick mapping (same as DarkanShortcuts.kt): our obstacle task starts one tick after the click = Darkan's T+1, so Darkan's
 * WorldTasks.schedule(n) is wait(n) here and a forceMove from the handler happens at the task start. darkanForceMove resumes when
 * the glide has ended (ceil(max(start, speed) / 30) ticks), so Darkan's waits after a forceMove are shortened by that much.
 *
 * Start tiles: Darkan starts from where the route to the object ended. Where Darkan's crossing runs along a fixed lane (log x 2474,
 * rope z 3420, pipes x = the pipe's x) the start is pinned to the lane's first tile, which is that route end in the normal
 * approach - a route ending on a corner would otherwise walk the first step diagonally into the obstacle.
 */

val COMPLETION_BONUS_EXPERIENCE = 39.5
val ADVANCED_COMPLETION_BONUS_EXPERIENCE = 605.0

/** Darkan GnomeAgility.java:244: the pipe crawl BAS (ready 2338 / walk 844 in this cache). */
val PIPE_CRAWL_RENDER = 295

fun Player.gnomeXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

/** Darkan Agility.hasLevel: "You need an agility level of N to use this obstacle." */
fun Player.gnomeLevel(level: Int) = hasAgility(level, "You need an agility level of $level to use this obstacle.")

// ---- Basic course ----

// Darkan GnomeAgility.java:128 (69526): addWalkSteps(2474, 3429) without clipping, BAS 155 for the walk, 7.5 xp.
on_obj_option(obj = Objs.LOG_BALANCE, option = "Walk-across") {
    obstacle {
        walkToTile(player, Tile(2474, 3436))
        player.filterableMessage("You walk carefully across the slippery log...")
        walkOverTile(player, Tile(2474, 3429), Render.ROPE_BALANCE)
        player.resetRenderAnimation()
        // The log starts a lap of either course.
        player.attr[GNOME_AGILITY_STAGE] = 1
        player.attr[ADVANCED_GNOME_AGILITY_STAGE] = 1
        player.gnomeXp(7.5)
        player.filterableMessage("... and make it safely to the other side.")
    }
}

// Darkan GnomeAgility.java:156 (69383): useStairs(828, (player.x, 3423, 1), 1, 2) - climb anim, teleport the next tick; 7.5 xp.
on_obj_option(obj = Objs.OBSTACLE_NET, option = "Climb-over") {
    obstacle {
        player.filterableMessage("You climb the netting.")
        player.animate(828)
        wait(1)
        // Darkan keeps the player's x; held to the three nets' span (2471-2476) so a route ending beside a net's end never lands
        // in the tree-house wall on plane 1 (2470 / 2477 are blocked there).
        player.moveTo(Tile(player.tile.x.coerceIn(2471, 2476), 3423, 1))
        player.advanceCourse(GNOME_AGILITY_STAGE, 2)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 2)
        wait(1)
        player.gnomeXp(7.5)
    }
}

// Darkan GnomeAgility.java:168 (69508): useStairs(828, (2473, 3420, 2), 1, 2, "... to the platform above."), 5 xp.
on_obj_option(obj = 35970, option = "Climb") {
    obstacle {
        player.filterableMessage("You climb the tree...")
        player.animate(828)
        wait(1)
        player.moveTo(Tile(2473, 3420, 2))
        player.message("... to the platform above.")
        player.advanceCourse(GNOME_AGILITY_STAGE, 3)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 3)
        wait(1)
        player.gnomeXp(5.0)
    }
}

// Darkan GnomeAgility.java:182 (2312): addWalkSteps(2483, 3420) without clipping, BAS 155, 7 xp.
on_obj_option(obj = Objs.BALANCING_ROPE, option = "Walk-on") {
    obstacle {
        walkToTile(player, Tile(2477, 3420, 2))
        walkOverTile(player, Tile(2483, 3420, 2), Render.ROPE_BALANCE)
        player.resetRenderAnimation()
        player.advanceCourse(GNOME_AGILITY_STAGE, 4)
        player.gnomeXp(7.0)
        player.filterableMessage("You passed the obstacle succesfully.")
    }
}

// Darkan GnomeAgility.java:180 (4059): no crossing back - a message only.
on_obj_option(obj = 4059, option = "Walk-on") {
    player.message("I would keep moving forward on the course.")
}

// Darkan GnomeAgility.java:208 (69507): useStairs(828, (2487, 3421, 0), 1, 2, "You climbed the tree branch succesfully."), 5 xp.
arrayOf(Objs.TREE_BRANCH, Objs.TREE_BRANCH_2315).forEach { branch ->
    on_obj_option(obj = branch, option = "Climb-down") {
        obstacle {
            player.animate(828)
            wait(1)
            player.moveTo(Tile(2487, 3421, 0))
            player.message("You climbed the tree branch succesfully.")
            player.advanceCourse(GNOME_AGILITY_STAGE, 5)
            wait(1)
            player.gnomeXp(5.0)
        }
    }
}

// Darkan GnomeAgility.java:219 (69384): route to (clamp(player.x, 2483, 2488), net.y - 1); useStairs(828, (x, net.y + 1, 0)); 8 xp.
on_obj_option(obj = Objs.OBSTACLE_NET_2286, option = "Climb-over") {
    obstacle { target ->
        walkToTile(player, Tile(player.tile.x.coerceIn(2483, 2488), target.tile.z - 1))
        player.filterableMessage("You climb the netting.")
        player.animate(828)
        wait(1)
        player.moveTo(Tile(player.tile.x, target.tile.z + 1))
        player.advanceCourse(GNOME_AGILITY_STAGE, 6)
        wait(1)
        player.gnomeXp(8.0)
    }
}

// Darkan GnomeAgility.java:231 (69377/69378): addWalkSteps(pipe.x, pipe.y == 3431 ? 3437 : 3430) without clipping, BAS 295 for the
// crawl, 7 xp (+39.5 on a lap). Both directions, as Darkan: the south pipe ends (z 3431) crawl north, the north ends (z 3435) south.
intArrayOf(43543, 43544).forEach { pipe ->
    on_obj_option(obj = pipe, option = "Squeeze-through") {
        obstacle { target ->
            val north = target.tile.z == 3431
            walkToTile(player, Tile(target.tile.x, if (north) 3430 else 3437))
            player.filterableMessage("You pulled yourself through the pipes.")
            walkOverTile(player, Tile(target.tile.x, if (north) 3437 else 3430), PIPE_CRAWL_RENDER)
            player.resetRenderAnimation()
            player.gnomeXp(7.0)
            if (player.courseStage(GNOME_AGILITY_STAGE) == 6) {
                player.attr[GNOME_AGILITY_STAGE] = 0
                player.gnomeXp(COMPLETION_BONUS_EXPERIENCE)
                gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.GNOME_COURSE)
            }
        }
    }
}

// ---- Advanced course ----

// Darkan GnomeAgility.java:90 (69506): lvl 85; useStairs(828, (2472, 3419, 3), 1, 2, "... to the platform above."); 19 xp.
on_obj_option(obj = 43528, option = "Climb-up") {
    if (!player.gnomeLevel(85)) return@on_obj_option
    obstacle {
        player.filterableMessage("You climb the tree...")
        player.animate(828)
        wait(1)
        player.moveTo(Tile(2472, 3419, 3))
        player.message("... to the platform above.")
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 4)
        // The basic lap ends here for a player who took the advanced branch.
        player.attr[GNOME_AGILITY_STAGE] = 0
        player.gnomeXp(19.0)
    }
}

// Darkan GnomeAgility.java:81 (69514): lvl 85; route to (2476, 3418, 3); forceMove((2484, 3418, 3), 2922, 25, 90); 22 xp. No failure.
on_obj_option(obj = Objs.SIGNPOST_43581, option = "Run-across") {
    if (!player.gnomeLevel(85)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2476, 3418, 3))
        darkanForceMove(player, Tile(2484, 3418, 3), startCycles = 25, speedCycles = 90, anim = 2922, direction = Direction.EAST)
        player.gnomeXp(22.0)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 5)
    }
}

// Darkan GnomeAgility.java:45 (43529): lvl 85; route to (clamp(pole.x, 2485, 2487), 3419, 3); timer t0 face, t1 11784 glide to 3421
// (0/30), t2 11785 glide 3421 -> 3425 (0/30), t3 anim 11789, t6 glide to 3429 (20/60), t11 glide 3429 -> 3432 (20/60), t15 xp 25.
// Darkan sends those glides packet-only (forceMoveVisually) and teleports the tile at the start of the next leg; there is no
// packet-only forced move here, so each leg moves the tile when its glide starts - the client sees the same glides at Darkan's ticks.
on_obj_option(obj = Objs.POLE_43529, option = "Swing-to") {
    if (!player.gnomeLevel(85)) return@on_obj_option
    obstacle { target ->
        val x = target.tile.x.coerceIn(2485, 2487)
        walkToTile(player, Tile(x, 3419, 3))
        face(player, Direction.NORTH)
        wait(1)
        darkanForceMove(player, Tile(x, 3421, 3), startCycles = 0, speedCycles = 30, anim = 11784, direction = Direction.NORTH)
        darkanForceMove(player, Tile(x, 3425, 3), startCycles = 0, speedCycles = 30, anim = 11785, direction = Direction.NORTH)
        player.animate(11789)
        wait(3)
        darkanForceMove(player, Tile(x, 3429, 3), startCycles = 20, speedCycles = 60, direction = Direction.NORTH)
        wait(3)
        darkanForceMove(player, Tile(x, 3432, 3), startCycles = 20, speedCycles = 60, direction = Direction.NORTH)
        wait(2)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 6)
        player.gnomeXp(25.0)
    }
}

// Darkan GnomeAgility.java:100 (69389): lvl 85; t0 forceMove((2485, 3434, 3), 2923, 25, 60); t3 anim 2924 + tele (2485, 3436, 0);
// t5 unlock, 25 xp (+605 on a lap). The route ends on the barrier's own tile (2485, 3433, its wall is on the north edge).
on_obj_option(obj = Objs.BARRIER_43539, option = "Jump-over") {
    if (!player.gnomeLevel(85)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2485, 3433, 3))
        darkanForceMove(player, Tile(2485, 3434, 3), startCycles = 25, speedCycles = 60, anim = 2923, direction = Direction.NORTH)
        wait(1)
        player.animate(2924)
        player.moveTo(Tile(2485, 3436, 0))
        wait(2)
        player.gnomeXp(25.0)
        if (player.courseStage(ADVANCED_GNOME_AGILITY_STAGE) == 6) {
            player.attr[ADVANCED_GNOME_AGILITY_STAGE] = 0
            player.gnomeXp(ADVANCED_COMPLETION_BONUS_EXPERIENCE)
            // ADAPTED: the RS2 advanced gnome lap rolls at the Gnome Stronghold course's chance.
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.GNOME_COURSE)
        }
    }
}

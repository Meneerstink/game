package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.ADVANCED_BARBARIAN_AGILITY_STAGE
import gg.rsmod.game.model.attr.BARBARIAN_AGILITY_STAGE

/**
 * Barbarian Outpost agility course, basic and advanced - ported from Darkan on 2026-10-04 (owner: "port everything from Darkan";
 * agent-handoffs/darkan-obstacles-map.md section 1b). Donor: import-source/donors/darkan/world-server-dev/.../skills/agility/
 * barbarianoutpost/BarbarianOutpostAgility.kt (revision 727, the same ids and tiles as this 667 map; the entrance pipe 20210 lives
 * in sourced_shortcuts). Darkan has no failure on any of these obstacles; animations, client cycles, ticks, landings, levels (35
 * basic / 90 advanced), experience and messages are Darkan's.
 *
 * Tick mapping (same as DarkanShortcuts.kt): our obstacle task starts one tick after the click = Darkan's T+1. A Darkan coroutine
 * (player.schedule) also starts at T+1, and its FIRST wait(n) resumes after n - 1 ticks (Darkan AsyncTaskScheduler.tick resumes a
 * new task and then ticks its first TickWait in the same world tick), every later wait(n) after n ticks - the waits below are
 * those effective tick counts. darkanForceMove resumes when its glide has ended (ceil(max(start, speed) / 30) ticks).
 */

val COMPLETION_BONUS_EXPERIENCE = 56.7
val ADVANCED_COMPLETION_BONUS_EXPERIENCE = 615.0

/** Darkan BarbarianOutpostAgility.kt:113 / :211: the ledge BAS (ready 757 / walk 756) and the balance-beam BAS (10289 / 10288). */
val LEDGE_RIGHT_RENDER = 157
val BALANCE_BEAM_RENDER = 330

fun Player.barbarianXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

/** Darkan Agility.hasLevel: "You need an agility level of N to use this obstacle." */
fun Player.barbarianLevel(level: Int) = hasAgility(level, "You need an agility level of $level to use this obstacle.")

fun Player.barbarianStage(stage: Int) {
    advanceCourse(BARBARIAN_AGILITY_STAGE, stage)
    advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, stage)
}

// ---- Basic course ----

// Darkan BarbarianOutpostAgility.kt:59: route to (2552 | 2551, 3554) by the rope's x; lvl 35; rope loc anim 497;
// forceMove((rope.x, 3549), 751, 20, 75); "You skillfully swing across."; 28 xp.
on_obj_option(obj = Objs.ROPE_SWING_43526, option = "Swing-on") {
    obstacle { target ->
        val x = if (target.tile.x == 2552) 2552 else 2551
        walkToTile(player, Tile(x, 3554))
        if (!player.barbarianLevel(35)) return@obstacle
        animateLoc(world, target, 497)
        darkanForceMove(player, Tile(x, 3549), startCycles = 20, speedCycles = 75, anim = 751, direction = Direction.SOUTH)
        player.filterableMessage("You skillfully swing across.")
        player.barbarianXp(28.0)
        // The rope swing starts a lap of either course.
        player.attr[BARBARIAN_AGILITY_STAGE] = 1
        player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 1
    }
}

// Darkan BarbarianOutpostAgility.kt:79: route to (2551, 3546); lvl 35; forceMove((2541, 3546), 9908, 20, 12 * 30), anim -1, 20.7 xp.
on_obj_option(obj = Objs.LOG_BALANCE_43595, option = "Walk-across") {
    obstacle {
        walkToTile(player, Tile(2551, 3546))
        if (!player.barbarianLevel(35)) return@obstacle
        player.filterableMessage("You walk carefully across the slippery log...")
        darkanForceMove(player, Tile(2541, 3546, player.tile.height), startCycles = 20, speedCycles = 12 * 30, anim = 9908, direction = Direction.WEST)
        player.animate(-1)
        player.barbarianXp(20.7)
        player.filterableMessage("... and make it safely to the other side.")
        player.barbarianStage(2)
    }
}

// Darkan BarbarianOutpostAgility.kt:93: lvl 35; "You climb the netting..."; 10.2 xp; useStairs(828, (net.x - 1, player.y, 1), 1, 2).
on_obj_option(obj = Objs.OBSTACLE_NET_20211, option = "Climb-over") {
    if (!player.barbarianLevel(35)) return@on_obj_option
    obstacle { target ->
        player.filterableMessage("You climb the netting...")
        player.barbarianXp(10.2)
        player.animate(828)
        wait(1)
        // Darkan keeps the player's z; held to the net's two tiles (3545-3546) for a route that ended beside its end.
        player.moveTo(Tile(target.tile.x - 1, player.tile.z.coerceIn(3545, 3546), 1))
        player.advanceCourse(BARBARIAN_AGILITY_STAGE, 3)
        // The net is the basic route only.
        player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 0
    }
}

// Darkan BarbarianOutpostAgility.kt:102: lvl 35; face the ledge, anim 753 + BAS 157 (same tick: the coroutine's first wait(1)),
// next tick addWalkSteps((2532, 3547)) without clipping, on arrival anim 759 + BAS -1, 26 xp, then one plain step to (2532, 3546).
on_obj_option(obj = Objs.BALANCING_LEDGE, option = "Walk-across") {
    if (!player.barbarianLevel(35)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2536, 3547, 1))
        player.filterableMessage("You put your foot on the ledge and try to edge across...")
        face(player, Direction.WEST)
        player.animate(753)
        player.setRenderAnimation(LEDGE_RIGHT_RENDER)
        wait(1)
        walkOverTile(player, Tile(2532, 3547, 1), LEDGE_RIGHT_RENDER)
        player.animate(759)
        player.resetRenderAnimation()
        player.barbarianXp(26.0)
        player.filterableMessage("You skillfully edge across the gap.")
        player.advanceCourse(BARBARIAN_AGILITY_STAGE, 4)
        // Darkan addWalkSteps(2532, 3546): the ordinary step towards the ladder.
        try {
            walkToTile(player, Tile(2532, 3546, 1))
        } catch (e: ObstacleApproachFailed) {
            player.stopMovement()
        }
    }
}

// The course ladder down from the ledge (Void ObjectTeleports/Stairs: climb_down 827, two ticks; Darkan has no own handler).
on_obj_option(obj = Objs.LADDER_3205, option = "Climb-down") {
    obstacle {
        player.animate(827)
        wait(2)
        player.moveTo(Tile(2532, 3546, 0))
    }
}

// Darkan BarbarianOutpostAgility.kt:129: lvl 35; only from the west ("You cannot climb that from this side."); "You climb the low
// wall..."; forceMove((wall.x + 1, wall.y), 4853, 30, 60); 16.2 xp; wall one / wall two, +56.7 on a lap.
on_obj_option(obj = Objs.CRUMBLING_WALL, option = "Climb-over") {
    val target = player.getInteractingGameObj()
    if (!player.barbarianLevel(35)) return@on_obj_option
    if (player.tile.x >= target.tile.x) {
        player.message("You cannot climb that from this side.")
        return@on_obj_option
    }
    obstacle {
        // The route end in front of the 1x1 wall (pinned to its row so the climb never starts from a corner).
        walkToTile(player, Tile(target.tile.x - 1, target.tile.z))
        player.filterableMessage("You climb the low wall...")
        darkanForceMove(player, Tile(target.tile.x + 1, target.tile.z), startCycles = 30, speedCycles = 60, anim = 4853, direction = Direction.EAST)
        player.barbarianXp(16.2)
        if (target.tile.x == 2542) {
            if (player.courseStage(BARBARIAN_AGILITY_STAGE) == 5) {
                player.attr[BARBARIAN_AGILITY_STAGE] = 0
                player.barbarianXp(COMPLETION_BONUS_EXPERIENCE)
                gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.BARBARIAN_COURSE)
            }
        } else {
            player.advanceCourse(BARBARIAN_AGILITY_STAGE, 5)
        }
    }
}

// ---- Advanced course ----

// Darkan BarbarianOutpostAgility.kt:156: lvl 90; face north, anim 10492 (same tick), wait 6, tele to plane 2 on the same tile and
// in the same tick forceMove((2538, 3545, 2), 10493, 10, 30); 15 xp. The plane teleport and the glide are one update, so the glide
// is sent from the ground tile straight to plane 2 (same packet: the glide offsets are x/z only).
on_obj_option(obj = 43533, option = "Run-up") {
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2538, 3540))
        face(player, Direction.NORTH)
        player.animate(10492)
        wait(6)
        darkanForceMove(player, Tile(2538, 3545, 2), startCycles = 10, speedCycles = 30, anim = 10493, direction = Direction.NORTH)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 3)
    }
}

// Darkan BarbarianOutpostAgility.kt:173: lvl 90; route to the wall's tile (2537, 3546, 2); face west, anim 10023 (same tick),
// wait 2, tele (2536, 3546, 3) + anim 11794, wait 1, 15 xp.
on_obj_option(obj = 43597, option = "Climb-up") {
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2537, 3546, 2))
        face(player, Direction.WEST)
        player.animate(10023)
        wait(2)
        player.moveTo(Tile(2536, 3546, 3))
        player.animate(11794)
        wait(1)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 4)
    }
}

// Darkan BarbarianOutpostAgility.kt:192: lvl 90; route to (2533, 3547, 3); face (2531, 3554); spring loc anim 11819 and (same tick)
// forceMove((2532, 3553, 3), 4189, 15, 90); after it 15 xp and the haystack (43590 at 2531, 3554, 3) plays 7527.
on_obj_option(obj = 43587, option = "Fire") {
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle { target ->
        walkToTile(player, Tile(2533, 3547, 3))
        player.faceTile(Tile(2531, 3554, 3))
        animateLoc(world, target, 11819)
        darkanForceMove(player, Tile(2532, 3553, 3), startCycles = 15, speedCycles = 90, anim = 4189, direction = Direction.NORTH)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 5)
        locAt(world, Tile(2531, 3554, 3), 43590)?.let { animateLoc(world, it, 7527) }
    }
}

// Darkan BarbarianOutpostAgility.kt:209: lvl 90; BAS 330; forceMove((2536, 3553, 3), 16079, 10, 90); anim -1; 15 xp. Darkan's
// sequence 16079 is not in this 667 cache (seqinfo 2026-10-04), so the beam keeps this cache's beam walk 10295.
on_obj_option(obj = 43527, option = "Cross") {
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle {
        player.setRenderAnimation(BALANCE_BEAM_RENDER)
        darkanForceMove(player, Tile(2536, 3553, 3), startCycles = 10, speedCycles = 90, anim = 10295, direction = Direction.EAST)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 6)
        player.animate(-1)
    }
}

// Darkan BarbarianOutpostAgility.kt:227: lvl 90; anim 2586, BAS -1; next tick tele (2538, 3553, 2) + anim 2588, 15 xp.
on_obj_option(obj = 43531, option = "Jump-over") {
    val target = player.getInteractingGameObj()
    if (target.tile.x != 2537 || target.tile.z != 3553) {
        player.message("Nothing interesting happens.")
        return@on_obj_option
    }
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle {
        player.animate(2586)
        player.resetRenderAnimation()
        wait(1)
        player.moveTo(Tile(2538, 3553, 2))
        player.animate(2588)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 7)
    }
}

// Darkan BarbarianOutpostAgility.kt:251: lvl 90; forceMove((2540, y, 2), 11792, 10, 30); then forceMove((2542, y, 1), 11790, 0, 90);
// forceMove((2543, y, 1), 11791, 0, 30); anim 2588 + tele (2543, y, 0); 15 xp, +615 on a lap. The glides change plane inside the
// forced move as Darkan does. (Darkan's coroutine starts the third glide one tick before the 90-cycle second glide ends - its first
// wait(3) is two ticks; here each glide runs to its end, so the third glide and the landing come one tick later.)
on_obj_option(obj = 43532, option = "Slide-down") {
    if (!player.barbarianLevel(90)) return@on_obj_option
    obstacle {
        val z = player.tile.z.coerceIn(3552, 3554)
        darkanForceMove(player, Tile(2540, z, 2), startCycles = 10, speedCycles = 30, anim = 11792, direction = Direction.EAST)
        darkanForceMove(player, Tile(2542, z, 1), startCycles = 0, speedCycles = 90, anim = 11790, direction = Direction.EAST)
        darkanForceMove(player, Tile(2543, z, 1), startCycles = 0, speedCycles = 30, anim = 11791, direction = Direction.EAST)
        player.animate(2588)
        player.moveTo(Tile(2543, z, 0))
        player.barbarianXp(15.0)
        if (player.courseStage(ADVANCED_BARBARIAN_AGILITY_STAGE) == 7) {
            player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 0
            player.barbarianXp(ADVANCED_COMPLETION_BONUS_EXPERIENCE)
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.BARBARIAN_COURSE)
        }
    }
}

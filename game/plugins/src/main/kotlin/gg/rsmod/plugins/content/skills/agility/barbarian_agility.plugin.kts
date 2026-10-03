package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.ADVANCED_BARBARIAN_AGILITY_STAGE
import gg.rsmod.game.model.attr.BARBARIAN_AGILITY_STAGE
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports

/**
 * Barbarian Outpost agility course, basic and advanced - ported from Void (`skill/agility/course/BarbarianOutpost.kt`,
 * `BarbarianAdvanced.kt`) on 2026-10-03 (owner priority #1; audit agent-handoffs/audit-2026-10-03-courses-vs-donors.md). Every
 * tile is Void's and a placement of this 667 map (find probe 2026-10-03); the entrance pipe 20210 lives in sourced_shortcuts.
 *
 * Against the old handlers: one locked [obstacle] task per obstacle with a real approach to a fixed start tile (the rope swing,
 * ledge and crumbling walls used to fire sub-queues that dropped the lock, and the walls moved the player before their slide -
 * a desync), the right render emotes (ledge_balance 1026, rope_balance 155, tread_water 152, falling 189), the right directions
 * (the rope swing faced EAST while it moves south), Void's failure branches (rope swing Level.success(level, 70), log and ledge
 * (level, 93); Void damage / 10), a single 13.7 for the second wall (it gave it twice) and no slide across planes (the run-up
 * wall and the roof teleport after their slide, as the audit asked).
 */

val COMPLETION_BONUS_EXPERIENCE = 46.3
val ADVANCED_COMPLETION_BONUS_EXPERIENCE = 615.0

fun Player.barbarianXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

fun Player.barbarianStage(stage: Int) {
    advanceCourse(BARBARIAN_AGILITY_STAGE, stage)
    advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, stage)
}

// ---- Basic course ----

on_obj_option(obj = Objs.ROPE_SWING_43526, option = "Swing-on") {
    obstacle { target ->
        walkToTile(player, Tile(player.tile.x.coerceIn(2551, 2552), 3554))
        face(player, Direction.SOUTH)
        val success = agilitySuccessUntil(player, 70)
        player.animate(751)
        animateLoc(world, target, 497)
        wait(1)
        if (success) {
            exactMove(player, Tile(player.tile.x, 3549), delay = 60, direction = Direction.SOUTH)
            player.barbarianXp(22.0)
            player.filterableMessage("You skillfully swing across.")
            // The rope swing starts a lap of either course.
            player.attr[BARBARIAN_AGILITY_STAGE] = 1
            player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 1
        } else {
            exactMove(player, Tile(player.tile.x, 3550), delay = 50, direction = Direction.SOUTH)
            wait(1)
            player.moveTo(Tile(player.tile.x, 9950))
            player.hit(5)
            wait(3)
            player.filterableMessage("You slip and fall to the pit below.")
            walkOverTile(player, Tile(player.tile.x, 9949), Render.NONE)
        }
    }
}

on_obj_option(obj = Objs.LOG_BALANCE_43595, option = "Walk-across") {
    obstacle {
        walkToTile(player, Tile(2551, 3546))
        walkOverTile(player, Tile(2550, 3546), Render.NONE)
        val success = agilitySuccessUntil(player, 93)
        player.filterableMessage("You walk carefully across the slippery log...")
        if (success) {
            walkOverTile(player, Tile(2541, 3546), Render.ROPE_BALANCE)
            player.resetRenderAnimation()
            player.barbarianXp(13.7)
            player.filterableMessage("... and make it safely to the other side.")
            player.barbarianStage(2)
        } else {
            face(player, Direction.WEST)
            walkOverTile(player, Tile(2545, 3546), Render.ROPE_BALANCE)
            player.animate(2581)
            wait(1)
            player.filterableMessage("... but you lose your footing and fall into the water.")
            player.moveTo(Tile(2545, 3545))
            player.setRenderAnimation(Render.TREAD_WATER)
            wait(1)
            walkOverTile(player, Tile(2545, 3543), Render.TREAD_WATER)
            player.filterableMessage("Something in the water bites you.")
            player.resetRenderAnimation()
            player.hit(java.util.concurrent.ThreadLocalRandom.current().nextInt(3, 6))
        }
    }
}

on_obj_option(obj = Objs.OBSTACLE_NET_20211, option = "Climb-over") {
    obstacle {
        player.filterableMessage("You climb the netting...")
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.advanceCourse(BARBARIAN_AGILITY_STAGE, 3)
        // The net is the basic route only.
        player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 0
        player.moveTo(Tile(2537, player.tile.z.coerceIn(3545, 3546), 1))
        player.barbarianXp(8.2)
    }
}

on_obj_option(obj = Objs.BALANCING_LEDGE, option = "Walk-across") {
    obstacle {
        walkToTile(player, Tile(2536, 3547, 1))
        face(player, Direction.WEST)
        player.animate(753)
        val success = agilitySuccessUntil(player, 93)
        wait(1)
        player.filterableMessage("You put your foot on the ledge and try to edge across...")
        if (success) {
            walkOverTile(player, Tile(2532, 3547, 1), Render.LEDGE_BALANCE)
            face(player, Direction.WEST)
            player.animate(759)
            player.resetRenderAnimation()
            player.barbarianXp(22.0)
            player.filterableMessage("You skillfully edge across the gap.")
            player.advanceCourse(BARBARIAN_AGILITY_STAGE, 4)
        } else {
            walkOverTile(player, Tile(2534, 3547, 1), Render.LEDGE_BALANCE)
            face(player, Direction.WEST)
            player.animate(2581)
            wait(1)
            player.moveTo(Tile(2534, 3546, 1))
            face(player, Direction.SOUTH)
            player.setRenderAnimation(Render.FALLING)
            wait(1)
            player.moveTo(Tile(2534, 3546, 0))
            player.resetRenderAnimation()
            player.hit(5)
            wait(1)
            walkOverTile(player, Tile(2534, 3545), Render.NONE)
        }
    }
}

// The course ladder down from the ledge (Void ObjectTeleports/Stairs: climb_down 827, two ticks).
on_obj_option(obj = Objs.LADDER_3205, option = "Climb-down") {
    obstacle {
        player.animate(ObjectTeleports.CLIMB_DOWN_ANIM)
        wait(2)
        player.moveTo(Tile(2532, 3546, 0))
    }
}

on_obj_option(obj = Objs.CRUMBLING_WALL, option = "Climb-over") {
    val target = player.getInteractingGameObj()
    if (player.tile.x > target.tile.x) {
        player.message("You cannot climb that from this side.")
        return@on_obj_option
    }
    obstacle {
        walkToTile(player, Tile(target.tile.x - 1, target.tile.z))
        face(player, Direction.EAST)
        player.filterableMessage("You climb the low wall...")
        player.animate(4853)
        exactMove(player, Tile(target.tile.x + 1, target.tile.z), delay = 60, direction = Direction.EAST)
        if (target.tile.x == 2542) {
            if (player.courseStage(BARBARIAN_AGILITY_STAGE) == 5) {
                player.attr[BARBARIAN_AGILITY_STAGE] = 0
                player.barbarianXp(COMPLETION_BONUS_EXPERIENCE)
                gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.BARBARIAN_COURSE)
            }
        } else {
            player.advanceCourse(BARBARIAN_AGILITY_STAGE, 5)
        }
        player.barbarianXp(13.7)
    }
}

// ---- Advanced course ----

on_obj_option(obj = 43533, option = "Run-up") {
    if (!player.hasAgility(90, "You need to have an Agility level of 90.")) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2538, 3540))
        face(player, Direction.NORTH)
        wait(1)
        player.animate(10492)
        wait(7)
        player.animate(10493)
        // Void slides from the ground straight onto plane 2; the climb is drawn by the sequence, the plane change is a teleport.
        wait(1)
        player.moveTo(Tile(2538, 3545, 2))
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 3)
    }
}

on_obj_option(obj = 43597, option = "Climb-up") {
    obstacle {
        walkToTile(player, Tile(2537, 3546, 2))
        face(player, Direction.WEST)
        wait(1)
        player.animate(10023)
        wait(1)
        player.moveTo(Tile(2536, 3546, 3))
        player.animate(11794)
        wait(1)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 4)
    }
}

on_obj_option(obj = 43587, option = "Fire") {
    obstacle { target ->
        face(player, Direction.NORTH)
        wait(1)
        animateLoc(world, target, 11819)
        wait(1)
        player.moveTo(Tile(2533, 3547, 3))
        player.animate(4189)
        exactMove(player, Tile(2532, 3553, 3), delay = 60, direction = Direction.NORTH)
        animateLoc(world, target, 11820)
        wait(2)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 5)
    }
}

on_obj_option(obj = 43527, option = "Cross") {
    obstacle {
        walkToTile(player, Tile(2533, 3553, 3))
        face(player, Direction.EAST)
        wait(1)
        player.animate(10295)
        wait(1)
        exactMove(player, Tile(2536, 3553, 3), delay = 45, direction = Direction.EAST)
        wait(1)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 6)
    }
}

on_obj_option(obj = 43531, option = "Jump-over") {
    val target = player.getInteractingGameObj()
    if (target.tile.x != 2537 || target.tile.z != 3553) {
        player.message("Nothing interesting happens.")
        return@on_obj_option
    }
    obstacle {
        walkToTile(player, Tile(2536, 3553, 3))
        face(player, Direction.EAST)
        player.animate(2586)
        wait(1)
        player.moveTo(Tile(2539, 3553, 2))
        player.animate(2588)
        wait(1)
        player.barbarianXp(15.0)
        player.advanceCourse(ADVANCED_BARBARIAN_AGILITY_STAGE, 7)
    }
}

on_obj_option(obj = 43532, option = "Slide-down") {
    obstacle {
        val z = player.tile.z.coerceIn(3552, 3554)
        walkToTile(player, Tile(2539, z, 2))
        face(player, Direction.EAST)
        player.animate(11792)
        exactMove(player, Tile(2540, z, 2), delay = 30, direction = Direction.EAST)
        player.animate(11790)
        // Void slides onto plane 1 here; on this server the slide stays on its plane and the drop is the teleport below.
        exactMove(player, Tile(2543, z, 2), delay = 90, direction = Direction.EAST)
        player.animate(11790)
        wait(1)
        player.animate(11791)
        wait(1)
        player.moveTo(Tile(2544, z, 0))
        player.animate(2588)
        wait(1)
        player.barbarianXp(15.0)
        if (player.courseStage(ADVANCED_BARBARIAN_AGILITY_STAGE) == 7) {
            player.attr[ADVANCED_BARBARIAN_AGILITY_STAGE] = 0
            player.barbarianXp(ADVANCED_COMPLETION_BONUS_EXPERIENCE)
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.BARBARIAN_COURSE)
        }
    }
}

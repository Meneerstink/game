package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.ADVANCED_GNOME_AGILITY_STAGE
import gg.rsmod.game.model.attr.GNOME_AGILITY_STAGE
import gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports

/**
 * Gnome Stronghold agility course, basic and advanced - ported from Void (`skill/agility/course/GnomeStronghold.kt`,
 * `GnomeAdvanced.kt`) on 2026-10-03 (owner priority #1, "alle obstakels exact zoals OSRS"; audit
 * agent-handoffs/audit-2026-10-03-courses-vs-donors.md). Tiles are Void's and every one of them is a placement of this 667 map.
 *
 * What changed against the old handlers: every obstacle is one locked [obstacle] task with a real approach to a fixed start tile
 * (no blind `wait(distance)`, no slide from wherever the click left the player); the old helper sub-queues that dropped the lock
 * halfway (sign post: xp without crossing; pole / barrier: stranded mid-air on plane 3) are gone; height changes are a
 * teleport after the climb, never a slide across planes; the barrier gives 25 (+605 on a lap) instead of 50; the advanced lap
 * resets the advanced stage; the sign post can fail as in Void (-8..286, "failure rate 4.68-1.17% from 85-88"), damage
 * (life points - 10, max 65) / 10 here; the rope end 4059 walks back across as in Void.
 * Squeeze-through pipes keep this cache's named squeeze sequences (AGIL_SQUEEZE_INTO_PIPE 12457 / OUT 12458) - Void's own note on
 * its climb_through_pipe is "not the correct anims".
 */

val COMPLETION_BONUS_EXPERIENCE = 39.0
val ADVANCED_COMPLETION_BONUS_EXPERIENCE = 605.0

fun Player.gnomeXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

// ---- Basic course ----

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

on_obj_option(obj = Objs.OBSTACLE_NET, option = "Climb-over") {
    obstacle {
        player.filterableMessage("You climb the netting...")
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.advanceCourse(GNOME_AGILITY_STAGE, 2)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 2)
        player.moveTo(Tile(player.tile.x.coerceIn(2471, 2476), 3424, 1))
        player.gnomeXp(7.5)
    }
}

on_obj_option(obj = 35970, option = "Climb") {
    obstacle {
        player.filterableMessage("You climb the tree...")
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.filterableMessage("... to the platform above.")
        player.advanceCourse(GNOME_AGILITY_STAGE, 3)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 3)
        player.moveTo(Tile(2473, 3420, 2))
        player.gnomeXp(5.0)
    }
}

on_obj_option(obj = Objs.BALANCING_ROPE, option = "Walk-on") {
    obstacle {
        walkToTile(player, Tile(2477, 3420, 2))
        walkOverTile(player, Tile(2483, 3420, 2), Render.ROPE_BALANCE)
        player.advanceCourse(GNOME_AGILITY_STAGE, 4)
        player.resetRenderAnimation()
        player.gnomeXp(7.5)
        player.filterableMessage("You passed the obstacle successfully.")
    }
}

on_obj_option(obj = 4059, option = "Walk-on") {
    obstacle {
        walkToTile(player, Tile(2483, 3420, 2))
        walkOverTile(player, Tile(2477, 3420, 2), Render.ROPE_BALANCE)
        player.resetRenderAnimation()
        player.gnomeXp(7.5)
        player.filterableMessage("You passed the obstacle successfully.")
    }
}

arrayOf(Objs.TREE_BRANCH, Objs.TREE_BRANCH_2315).forEach { branch ->
    on_obj_option(obj = branch, option = "Climb-down") {
        obstacle {
            player.filterableMessage("You climb the tree...")
            player.animate(ObjectTeleports.CLIMB_DOWN_ANIM)
            wait(2)
            player.advanceCourse(GNOME_AGILITY_STAGE, 5)
            player.moveTo(Tile(2486, 3420, 0))
            player.gnomeXp(5.0)
        }
    }
}

on_obj_option(obj = Objs.OBSTACLE_NET_2286, option = "Climb-over") {
    obstacle { target ->
        player.filterableMessage("You climb the netting.")
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.advanceCourse(GNOME_AGILITY_STAGE, 6)
        // Void: two tiles on, towards the net (vertical part of the delta), from whichever side.
        val north = target.tile.z >= player.tile.z
        player.moveTo(Tile(player.tile.x, player.tile.z + if (north) 2 else -2))
        player.gnomeXp(7.5)
    }
}

intArrayOf(43543, 43544).forEach { pipe ->
    on_obj_option(obj = pipe, option = "Squeeze-through") {
        val target = player.getInteractingGameObj()
        // The pipes are entered at their south ends (z 3431) and come out north (z 3437).
        if (target.tile.z != 3431 || player.tile.z > target.tile.z) {
            player.message("You can't enter the pipe from this side.")
            return@on_obj_option
        }
        obstacle {
            walkToTile(player, Tile(target.tile.x, 3430))
            face(player, Direction.NORTH)
            player.filterableMessage("You pull yourself through the pipes..")
            wait(1)
            player.animate(Anims.AGIL_SQUEEZE_INTO_PIPE)
            exactMove(player, Tile(target.tile.x, 3433), direction = Direction.NORTH)
            player.moveTo(Tile(target.tile.x, 3434))
            player.animate(Anims.AGIL_SQUEEZE_OUT_PIPE, delay = 1)
            exactMove(player, Tile(target.tile.x, 3437), direction = Direction.NORTH)
            if (player.courseStage(GNOME_AGILITY_STAGE) == 6) {
                player.attr[GNOME_AGILITY_STAGE] = 0
                player.gnomeXp(COMPLETION_BONUS_EXPERIENCE)
                gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.GNOME_COURSE)
            }
            player.gnomeXp(7.5)
        }
    }
}

// ---- Advanced course ----

on_obj_option(obj = 43528, option = "Climb-up") {
    if (!player.hasAgility(85, "You need to have an Agility level of 85.")) return@on_obj_option
    obstacle {
        walkToTile(player, Tile(2472, 3420, 2))
        player.filterableMessage("You climb the tree...")
        player.animate(ObjectTeleports.CLIMB_UP_ANIM)
        wait(2)
        player.filterableMessage("... to an even higher platform.")
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 4)
        // The basic lap ends here for a player who took the advanced branch.
        player.attr[GNOME_AGILITY_STAGE] = 0
        player.moveTo(Tile(2472, 3419, 3))
        player.gnomeXp(25.0)
    }
}

on_obj_option(obj = Objs.SIGNPOST_43581, option = "Run-across") {
    obstacle {
        walkToTile(player, Tile(2477, 3418, 3))
        face(player, Direction.EAST)
        val success = agilitySuccess(player, -8, 286)
        player.animate(if (success) 2922 else 11793)
        wait(1)
        if (!success) {
            exactMove(player, Tile(2480, 3418, 3), delay = 30, direction = Direction.EAST)
            wait(5)
        }
        exactMove(player, Tile(2484, 3418, 3), delay = if (success) 60 else 210, direction = Direction.EAST)
        if (success) {
            player.gnomeXp(25.0)
            player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 5)
        } else {
            wait(3)
            player.animate(11794)
            wait(1)
            player.hit(((player.getCurrentLifepoints() - 1).coerceAtMost(6)).coerceAtLeast(0))
        }
        player.animate(-1)
    }
}

on_obj_option(obj = Objs.POLE_43529, option = "Swing-to") {
    obstacle {
        // Void: the player's own lane, 2484 moved to 2485; the run-up lanes on this map are x 2485-2487 (map dump 2026-10-03).
        val x = player.tile.x.coerceIn(2485, 2487)
        walkToTile(player, Tile(x, 3418, 3))
        face(player, Direction.NORTH)
        wait(1)
        player.animate(11784)
        exactMove(player, Tile(x, 3421, 3), delay = 60, direction = Direction.NORTH)
        player.animate(11785)
        exactMove(player, Tile(x, 3425, 3), delay = 30, direction = Direction.NORTH)
        player.animate(11789)
        wait(4)
        exactMove(player, Tile(x, 3429, 3), delay = 30, direction = Direction.NORTH)
        wait(4)
        exactMove(player, Tile(x, 3432, 3), delay = 30, direction = Direction.NORTH)
        wait(1)
        player.advanceCourse(ADVANCED_GNOME_AGILITY_STAGE, 6)
        player.gnomeXp(25.0)
    }
}

on_obj_option(obj = Objs.BARRIER_43539, option = "Jump-over") {
    obstacle {
        walkToTile(player, Tile(2485, 3432, 3))
        face(player, Direction.NORTH)
        player.animate(2923)
        wait(1)
        exactMove(player, Tile(2485, 3434, 3), delay = 30, direction = Direction.NORTH)
        wait(1)
        player.moveTo(Tile(2485, 3436, 0))
        player.animate(2924)
        if (player.courseStage(ADVANCED_GNOME_AGILITY_STAGE) == 6) {
            player.attr[ADVANCED_GNOME_AGILITY_STAGE] = 0
            player.gnomeXp(ADVANCED_COMPLETION_BONUS_EXPERIENCE)
            // ADAPTED: the RS2 advanced gnome lap rolls at the Gnome Stronghold course's chance.
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.GNOME_COURSE)
        }
        player.gnomeXp(25.0)
    }
}

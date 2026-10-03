package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.model.attr.WILDERNESS_AGILITY_STAGE

/**
 * Wilderness agility course - ported from Void (`skill/agility/course/WildernessCourse.kt`) on 2026-10-03 (owner priority #1,
 * Deadman escapes and pursuits run through here; audit agent-handoffs/audit-2026-10-03-courses-vs-donors.md). Every tile is
 * Void's and a placement of this 667 map (find probe 2026-10-03): door 2309 (2998,3917), gates 2307/2308 (2998/2997,3931),
 * pipe 2288 (3004,3938 / 3948), rope swing 2283 (3005,3952), stepping stone 37704 (3001,3960), log 2297 (3001,3945), rocks 2328
 * (2993-2995,3936).
 *
 * Against the old handlers: the ridge between the door and the gates is walked with beam_balance (330) both ways (the old door
 * moved the player one tile, gave 15 xp both ways and refused level 52); the gates 2307/2308 had no handler (no way back, no
 * wolf-pit fall); the log is beam_balance (330, not 155); the rocks are the "climbing" walk (they teleported); every obstacle is
 * one locked task with a fixed start tile and Void's failure branch (Level.success 200..250, stones 180..250 at the third
 * stone; Void damage / 10).
 */

val COMPLETION_BONUS_EXPERIENCE = 499.0
val DOOR_OPEN = 1543
val GATE_EAST_OPEN = 2040
val GATE_WEST_OPEN = 2042

fun Player.wildernessXp(xp: Double) = addXp(Skills.AGILITY, xp, checkBrawlingGloves = true)

/** Void fallIntoPit: off the ridge into the wolf pit. */
suspend fun QueueTask.fallIntoPit(player: Player) {
    walkOverTile(player, Tile(2998, 3924), Render.BEAM_BALANCE)
    player.resetRenderAnimation()
    face(player, Direction.NORTH)
    player.animate(764)
    player.filterableMessage("You lose your footing and fall into the wolf pit.")
    wait(1)
    exactMove(player, Tile(3001, 3923), delay = 25, direction = Direction.SOUTH)
}

on_obj_option(obj = Objs.DOOR_2309, option = "Open") {
    if (!player.hasAgility(52, "You need an Agility level of 52 to attempt balancing on the ridge.")) return@on_obj_option
    obstacle { door ->
        if (player.tile.z > 3916) {
            // Back out of the course: through the door only.
            passDoor(player, door, DOOR_OPEN)
            return@obstacle
        }
        walkToTile(player, Tile(2998, 3916))
        player.filterableMessage("You go through the gate and try to edge over the ridge...")
        if (!passDoor(player, door, DOOR_OPEN)) return@obstacle
        walkOverTile(player, Tile(2998, 3930), Render.BEAM_BALANCE)
        player.resetRenderAnimation()
        val gate = locAt(world, Tile(2998, 3931), Objs.GATE_2307)
        if (gate == null || !passDoor(player, gate, GATE_EAST_OPEN)) walkOverTile(player, Tile(2998, 3931), Render.NONE)
        player.filterableMessage("You skillfully balance across the ridge...")
        player.wildernessXp(15.0)
    }
}

listOf(Objs.GATE_2307 to GATE_EAST_OPEN, Objs.GATE_2308 to GATE_WEST_OPEN).forEach { (id, opened) ->
    on_obj_option(obj = id, option = "Open") {
        obstacle { gate ->
            if (player.tile.z < 3931) {
                // Up off the ridge into the course: through the gate only.
                passDoor(player, gate, opened)
                return@obstacle
            }
            val success = agilitySuccess(player, 200, 250)
            player.filterableMessage("You go through the gate and try to edge over the ridge...")
            walkToTile(player, Tile(gate.tile.x, 3931))
            if (!passDoor(player, gate, opened)) return@obstacle
            if (!success) {
                fallIntoPit(player)
                return@obstacle
            }
            walkOverTile(player, Tile(2998, 3917), Render.BEAM_BALANCE)
            player.resetRenderAnimation()
            val door = locAt(world, Tile(2998, 3917), Objs.DOOR_2309)
            if (door == null || !passDoor(player, door, DOOR_OPEN)) walkOverTile(player, Tile(2998, 3916), Render.NONE)
            player.filterableMessage("You skillfully balance across the ridge...")
            player.wildernessXp(15.0)
        }
    }
}

on_obj_option(obj = Objs.OBSTACLE_PIPE_2288, option = "Squeeze-through") {
    val target = player.getInteractingGameObj()
    if (target.tile.x != 3004 || target.tile.z != 3938) {
        player.message("You can't enter the pipe from this side.")
        return@on_obj_option
    }
    obstacle {
        walkToTile(player, Tile(3004, 3937))
        face(player, Direction.NORTH)
        player.animate(ANIM_PIPE, delay = 30)
        exactMove(player, Tile(3004, 3940), delay = 96, direction = Direction.NORTH, startDelay = 30)
        player.moveTo(Tile(3004, 3947))
        wait(1)
        player.animate(ANIM_PIPE, delay = 30)
        exactMove(player, Tile(3004, 3950), delay = 96, direction = Direction.NORTH, startDelay = 30)
        player.wildernessXp(12.5)
        // The pipe starts a lap.
        player.attr[WILDERNESS_AGILITY_STAGE] = 1
    }
}

on_obj_option(obj = Objs.ROPESWING_2283, option = "Swing-on") {
    obstacle { target ->
        walkToTile(player, Tile(target.tile.x, 3953))
        face(player, Direction.NORTH)
        val success = agilitySuccess(player, 200, 250)
        player.animate(751)
        animateLoc(world, target, 497)
        wait(1)
        if (success) {
            exactMove(player, Tile(player.tile.x, 3958), delay = 60, direction = Direction.NORTH)
            player.wildernessXp(20.0)
            player.filterableMessage("You skillfully swing across.")
            player.advanceCourse(WILDERNESS_AGILITY_STAGE, 2)
        } else {
            exactMove(player, Tile(player.tile.x, 3957), delay = 50, direction = Direction.NORTH)
            wait(1)
            player.moveTo(Tile(3004, 10357))
            player.hit((player.skills.getMaxLevel(Skills.CONSTITUTION) * 0.15).toInt() + 1)
            player.filterableMessage("You slip and fall to the pit below.")
        }
    }
}

on_obj_option(obj = Objs.STEPPING_STONE_37704, option = "Cross") {
    obstacle { target ->
        walkToTile(player, Tile(target.tile.x + 1, target.tile.z))
        face(player, Direction.WEST)
        player.filterableMessage("You carefully start crossing the stepping stones...")
        for (i in 0..5) {
            player.animate(741)
            player.playSound(2461)
            exactMove(player, Tile(target.tile.x - i, target.tile.z), delay = 30, direction = Direction.WEST, startDelay = 15)
            wait(1)
            if (i == 2 && !agilitySuccess(player, 180, 250)) {
                player.animate(764)
                face(player, Direction.WEST)
                player.filterableMessage("...You lose your footing and fall into the lava.")
                wait(2)
                player.hit(player.skills.getMaxLevel(Skills.CONSTITUTION) / 5 + 1)
                player.moveTo(Tile(3002, 3963))
                return@obstacle
            }
        }
        player.filterableMessage("...You safely cross to the other side.")
        player.wildernessXp(20.0)
        player.advanceCourse(WILDERNESS_AGILITY_STAGE, 3)
    }
}

on_obj_option(obj = Objs.LOG_BALANCE_2297, option = "Walk-across") {
    obstacle { target ->
        walkToTile(player, Tile(target.tile.x + 1, target.tile.z))
        player.filterableMessage("You walk carefully across the slippery log...")
        val success = agilitySuccess(player, 200, 250)
        walkOverTile(player, target.tile, Render.NONE)
        if (success) {
            walkOverTile(player, Tile(2994, 3945), Render.BEAM_BALANCE)
            player.filterableMessage("You skillfully edge across the gap.")
            player.resetRenderAnimation()
            wait(1)
            player.wildernessXp(20.0)
            player.advanceCourse(WILDERNESS_AGILITY_STAGE, 4)
        } else {
            walkOverTile(player, Tile(2998, 3945), Render.BEAM_BALANCE)
            player.filterableMessage("You slip and fall onto the spikes below.")
            player.animate(764)
            face(player, Direction.NORTH)
            wait(1)
            player.moveTo(Tile(2998, 10346))
            player.resetRenderAnimation()
            player.playSound(2504)
            wait(1)
            walkOverTile(player, Tile(2998, 10345), Render.NONE)
            player.hit((player.skills.getMaxLevel(Skills.CONSTITUTION) * 0.15).toInt() + 1)
            player.playSound(519, delay = 20)
        }
    }
}

on_obj_option(obj = Objs.ROCKS_2328, option = "Climb") {
    obstacle { target ->
        walkToTile(player, Tile(target.tile.x, 3937))
        player.filterableMessage("You walk carefully across the slippery log...")
        walkOverTile(player, Tile(target.tile.x, 3933), Render.CLIMBING)
        player.resetRenderAnimation()
        player.filterableMessage("You reach the top.")
        if (player.courseStage(WILDERNESS_AGILITY_STAGE) == 4) {
            player.attr[WILDERNESS_AGILITY_STAGE] = 0
            player.wildernessXp(COMPLETION_BONUS_EXPERIENCE)
            gg.rsmod.plugins.content.skills.summoning.SkillingPets.agilityLap(player, gg.rsmod.plugins.content.skills.summoning.SkillingPets.WILDERNESS_COURSE)
        }
    }
}

/** climb_through_pipe (Void) - the squeeze through the course pipe. */
val ANIM_PIPE = 10580

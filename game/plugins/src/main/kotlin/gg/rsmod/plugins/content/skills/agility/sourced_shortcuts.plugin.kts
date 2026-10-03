package gg.rsmod.plugins.content.skills.agility

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.plugin.Plugin
import java.util.concurrent.ThreadLocalRandom

/**
 * Agility shortcuts ported from Void (`content/skill/agility/shortcut/{Stiles,UnderWallTunnels,Pipes,
 * LogBalance,SteppingStones}.kt`, `area/troll_country/trollheim/Trollheim.kt`, `god_wars_dungeon/
 * GodwarsBoulder.kt`, `al_kharid/AlKharidMine.kt`, `burgh_de_rott/BurghDeRottLowFence.kt`,
 * `karamja/BrimhavenShotcuts.kt`, `tree_gnome_stronghold/TreeGnomeStronghold.kt`, `zanaris/Zanaris.kt`,
 * `ancient_cavern/KuradalsDungeon.kt`, `rellekka/FremennikSlayerDungeon.kt`, `taverley/TaverleyDungeon.kt`).
 * Object, animation, sound and render ids are Void's string ids resolved through its toml data; tiles,
 * levels, experience, messages and exact-move timings are Void's. Void HP damage is x10 life points
 * and is divided by 10 for this server's 1:1 hitpoints.
 *
 * Not ported, recorded instead of guessed:
 * - Draynor (9315) and Shilo waterfall (2333-2335) stepping-stone failures: Void marks the success rate
 *   "Unknown rate", so only the success path is used.
 * - Lumbridge swamp stepping-stone failure extinguishing a light source (no light-source mechanic here).
 * - Brimhaven rope swing object animation (497) and render emote "climbing" (Void id 0): no object
 *   animation API here and id 0 is not a usable render; the movement itself is ported.
 * - Grapple shortcuts (next batch: needs grapple/crossbow equipment checks and temporary rope objects).
 *
 * Every binding is checked against the loaded cache at world init: an id without the option, or an
 * option another plugin already binds, is skipped and logged instead of crashing or double-binding.
 */

val RENDER_ROPE_BALANCE = 155
val RENDER_DROWNING = 163
val RENDER_SWIM = 164

val ANIM_ROCKS_PILE_CLIMB = 839
val ANIM_RAILING_SQUEEZE = 3844
val ANIM_CLIMB_INTO_TUNNEL = 2589
val ANIM_TUNNEL_INVISIBLE = 2590
val ANIM_CLIMB_OUT_OF_TUNNEL = 2591
val ANIM_CLIMB_THROUGH_PIPE = 10580
val ANIM_FALL_OFF_LOG_LEFT = 2581
val ANIM_STEPPING_STONE_STEP = 769
val ANIM_STEPPING_STONE_JUMP = 741
val ANIM_ROPE_WALK_FALL_DOWN = 764
val ANIM_HUMAN_CLIMBING_DOWN = 1148
val ANIM_ROCKS_CLIMB_DOWN = 740
val ANIM_GODWARS_HUMAN_CRAWLING = 7023
val ANIM_MOVE_BOULDER_NORTH = 6978
val ANIM_MOVE_BOULDER_SOUTH = 6979
val ANIM_LOW_FENCE_JUMP = 11574
val ANIM_ROPE_SWING = 751
val ANIM_SPEAR_TRAP_WALK_RIGHT = 3276
val ANIM_SPEAR_TRAP_WALK_LEFT = 3277
val ANIM_SPEAR_TRAP_CAUGHT_RIGHT = 3278
val ANIM_SPEAR_TRAP_CAUGHT_LEFT = 3279
val ANIM_SIDE_HURT_LEFT = 2593
val ANIM_DIVE_PLAYER = 1115
val ANIM_PASS_THROUGH_BARRIER = 10584
val ANIM_CRACK_ENTER = 2594
val ANIM_CRACK_LEAVE = 2595
val ANIM_SHORTCUT_JUMP = 10738
val ANIM_WILD_DITCH_JUMP = 6132

val SOUND_STUMBLE_LOOP = 2493
val SOUND_POOL_PLOP = 1658
val SOUND_JUMP = 2461
val SOUND_GRAPPLE_SPLASH = 2929
val SOUND_SQUEEZE_OUT = 2490
val SOUND_HUMAN_HIT = 514
val SOUND_SPEAR_TRAP_JUMP = 2487
val SOUND_MALE_DEFEND_1 = 519
val SOUND_MALE_DEFEND_2 = 520

/* Shared helpers (agilitySuccess, hasAgility, Tile.add, walkToTile, walkOverTile, exactMove, face,
 * shortcut, obstacle) live in ShortcutSupport.kt so grapple_shortcuts.plugin.kts can use them too. */

// ---- Underwall tunnels (Void UnderWallTunnels.tunnel) ----

/** A climb with no landing in this cache ([chainLanding]): traced as a PROBLEM, and the player stays where he is. */
fun gg.rsmod.game.model.entity.Player.noLanding(target: gg.rsmod.game.model.entity.GameObject) {
    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
        "rocks ${target.id} at ${target.tile.x},${target.tile.z}: no landing across from ${tile.x},${tile.z}"
    }
    message("You can't climb the rocks from here.")
}

fun tunnel(
    id: Int,
    option: String,
    level: Int,
    start: Tile,
    end: Tile,
    direction: Direction,
) = shortcut(id, option) {
    if (!player.hasAgility(level, "You need an Agility level of $level to negotiate this tunnel.")) return@shortcut
    obstacle {
        walkToTile(player, start)
        face(player, direction)
        wait(1)
        player.animate(ANIM_CLIMB_INTO_TUNNEL)
        exactMove(player, start.add(direction), 50, direction)
        player.animate(ANIM_TUNNEL_INVISIBLE)
        exactMove(player, end, 100, direction)
        wait(1)
        player.animate(ANIM_CLIMB_OUT_OF_TUNNEL)
        exactMove(player, end.add(direction), delay = 33, direction = direction, startDelay = 15)
    }
}

// ---- Pipes (Void Pipes.squeezeThroughVertical) ----

fun verticalPipe(
    id: Int,
    requirementZ: Int,
    level: Int,
    north: Int,
    middle: Int,
    south: Int,
    xp: Double,
) = shortcut(id, "Squeeze-through") {
    val target = player.getInteractingGameObj()
    if (target.tile.z == requirementZ &&
        !player.hasAgility(level, "You need an Agility level of $level to squeeze through the pipe.")
    ) {
        return@shortcut
    }
    obstacle {
        val above = player.tile.z >= middle
        val targetTile = Tile(target.tile.x, if (above) south else north)
        walkToTile(player, Tile(target.tile.x, if (above) north else south))
        val direction = if (above) Direction.SOUTH else Direction.NORTH
        face(player, direction)
        player.animate(ANIM_CLIMB_THROUGH_PIPE, delay = 30)
        exactMove(player, Tile(target.tile.x, middle), delay = 126, direction = direction, startDelay = 30)
        player.moveTo(Tile(target.tile.x, targetTile.z - direction.getDeltaZ() * 2))
        player.animate(ANIM_CLIMB_THROUGH_PIPE, delay = 20)
        exactMove(player, targetTile, delay = 96, direction = direction)
        player.addXp(Skills.AGILITY, xp)
    }
}

// ---- Log balances (Void LogBalance / BrimhavenShotcuts.walkAcross) ----

fun brimhavenLog(
    id: Int,
    targetX: Int,
) = shortcut(id, "Walk-across") {
    if (!player.hasAgility(30, "You need an Agility level of 30 to cross the log.")) return@shortcut
    obstacle { target ->
        player.filterableMessage("You walk carefully across the slippery log...")
        player.setRenderAnimation(RENDER_ROPE_BALANCE)
        walkOverTile(player, target.tile, Render.ROPE_BALANCE)
        walkOverTile(player, Tile(targetX, target.tile.z), Render.ROPE_BALANCE)
        player.resetRenderAnimation()
        player.addXp(Skills.AGILITY, 10.0)
        player.filterableMessage("... and make it safely to the other side.")
    }
}

/*
 * Coal Truck log balance (Void LogBalance "coal_truck_log_balance"): 20 Agility, rope-balance render onto the clicked end of the
 * log, then along it to the other end (2602 -> 2598, 2599 -> 2603), 8.5 xp. Was an unbounded collision-free walk from the click
 * tile with the render set after the walk began (conform traversal 2026-10-03: OB7 + through the oak at 2605,3477).
 */
shortcut(Objs.LOG_BALANCE_2296, "Walk-across") {
    if (!player.hasAgility(20, "You need at least 20 Agility to do that.")) return@shortcut
    obstacle { target ->
        player.filterableMessage("You walk carefully across the slippery log...")
        player.setRenderAnimation(RENDER_ROPE_BALANCE)
        walkOverTile(player, target.tile, Render.ROPE_BALANCE)
        when {
            target.tile.x == 2602 && target.tile.z == 3477 -> walkOverTile(player, Tile(2598, 3477, target.tile.height), Render.ROPE_BALANCE)
            target.tile.x == 2599 && target.tile.z == 3477 -> walkOverTile(player, Tile(2603, 3477, target.tile.height), Render.ROPE_BALANCE)
        }
        player.resetRenderAnimation()
        player.addXp(Skills.AGILITY, 8.5)
        player.filterableMessage("... and make it safely to the other side.")
    }
}

fun ardougneLog(
    id: Int,
    direction: Direction,
) = shortcut(id, "Walk-across") {
    if (!player.hasAgility(33)) return@shortcut
    obstacle { target ->
        player.filterableMessage("You attempt to walk across the slippery log.")
        player.setRenderAnimation(RENDER_ROPE_BALANCE)
        walkOverTile(player, target.tile, Render.ROPE_BALANCE)
        val middle = target.tile.add(direction)
        walkOverTile(player, middle, Render.ROPE_BALANCE)
        if (agilitySuccess(player, 90, 250)) {
            walkOverTile(player, middle.add(direction, 2), Render.ROPE_BALANCE)
            player.message("You make it across the log without any problems.")
            player.resetRenderAnimation()
            wait(1)
            player.addXp(Skills.AGILITY, 4.0)
        } else {
            player.message("You lose your footing and fall into the water.")
            player.animate(ANIM_FALL_OFF_LOG_LEFT)
            player.playSound(SOUND_STUMBLE_LOOP)
            exactMove(player, Tile(middle.x, middle.z - 1), delay = 35, direction = direction, startDelay = 22)
            wait(1)
            player.setRenderAnimation(RENDER_SWIM)
            player.playSound(SOUND_POOL_PLOP)
            player.message("You're being washed down the river.")
            walkOverTile(player, Tile(middle.x, middle.z - 1), Render.SWIM)
            player.filterableMessage("You feel like you're drowning...")
            walkOverTile(player, Tile(middle.x, middle.z - 6), Render.SWIM)
            walkOverTile(player, Tile(middle.x, middle.z - 7).add(direction), Render.SWIM)
            player.filterableMessage("You finally come to shore.")
            player.resetRenderAnimation()
            walkOverTile(player, Tile(middle.x, middle.z - 7).add(direction, 2), Render.NONE)
            player.hit(ThreadLocalRandom.current().nextInt(2, 5))
            player.addXp(Skills.AGILITY, 2.0)
        }
    }
}

// ---- Trollheim (Void Trollheim) ----

suspend fun QueueTask.climbDownRocks(
    player: Player,
    anim: Int,
    target: Tile,
    delay: Int,
    direction: Direction,
) {
    player.animate(anim)
    exactMove(player, target, delay, direction)
}

on_world_init {
    // Stiles, fences and the Falador crumbling wall (Void Stiles.kt: freds_farm 45205, catherby 993, death_plateau 3730, falconry
    // 19222, meiyerditch broken fence 18411, ardougne_farm 34776, al_kharid 48208, falador_farm 7527, vinesweeper 29460, falador
    // crumbling wall 11844 at 5 Agility for 0.5 xp, McGrubor's railing 51 with railing_squeeze 3844). No xp and no message for the
    // stiles in Void. The old agility_shortcuts stile code (anim 1560 = a cockatrice sequence, xp before the climb, the lock released
    // after 2 ticks while the walk went on) and crumbling_wall.plugin.kts (a slide from the player's tile +-2) are gone (2026-10-03).
    listOf(993, 3730, 19222, 18411, 45205, 34776, 48208, 7527, 29460).forEach { id ->
        shortcut(id, "Climb-over") { obstacle { climbOver(player, it) } }
    }
    shortcut(11844, "Climb-over") {
        if (!player.hasAgility(5, "You need an Agility level of 5 to climb over this wall.")) return@shortcut
        obstacle { if (climbOver(player, it)) player.addXp(Skills.AGILITY, 0.5) }
    }
    shortcut(51, "Squeeze-through") { obstacle { climbOver(player, it, ANIM_RAILING_SQUEEZE) } }

    // Level corrected 2026-09-18: 2009scape TunnelShortcut sources ids 9301/9302 at level 16, not the
    // 15/21 previously recorded here (which disagreed with each other and with the source).
    tunnel(9302, "Climb-into", 16, Tile(2575, 3112), Tile(2575, 3108), Direction.SOUTH)
    tunnel(9301, "Climb-under", 16, Tile(2575, 3107), Tile(2575, 3111), Direction.NORTH)
    tunnel(9311, "Climb-into", 21, Tile(3138, 3516), Tile(3143, 3514), Direction.EAST)
    tunnel(9312, "Climb-into", 21, Tile(3144, 3514), Tile(3139, 3516), Direction.WEST)
    tunnel(9310, "Climb-into", 26, Tile(2948, 3313), Tile(2948, 3310), Direction.SOUTH)
    tunnel(9309, "Climb-into", 26, Tile(2948, 3309), Tile(2948, 3312), Direction.NORTH)

    // Void Pipes.kt (2026-10-03): brimhaven_pipe_moss 5100 needs 22 at y 9567 for 8.5 xp, brimhaven_pipe_dragon 5099 needs 34 at
    // y 9498 for 10 xp - the OSRS Wiki's 22 (moss giants) / 34 (red dragons). The 2026-09-18 "correction" took 2009scape's
    // swapped numbers.
    verticalPipe(5100, requirementZ = 9567, level = 22, north = 9573, middle = 9570, south = 9566, xp = 8.5)
    verticalPipe(5099, requirementZ = 9498, level = 34, north = 9499, middle = 9495, south = 9492, xp = 10.0)

    shortcut(9293, "Squeeze-through") {
        if (!player.hasAgility(70, "You need an Agility level of 70 to squeeze through the pipe.")) return@shortcut
        obstacle { target ->
            val dir = if (target.tile.x < 2889) Direction.EAST else Direction.WEST
            walkToTile(player, Tile(if (dir == Direction.EAST) 2886 else 2892, 9799))
            player.animate(ANIM_CLIMB_THROUGH_PIPE)
            exactMove(player, Tile(2889, 9799), delay = 96, direction = dir, startDelay = 30)
            player.animate(ANIM_CLIMB_THROUGH_PIPE)
            exactMove(player, Tile(if (dir == Direction.EAST) 2892 else 2886, 9799), delay = 96, direction = dir, startDelay = 30)
            player.addXp(Skills.AGILITY, 10.0)
        }
    }

    ardougneLog(35999, Direction.WEST)
    ardougneLog(35997, Direction.EAST)
    brimhavenLog(5088, 2687)
    brimhavenLog(5090, 2682)

    // ---- Stepping stones (Void SteppingStones) ----
    shortcut(5948, "Jump-across") {
        obstacle { target ->
            val direction = if (player.tile.x > target.tile.x) Direction.WEST else Direction.EAST
            val start = if (direction == Direction.WEST) Tile(3208, 9572) else Tile(3204, 9572)
            val end = if (direction == Direction.WEST) Tile(3204, 9572) else Tile(3208, 9572)
            if (player.tile != start) {
                walkToTile(player, start)
                wait(1)
            }
            player.animate(ANIM_STEPPING_STONE_STEP)
            player.filterableMessage("You leap across with a mighty leap!")
            player.playSound(SOUND_JUMP)
            exactMove(player, target.tile, delay = 70, direction = direction, startDelay = 58)
            if (agilitySuccess(player, 51, 252)) {
                player.animate(ANIM_STEPPING_STONE_STEP)
                player.playSound(SOUND_JUMP)
                exactMove(player, end, delay = 70, direction = direction, startDelay = 58)
                player.addXp(Skills.AGILITY, 3.0)
            } else {
                player.filterableMessage("You slip over on the slimy stone.")
                player.animate(ANIM_ROPE_WALK_FALL_DOWN)
                player.playSound(SOUND_GRAPPLE_SPLASH)
                exactMove(player, Tile(target.tile.x, target.tile.z + direction.getDeltaX()), delay = 40, direction = direction, startDelay = 12)
                player.setRenderAnimation(RENDER_DROWNING)
                wait(2)
                exactMove(player, end, direction = direction)
                player.resetRenderAnimation()
            }
        }
    }

    intArrayOf(2333, 2334, 2335).forEach { id ->
        shortcut(id, "Cross") {
            if (player.skills.getCurrentLevel(Skills.AGILITY) < 30) {
                player.queue { messageBox("The stepping stone looks very small and slippery. You'd better have an Agility level of 30.") }
                return@shortcut
            }
            obstacle { target ->
                if (player.tile == target.tile || !player.tile.isWithinRadius(target.tile, 1)) return@obstacle
                val direction = Direction.between(player.tile, target.tile)
                player.filterableMessage("You attempt to balance on the stepping stone.")
                player.animate(ANIM_STEPPING_STONE_STEP)
                exactMove(player, target.tile, delay = 60, direction = direction, startDelay = 48)
                player.playSound(SOUND_JUMP)
                wait(1)
                player.filterableMessage("You manage to make the jump.")
                player.addXp(Skills.AGILITY, 3.0)
            }
        }
    }

    shortcut(9315, "Jump-onto") {
        if (!player.hasAgility(31, "You need level 31 Agility to tackle this obstacle.")) return@shortcut
        obstacle { target ->
            if (player.tile == target.tile) return@obstacle
            wait(1)
            val direction = Direction.between(player.tile, target.tile)
            player.filterableMessage("You attempt to balance on the stepping stone.")
            player.animate(ANIM_STEPPING_STONE_STEP)
            exactMove(player, target.tile, delay = 70, direction = direction, startDelay = 58)
            player.filterableMessage("You manage to make the jump.")
            player.addXp(Skills.AGILITY, 3.0)
        }
    }

    shortcut(10536, "Jump-to") {
        obstacle { target ->
            val direction = if (target.tile.z > player.tile.z) Direction.NORTH else Direction.SOUTH
            walkToTile(player, Tile(target.tile.x, target.tile.z - direction.getDeltaZ() * 3))
            face(player, direction)
            wait(1)
            if (!player.hasAgility(74, "You need level 74 Agility to tackle this obstacle.")) return@obstacle
            player.animate(ANIM_STEPPING_STONE_JUMP)
            player.playSound(SOUND_JUMP)
            exactMove(player, target.tile, delay = 45, direction = direction, startDelay = 30)
            wait(1)
            player.animate(ANIM_STEPPING_STONE_JUMP)
            player.playSound(SOUND_JUMP)
            exactMove(player, Tile(target.tile.x, target.tile.z + direction.getDeltaZ() * 3), delay = 45, direction = direction, startDelay = 30)
        }
    }

    val brimhavenStones = listOf(Direction.SOUTH, Direction.SOUTH, Direction.WEST, Direction.WEST, Direction.SOUTH, Direction.SOUTH, Direction.SOUTH)
    shortcut(5110, "Jump-from") {
        if (!player.hasAgility(12, "You need an agility level of 12 to attempt to swing on this vine.")) return@shortcut
        obstacle {
            // Void jumps from wherever the player stands; the chain only lands on its stones from this tile (5110 at 2649,9561).
            walkToTile(player, Tile(2649, 9562))
            face(player, Direction.SOUTH)
            player.filterableMessage("You carefully start crossing the stepping stones...")
            for (direction in brimhavenStones) {
                player.animate(ANIM_STEPPING_STONE_JUMP)
                exactMove(player, player.tile.add(direction), direction = direction)
                face(player, direction)
                wait(1)
            }
            player.addXp(Skills.AGILITY, 7.5)
            player.filterableMessage("... You safely cross to the other side.")
        }
    }
    shortcut(5111, "Jump-from") {
        obstacle {
            // The reverse chain lands on its stones only from this tile (5111 at 2647,9558).
            walkToTile(player, Tile(2647, 9557))
            face(player, Direction.NORTH)
            player.filterableMessage("You carefully start crossing the stepping stones...")
            for (step in brimhavenStones.reversed()) {
                val direction = step.getOpposite()
                player.animate(ANIM_STEPPING_STONE_JUMP)
                exactMove(player, player.tile.add(direction), direction = direction)
                face(player, direction)
                wait(1)
            }
            player.addXp(Skills.AGILITY, 7.5)
            player.filterableMessage("... You safely cross to the other side.")
        }
    }

    // ---- Brimhaven rope swings ----
    listOf(Triple(2322, Tile(2709, 3209), Tile(2704, 3209)), Triple(2323, Tile(2705, 3205), Tile(2709, 3205))).forEach { (id, start, end) ->
        shortcut(id, "Swing-on") {
            if (!player.hasAgility(10, "You need an agility level of 10 to attempt to swing on this vine.")) return@shortcut
            obstacle {
                val direction = if (end.x < start.x) Direction.WEST else Direction.EAST
                walkToTile(player, start)
                player.animate(ANIM_ROPE_SWING)
                exactMove(player, end, delay = 70, direction = direction, startDelay = 45)
                player.addXp(Skills.AGILITY, 3.0)
                player.filterableMessage("You skillfully swing across.")
            }
        }
    }

    // ---- Trollheim rocks (Void Trollheim.kt; this 2011 map's own placements) ----
    // The 667 map re-placed every Trollheim rock as one "Cliffside" loc (3803 @2876,3671, 9303 @2855,3663, 9304 @2873,3660,
    // 9305 @2894,3672, 9306 @2909,3685, 3748 x3); Void's tiles (2885,3683 ...) are not on this map. Each climb is the shared
    // climbCliff: a fixed lane through the loc, down = Void's climb-down sequence, up = Void's "climbing" render, levels / xp as Void
    // (9305: OSRS Wiki 47, Void has a TODO). Owner FAIL 2026-10-03 02:24 (walked through the rock) and the stuck / noclip reports
    // 09-30 .. 10-03 were the slides from wherever the click left the player and the plain walk up (OB7).
    listOf(
        // id, level, down sequence, xp down, xp up
        listOf(9306, 0, ANIM_HUMAN_CLIMBING_DOWN, 1, 8),
        listOf(3803, 0, ANIM_ROCKS_CLIMB_DOWN, 1, 8),
        listOf(3804, 0, ANIM_ROCKS_CLIMB_DOWN, 1, 8),
        listOf(9305, 47, ANIM_HUMAN_CLIMBING_DOWN, 8, 8),
        listOf(9304, 43, ANIM_HUMAN_CLIMBING_DOWN, 8, 8),
        listOf(9303, 41, ANIM_HUMAN_CLIMBING_DOWN, 8, 8),
    ).forEach { (id, level, downAnim, xpDown, xpUp) ->
        shortcut(id, "Climb") {
            if (level > 0 && !player.hasAgility(level)) return@shortcut
            obstacle { target ->
                val crossing = cliffCrossing(world, target, player.tile)
                val down = crossing != null && goesDown(world, crossing)
                if (climbCliff(player, target, downAnim)) player.addXp(Skills.AGILITY, (if (down) xpDown else xpUp).toDouble())
            }
        }
    }
    shortcut(3748, "Climb") {
        if (!player.hasAgility(44)) return@shortcut
        obstacle { target ->
            player.message("You climb onto the rock...")
            if (climbCliff(player, target, ANIM_ROCKS_PILE_CLIMB)) player.message("...and step down the other side.")
        }
    }

    // God Wars entrance boulder (667 cache placement: (2907,3709,0)).
    // OSRS allows either 60 Strength (Lift) or 60 Agility (Squeeze past).
    shortcut(Objs.BOULDER_35390, "Lift") {
        if (player.skills.getCurrentLevel(Skills.STRENGTH) < 60) {
            player.message("You need a Strength level of 60 to negotiate this boulder.")
            return@shortcut
        }
        obstacle { target ->
            val north = player.tile.z < target.tile.z
            val start = Tile(target.tile.x, target.tile.z + if (north) -1 else 3)
            val end = Tile(target.tile.x, target.tile.z + if (north) 3 else -1)
            walkToTile(player, start)
            face(player, if (north) Direction.NORTH else Direction.SOUTH)
            player.animate(if (north) ANIM_MOVE_BOULDER_NORTH else ANIM_MOVE_BOULDER_SOUTH)
            wait(3)
            exactMove(player, end, delay = 210, direction = if (north) Direction.NORTH else Direction.SOUTH)
        }
    }
    shortcut(Objs.BOULDER_35390, "Squeeze past") {
        if (!player.hasAgility(60, "You need an Agility level of 60 to squeeze past this boulder.")) return@shortcut
        obstacle { target ->
            val north = player.tile.z < target.tile.z
            val start = Tile(target.tile.x, target.tile.z + if (north) -1 else 3)
            val end = Tile(target.tile.x, target.tile.z + if (north) 3 else -1)
            walkToTile(player, start)
            player.filterableMessage("You try to squeeze past.")
            player.animate(ANIM_GODWARS_HUMAN_CRAWLING)
            exactMove(player, end, delay = 120, direction = if (north) Direction.NORTH else Direction.SOUTH)
        }
    }

    // ---- God Wars Dungeon entrance crack ----
    shortcut(26305, "Crawl-through") {
        if (!player.hasAgility(60, "You need an Agility level of 60 to squeeze through the crack.")) return@shortcut
        obstacle { target ->
            wait(2)
            player.animate(ANIM_GODWARS_HUMAN_CRAWLING)
            wait(3)
            when {
                target.tile.x == 2900 && target.tile.z == 3713 -> player.moveTo(Tile(2904, 3720))
                target.tile.x == 2904 && target.tile.z == 3719 -> player.moveTo(Tile(2899, 3713))
            }
        }
    }

    // ---- Al Kharid mine ----
    shortcut(9332, "Climb") {
        if (!player.hasAgility(38, "You need an Agility level of 38 to negotiate these rocks.")) return@shortcut
        obstacle {
            face(player, Direction.EAST)
            wait(1)
            walkToTile(player, Tile(3303, 3315))
            walkOverTile(player, Tile(3307, 3315), Render.CLIMBING)
        }
    }
    shortcut(9331, "Climb") {
        if (!player.hasAgility(38, "You need an Agility level of 38 to negotiate these rocks.")) return@shortcut
        obstacle {
            // Void walks over the rocks to the edge in the plain walk; here with the climbing render like every other rock walk
            // (Trollheim, Wilderness course 2328) - the plain walk reads as walking through the rock (OB7, conform traversal).
            walkOverTile(player, Tile(3305, 3315), Render.CLIMBING)
            player.resetRenderAnimation()
            face(player, Direction.WEST)
            wait(1)
            climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, Tile(3303, 3315), 120, Direction.EAST)
        }
    }

    // ---- Burgh de Rott low fence ----
    shortcut(12776, "Jump-over") {
        if (!player.hasAgility(25)) return@shortcut
        obstacle { target ->
            val direction = if (player.tile.x < target.tile.x) Direction.EAST else Direction.WEST
            val landing = if (direction == Direction.EAST) target.tile else target.tile.add(Direction.WEST)
            walkOverTile(player, landing.add(direction.getOpposite(), 2), Render.NONE)
            face(player, direction)
            wait(1)
            player.animate(ANIM_LOW_FENCE_JUMP)
            exactMove(player, landing, 60, direction)
        }
    }

    // ---- Tree Gnome Stronghold rocks ----
    shortcut(9316, "Climb") {
        if (!player.hasAgility(37, "You need an Agility level of 37 to negotiate these rocks.")) return@shortcut
        obstacle {
            walkToTile(player, Tile(2486, 3515))
            walkToTile(player, Tile(2487, 3515))
            player.animate(ANIM_HUMAN_CLIMBING_DOWN)
            exactMove(player, Tile(2488, 3516), delay = 80, direction = Direction.SOUTH, startDelay = 20)
            wait(1)
            walkToTile(player, Tile(2489, 3517))
            climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, Tile(2489, 3521), 120, Direction.SOUTH)
        }
    }
    shortcut(9317, "Climb") {
        if (!player.hasAgility(37, "You need an Agility level of 37 to negotiate these rocks.")) return@shortcut
        obstacle {
            walkOverTile(player, Tile(2489, 3521), Render.NONE)
            face(player, Direction.SOUTH)
            walkOverTile(player, Tile(2489, 3519), Render.CLIMBING)
            walkOverTile(player, Tile(2489, 3517), Render.CLIMBING)
            walkOverTile(player, Tile(2488, 3516), Render.CLIMBING)
            walkOverTile(player, Tile(2487, 3515), Render.CLIMBING)
            player.resetRenderAnimation()
            walkToTile(player, Tile(2486, 3515))
        }
    }

    // ---- Zanaris jutting wall ----
    shortcut(12127, "Squeeze-past") {
        val target = player.getInteractingGameObj()
        val level = if (target.tile.x == 2400) 46 else 66
        if (!player.hasAgility(level)) return@shortcut
        obstacle {
            player.filterableMessage("You try to squeeze past.")
            val direction = if (player.tile.z < target.tile.z) Direction.NORTH else Direction.SOUTH
            walkToTile(player, target.tile.add(direction.getOpposite()))
            if (agilitySuccess(player, 50, 254)) {
                face(player, direction)
                player.animate(if (direction == Direction.SOUTH) ANIM_SPEAR_TRAP_WALK_RIGHT else ANIM_SPEAR_TRAP_WALK_LEFT)
                exactMove(player, target.tile.add(direction), delay = 124, direction = direction, startDelay = 28)
                player.playSound(SOUND_SQUEEZE_OUT)
                player.addXp(Skills.AGILITY, 10.0)
            } else {
                player.animate(if (direction == Direction.SOUTH) ANIM_SPEAR_TRAP_CAUGHT_RIGHT else ANIM_SPEAR_TRAP_CAUGHT_LEFT)
                face(player, direction)
                exactMove(player, target.tile, delay = 48, direction = direction, startDelay = 28)
                for (ouch in listOf("Ahhh...", "Owww...", "Arrgghhhh!")) {
                    player.animate(ANIM_SIDE_HURT_LEFT)
                    player.forceChat(ouch)
                    wait(1)
                }
                player.animate(ANIM_DIVE_PLAYER)
                player.playSound(SOUND_HUMAN_HIT)
                player.playSound(SOUND_SPEAR_TRAP_JUMP)
                player.playSound(SOUND_MALE_DEFEND_1)
                player.playSound(SOUND_MALE_DEFEND_2)
                player.hit(2)
                player.hit(2)
                player.addXp(Skills.AGILITY, 6.0)
                exactMove(player, target.tile.add(direction), delay = 20, direction = direction, startDelay = 10)
            }
        }
    }

    // ---- Kuradal's Dungeon ----
    // Level check added 2026-09-18: wiki records this "wall run" at Agility 90; the handler had no
    // requirement at all, so it worked at any level.
    shortcut(47236, "Pass") {
        if (!player.hasAgility(90, "You need an Agility level of 90 to tackle this obstacle.")) return@shortcut
        obstacle { target ->
            val destination =
                when (target.rot) {
                    2 -> {
                        val x = if (player.tile.x <= target.tile.x) target.tile.x + 1 else target.tile.x
                        val z = player.tile.z.coerceIn(target.tile.z, target.tile.z + 1)
                        walkOverTile(player, Tile(player.tile.x, z), Render.NONE)
                        Tile(x, z)
                    }
                    3 -> {
                        val x = player.tile.x.coerceIn(target.tile.x, target.tile.x + 1)
                        val z = if (player.tile.z >= target.tile.z) target.tile.z - 1 else target.tile.z
                        walkOverTile(player, Tile(x, player.tile.z), Render.NONE)
                        Tile(x, z)
                    }
                    else -> return@obstacle
                }
            player.animate(ANIM_PASS_THROUGH_BARRIER)
            exactMove(player, destination)
        }
    }
    shortcut(47233, "Climb-over") {
        if (!player.hasAgility(86)) return@shortcut
        obstacle { target ->
            val north = player.tile.z < target.tile.z
            val start = if (north) Tile(1633, 5292) else Tile(1633, 5294)
            val end = if (north) Tile(1633, 5294) else Tile(1633, 5292)
            val direction = if (north) Direction.NORTH else Direction.SOUTH
            walkOverTile(player, start, Render.NONE)
            face(player, direction)
            wait(1)
            player.animate(ANIM_ROCKS_PILE_CLIMB)
            exactMove(player, end, 30, direction)
        }
    }

    // ---- Fremennik Slayer Dungeon ----
    shortcut(9321, "Squeeze-through") {
        if (!player.hasAgility(62, "You need level 62 agility in order to contort your body through this crack.")) return@shortcut
        obstacle { target ->
            val direction = if (player.tile.x < 2734) Direction.EAST else Direction.WEST
            // Void slides onto the crevice from wherever the player stands; the crawl starts in front of it.
            walkToTile(player, target.tile.add(direction.getOpposite()))
            face(player, direction)
            player.animate(ANIM_CRACK_ENTER)
            exactMove(player, target.tile, direction = direction)
            player.animate(ANIM_TUNNEL_INVISIBLE)
            exactMove(player, Tile(target.tile.x + direction.getDeltaX() * 3, target.tile.z), direction = direction)
            player.animate(ANIM_CRACK_LEAVE)
            exactMove(player, Tile(target.tile.x + direction.getDeltaX() * 4, target.tile.z), direction = direction)
            player.message("You climb your way through the narrow crevice.")
            player.addXp(Skills.AGILITY, 7.5)
        }
    }
    shortcut(44339, "Jump-across") {
        if (!player.hasAgility(81, "You need an agility level of 81 to tackle this obstacle.")) return@shortcut
        obstacle {
            val direction = if (player.tile.x < 2771) Direction.EAST else Direction.WEST
            val start = if (direction == Direction.EAST) Tile(2768, 10002) else Tile(2775, 10002)
            walkToTile(player, start)
            face(player, direction)
            wait(1)
            player.animate(ANIM_SHORTCUT_JUMP)
            exactMove(player, Tile(start.x + direction.getDeltaX() * 7, start.z), delay = 120, direction = direction, startDelay = 30)
            player.message("Your feet skid as you land on the floor.")
            player.addXp(Skills.AGILITY, 10.0)
        }
    }

    /*
     * Batch 2026-09-18: previously-unhandled shortcuts audited against 2009scape's
     * content/global/skill/agility/shortcuts/{CrumblingWallShortcut,TunnelShortcut,PipeShortcut,
     * LogBalanceShortcut,RockClimbShortcut,StrangeFloorShortcut}.* and the OSRS Wiki "Agility
     * shortcuts" page for XP where the donor left it at 0. All ids below were confirmed present in
     * this cache with ObjectPlacementProbeTool before wiring; two 2009scape-sourced shortcuts
     * (Varrock south fence, obj 9300; Karamja volcano grapple tree, obj 17074) are NOT in this
     * revision-667 cache and are intentionally left unbound - SOURCE_BLOCKED, no matching object.
     */

    // ---- Yanille Dungeon pipe (2009scape PipeShortcut, obj 2290) ----
    shortcut(2290, "Squeeze-through") {
        if (!player.hasAgility(49, "You need an Agility level of 49 to squeeze through the pipe.")) return@shortcut
        obstacle {
            val direction = if (player.tile.x < 2575) Direction.EAST else Direction.WEST
            val start = if (direction == Direction.EAST) Tile(2572, 9506) else Tile(2578, 9506)
            walkToTile(player, start)
            player.animate(ANIM_CLIMB_THROUGH_PIPE)
            exactMove(player, Tile(start.x + direction.getDeltaX() * 6, start.z), delay = 90, direction = direction, startDelay = 20)
        }
    }

    // ---- Barbarian Outpost entrance pipe (Void BarbarianOutpost.kt: start (2552,3561) / (2552,3558), walk there, face, climb
    // through to the other end in 60 cycles; requirement 35 with the standard message; no xp in Void). The 2009scape port landed on
    // 3562 / 3556 and refused anyone not already on x 2552.
    shortcut(20210, "Squeeze-through") {
        if (!player.hasAgility(35)) return@shortcut
        obstacle { target ->
            val south = player.tile.z >= 3560
            val start = Tile(2552, if (south) 3561 else 3558)
            if (!player.tile.sameAs(start)) {
                walkToTile(player, start)
                player.faceTile(target.tile)
                wait(1)
            }
            player.animate(ANIM_CLIMB_THROUGH_PIPE)
            exactMove(player, Tile(2552, if (south) 3558 else 3561), delay = 60, direction = if (south) Direction.SOUTH else Direction.NORTH)
        }
    }

    // ---- Taverley Dungeon strange floor to poison spiders (2009scape StrangeFloorShortcut, obj 9294) ----
    shortcut(9294, "Jump-over") {
        if (!player.hasAgility(80, "You need an Agility level of 80 to tackle this obstacle.")) return@shortcut
        obstacle {
            val direction = if (player.tile.x >= 2880) Direction.WEST else Direction.EAST
            val dest = if (direction == Direction.WEST) Tile(2877, 9813) else Tile(2881, 9813)
            player.animate(ANIM_SHORTCUT_JUMP)
            exactMove(player, dest, delay = 90, direction = direction)
            player.addXp(Skills.AGILITY, 12.5)
        }
    }

    // ---- Fremennik Province log balance, Sinclair Mansion <-> Rellekka (2009scape LogBalanceShortcut, obj 9322/9324) ----
    listOf(9322, 9324).forEach { id ->
        shortcut(id, "Walk-across") {
            if (!player.hasAgility(48, "You need an Agility level of 48 to cross this log.")) return@shortcut
            obstacle {
                val north = player.tile.z <= 3594
                val dest = if (north) Tile(2722, 3596) else Tile(2722, 3592)
                player.filterableMessage("You walk carefully across the slippery log...")
                player.setRenderAnimation(RENDER_ROPE_BALANCE)
                walkOverTile(player, dest, Render.ROPE_BALANCE)
                player.resetRenderAnimation()
                player.addXp(Skills.AGILITY, 4.0)
                player.filterableMessage("... and make it safely to the other side.")
            }
        }
    }

    // ---- West-of-Isafdar log balances (2009scape LogBalanceShortcut, obj 3931/3932/3933) ----
    listOf(
        3931 to Pair(Tile(2196, 3237), Tile(2202, 3237)),
        3932 to Pair(Tile(2258, 3250), Tile(2264, 3250)),
        3933 to Pair(Tile(2290, 3232), Tile(2290, 3239)),
    ).forEach { (id, ends) ->
        shortcut(id, "Cross") {
            if (!player.hasAgility(45, "You need an Agility level of 45 to cross this log.")) return@shortcut
            obstacle {
                val (a, b) = ends
                val dest = if (player.tile.getDistance(a) < player.tile.getDistance(b)) b else a
                player.filterableMessage("You carefully cross the log.")
                player.setRenderAnimation(RENDER_ROPE_BALANCE)
                walkOverTile(player, dest, Render.ROPE_BALANCE)
                player.resetRenderAnimation()
                player.addXp(Skills.AGILITY, 1.0)
            }
        }
    }

    // ---- Rock pairs over one cliff band (shared climbPair: from the tile behind the clicked rock to the tile behind its partner,
    // down / up / level by this map's terrain heights). The 2009scape ports slid from wherever the player stood onto the partner
    // rock's own tile (a blocked tile - conform traversal 2026-10-03 "landed in a pocket" on every one).
    // Arandar (Elven Overpass), three tiers: levels 59 / 85 / 68 (OSRS Wiki), no xp in any source.
    val arandarTiers =
        listOf(
            Triple(59, Tile(2333, 3252), Tile(2337, 3253)),
            Triple(85, Tile(2338, 3282), Tile(2338, 3285)),
            Triple(68, Tile(2346, 3299), Tile(2344, 3295)),
        )
    listOf(9296, 9297).forEach { id ->
        shortcut(id, "Climb") {
            val clicked = player.getInteractingGameObj()
            val tier = arandarTiers.firstOrNull { (_, a, b) -> clicked.tile.sameAs(a) || clicked.tile.sameAs(b) } ?: return@shortcut
            val (level, a, b) = tier
            if (!player.hasAgility(level, "You need an Agility level of at least $level to do this.")) return@shortcut
            obstacle { target -> climbPair(player, target, if (target.tile.sameAs(a)) b else a, if (id == 9296) ANIM_HUMAN_CLIMBING_DOWN else ANIM_ROCKS_CLIMB_DOWN) }
        }
    }

    // ---- Eagles' Peak rocks 19849 (Void EaglesEyries.kt): the lower rock (2322,3501) climbs up to (2324,3497) - rocks_climb 4435
    // (delay 15), a 250-cycle slide south, human_walk_forward 819; the two upper rocks climb back down to (2322,3502) -
    // human_walk_backwards 820 (delay 7), a 240-cycle slide, rocks_climb_down 740 (delay 7); 1 xp either way (terrain: 2322,3502
    // h -344, 2324,3497 h -1224 = higher). Added: the walk to the tile in front first.
    shortcut(19849, "Climb") {
        if (!player.hasAgility(25, "You must have an Agility level of at least 25 to climb these rocks.")) return@shortcut
        obstacle { target ->
            if (target.tile.x == 2322 && target.tile.z == 3501) {
                walkToTile(player, Tile(2322, 3502))
                face(player, Direction.SOUTH)
                player.animate(4435, delay = 15)
                exactMove(player, Tile(2324, 3497), delay = 250, direction = Direction.SOUTH)
                player.animate(819)
                wait(1)
            } else {
                walkToTile(player, Tile(2324, 3497))
                face(player, Direction.SOUTH)
                player.animate(820, delay = 7)
                exactMove(player, Tile(2322, 3502), delay = 240, direction = Direction.SOUTH)
                player.animate(ANIM_ROCKS_CLIMB_DOWN, delay = 7)
                wait(1)
            }
            player.animate(-1)
            player.addXp(Skills.AGILITY, 1.0)
        }
    }

    // ---- God Wars Dungeon rocky handholds, two pairs on the way in (level 60, no xp in any source).
    listOf(26327 to Tile(2943, 3768), 26328 to Tile(2949, 3767), 26324 to Tile(2928, 3758), 26323 to Tile(2927, 3760)).forEach { (id, partner) ->
        shortcut(id, "Climb") {
            if (!player.hasAgility(60, "You need an Agility level of at least 60 to do this.")) return@shortcut
            obstacle { target -> climbPair(player, target, partner, ANIM_ROCKS_CLIMB_DOWN) }
        }
    }

    // ---- Karamja rocks 2231, three lanes of two rocks (2792,z) / (2794,z), no requirement.
    shortcut(2231, "Climb") {
        obstacle { target -> climbPair(player, target, Tile(if (target.tile.x == 2792) 2794 else 2792, target.tile.z), ANIM_ROCKS_CLIMB_DOWN) }
    }
    shortcut(9335, "Climb") {
        obstacle {
            player.animate(ANIM_ROCKS_CLIMB_DOWN)
            exactMove(player, Tile(3425, 3476), direction = Direction.between(player.tile, Tile(3425, 3476)))
        }
    }
    shortcut(9336, "Climb") {
        obstacle {
            player.animate(ANIM_HUMAN_CLIMBING_DOWN)
            exactMove(player, Tile(3426, 3477), direction = Direction.between(player.tile, Tile(3426, 3477)))
        }
    }

    // ---- Brimhaven Dungeon monkey bars, red dragons <-> black demons (2009scape MonkeyBarShortcut, obj 2321) ----
    shortcut(2321, "Swing across") {
        if (!player.hasAgility(57, "You need an Agility level of 57 to swing across the monkey bars.")) return@shortcut
        obstacle {
            val south = player.tile.z >= 9494
            val direction = if (south) Direction.SOUTH else Direction.NORTH
            val dest = if (south) Tile(2599, 9489) else Tile(2599, 9494)
            // The 2009scape failure fell to (2599,9564) - a 70-tile slide out of the bars' cave; no donor has a failure here
            // (Void/Novite none), so the crossing always succeeds (SOURCE_BLOCKED: failure rate and landing, 2026-10-03).
            walkToTile(player, Tile(2599, if (south) 9494 else 9489))
            face(player, direction)
            player.animate(Anims.AGIL_JUMP_UP_BARS)
            wait(1)
            player.animate(Anims.AGIL_SWING_BARS)
            exactMove(player, dest, delay = 150, direction = direction, startDelay = 30)
            player.addXp(Skills.AGILITY, 20.0)
        }
    }

    // ---- Wilderness ditch (Void WildernessWall.kt, obj 1440-1444; Novite ObjectHandler 439-452) ----
    // Void: wild_ditch_jump 6132 and a 60-cycle slide to the far side of the 3x2 segment (wall.y + 2 / wall.y - 1; the vertical
    // segment at 2996,3531 across x). The far side is the wall's, never "three tiles from wherever the player stands" (the old
    // objs/wilderness_wall handler, which shadowed this one until 2026-10-03 and slid along or into the ditch from a segment's end).
    // Added on top of Void: the walk to the tile right in front of the segment first (owner priority #1, a slide never starts from an
    // arbitrary tile) - Deadman escapes and pursuits cross here all the time.
    (1440..1444).forEach { id ->
        shortcut(id, "Cross") {
            obstacle { target ->
                val crossing = shortAxisCrossing(world, target, player.tile)
                if (crossing == null) {
                    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                        "ditch ${target.id} at ${target.tile.x},${target.tile.z}: no lane from ${player.tile.x},${player.tile.z}"
                    }
                    player.message("You can't cross the ditch from here.")
                    return@obstacle
                }
                walkToTile(player, crossing.start)
                face(player, crossing.direction)
                player.animate(ANIM_WILD_DITCH_JUMP)
                exactMove(player, crossing.landing, delay = 60, direction = crossing.direction)
            }
        }
    }

    // ---- Port Phasmatys weathered wall to the Ectopool (2009scape PhasmatysZone, obj 9307/9308) ----
    shortcut(9307, "Jump-up") {
        if (!player.hasAgility(58, "You need an Agility level of at least 58 to climb up this wall.")) return@shortcut
        obstacle { player.moveTo(Tile(3670, 9888, 3)) }
    }
    shortcut(9308, "Jump-down") {
        if (!player.hasAgility(58, "You need an Agility level of at least 58 to climb down this wall.")) return@shortcut
        obstacle { player.moveTo(Tile(3671, 9888, 2)) }
    }

    // ---- Slayer Tower spiked chains (Void SlayerTower.kt takeOff/land + ObjectTeleports/Stairs.kt: a chain is climbed like a
    // ladder) - requirement by the chain's tile (3422,3550: 61, else 71), Level.success(level, 90) "unknown success rate", a failed
    // climb hurts (Void 20 = 2 here) and still climbs, xp 3 / 6; climb_up 828 / climb_down 827, two ticks, then the player's own
    // tile one floor up / down. Replaces areas/slayertower/spikey_chain.plugin.kts (no roll, no damage, no xp), 2026-10-03.
    listOf(9319 to "Climb-up", 9320 to "Climb-down").forEach { (id, option) ->
        shortcut(id, option) {
            val target = player.getInteractingGameObj()
            val req = if (target.tile.x == 3422 && target.tile.z == 3550) 61 else 71
            if (!player.hasAgility(req, "You need an Agility level of $req to negotiate this obstacle.")) return@shortcut
            obstacle {
                val up = id == 9319
                val success = agilitySuccessUntil(player, 90)
                if (!success) {
                    player.hit(2)
                    player.filterableMessage("You rip your hands to pieces on the chain as you climb.")
                }
                player.animate(if (up) 828 else 827)
                wait(2)
                player.moveTo(gg.rsmod.plugins.api.ext.climbLanding(player, Tile(player.tile.x, player.tile.z, player.tile.height + if (up) 1 else -1)))
                player.addXp(Skills.AGILITY, if (success) 3.0 else 6.0)
            }
        }
    }

    // ---- Rellekka broken bridge and rockslide (2009scape RellekkaZone.java, obj 4615/4616/5847) ----
    listOf(4615 to Direction.EAST, 4616 to Direction.WEST).forEach { (id, direction) ->
        shortcut(id, "Cross") {
            obstacle { target ->
                walkToTile(player, target.tile)
                player.animate(ANIM_DIVE_PLAYER)
                exactMove(player, target.tile.add(direction, 4), delay = 70, direction = direction)
                player.addXp(Skills.AGILITY, 1.0)
            }
        }
    }
    // The rockslide (1x5, x 2760, z 3651-3655) closes the gap between the west and east ground (map dump 2026-10-03): it is climbed
    // across x on the player's own lane, over the rocks only - the 2009scape port slid 3 tiles north / south from the player's tile
    // along it and landed in the rocks.
    shortcut(5847, "Climb-over") {
        obstacle { target ->
            val crossing = shortAxisCrossing(world, target, player.tile)
            if (crossing == null) {
                player.message("You can't climb over from here.")
                return@obstacle
            }
            walkToTile(player, crossing.start)
            face(player, crossing.direction)
            wait(1)
            player.animate(ANIM_ROCKS_PILE_CLIMB, delay = 30)
            exactMove(player, crossing.landing, delay = 94, direction = crossing.direction, startDelay = 30)
            player.addXp(Skills.AGILITY, 1.0)
        }
    }

    // ---- Basalt rock chain, Barbarian Outpost beach <-> Fremennik Lighthouse rocky shore
    // (2009scape BasaltRockShortcut.kt, obj 4550-4559). Level 1, no xp, in the source: clicking a
    // rock jumps the player onto that rock's own tile, and only fails when already standing there
    // (the donor's per-id branches all reduce to exactly this once the fixed "from" side is dropped -
    // there is no real per-id direction logic, just "walk to this rock"). Ids 4550-4559 are placed
    // twice in this cache: once at the authentic coastal chain confirmed against the donor's own
    // coordinates (region 10040, guarded below by exact tile), and once more near (2450-2458,4555-
    // 4579) in an unrelated instance this shortcut deliberately does not touch, since only the
    // coastal chain's tiles were verified with ObjectPlacementProbeTool. ----
    val basaltChain =
        listOf(
            4550 to Pair(Tile(2522, 3595, 1), "Jump-to"), 4551 to Pair(Tile(2522, 3597, 1), "Jump-across"),
            4552 to Pair(Tile(2522, 3600, 1), "Jump-across"), 4553 to Pair(Tile(2522, 3602, 1), "Jump-across"),
            4554 to Pair(Tile(2518, 3611, 1), "Jump-across"), 4555 to Pair(Tile(2516, 3611, 1), "Jump-across"),
            4556 to Pair(Tile(2514, 3613, 1), "Jump-across"), 4557 to Pair(Tile(2514, 3615, 1), "Jump-across"),
            4558 to Pair(Tile(2514, 3617, 1), "Jump-across"), 4559 to Pair(Tile(2514, 3619, 1), "Jump-to"),
        )
    basaltChain.forEach { (id, pair) ->
        val (tile, option) = pair
        shortcut(id, option) {
            obstacle { target ->
                if (target.tile != tile) return@obstacle
                if (player.tile == tile) {
                    player.message("I can't jump from here.")
                    return@obstacle
                }
                player.lockingQueue(lockState = LockState.FULL) {
                    player.animate(ANIM_STEPPING_STONE_STEP)
                    exactMove(player, tile, direction = Direction.between(player.tile, tile))
                }
            }
        }
    }
    // The Gnome Stronghold course lives in gnome_agility.plugin.kts (Void port, 2026-10-03).
}

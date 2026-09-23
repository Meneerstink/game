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

// ---- Stiles (Void Stiles.climbStile) ----

suspend fun QueueTask.climbStile(
    player: Player,
    target: GameObject,
    rotation: Direction,
    anim: Int = ANIM_ROCKS_PILE_CLIMB,
) {
    val direction =
        when (rotation) {
            Direction.NORTH -> if (player.tile.z > target.tile.z) Direction.SOUTH else Direction.NORTH
            Direction.EAST -> if (player.tile.x > target.tile.x) Direction.WEST else Direction.EAST
            Direction.WEST -> if (player.tile.x < target.tile.x) Direction.EAST else Direction.WEST
            else -> if (player.tile.z < target.tile.z) Direction.NORTH else Direction.SOUTH
        }
    val start = if (direction == rotation) target.tile else target.tile.add(direction.getOpposite())
    walkOverTile(player, start)
    face(player, direction)
    wait(1)
    player.animate(anim)
    val end = if (direction == rotation) target.tile.add(direction) else target.tile
    exactMove(player, end, 30, direction)
}

// ---- Underwall tunnels (Void UnderWallTunnels.tunnel) ----

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
        player.animate(ANIM_CLIMB_THROUGH_PIPE)
        exactMove(player, Tile(target.tile.x, middle), delay = 126, direction = direction, startDelay = 30)
        player.moveTo(Tile(target.tile.x, targetTile.z - direction.getDeltaZ() * 2))
        player.animate(ANIM_CLIMB_THROUGH_PIPE)
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
        walkOverTile(player, target.tile)
        walkOverTile(player, Tile(targetX, target.tile.z))
        player.resetRenderAnimation()
        player.addXp(Skills.AGILITY, 10.0)
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
        walkOverTile(player, target.tile)
        val middle = target.tile.add(direction)
        walkOverTile(player, middle)
        if (agilitySuccess(player, 90, 250)) {
            walkOverTile(player, middle.add(direction, 2))
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
            walkOverTile(player, Tile(middle.x, middle.z - 1))
            player.filterableMessage("You feel like you're drowning...")
            walkOverTile(player, Tile(middle.x, middle.z - 6))
            walkOverTile(player, Tile(middle.x, middle.z - 7).add(direction))
            player.filterableMessage("You finally come to shore.")
            player.resetRenderAnimation()
            walkOverTile(player, Tile(middle.x, middle.z - 7).add(direction, 2))
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
    // Stiles not already covered by agility_shortcuts.plugin.kts.
    listOf(993 to Direction.NORTH, 3730 to Direction.NORTH, 19222 to Direction.NORTH, 18411 to Direction.NORTH).forEach { (id, axis) ->
        shortcut(id, "Climb-over") { obstacle { climbStile(player, it, axis) } }
    }
    shortcut(51, "Squeeze-through") { obstacle { climbStile(player, it, Direction.WEST, ANIM_RAILING_SQUEEZE) } }

    // Level corrected 2026-09-18: 2009scape TunnelShortcut sources ids 9301/9302 at level 16, not the
    // 15/21 previously recorded here (which disagreed with each other and with the source).
    tunnel(9302, "Climb-into", 16, Tile(2575, 3112), Tile(2575, 3108), Direction.SOUTH)
    tunnel(9301, "Climb-under", 16, Tile(2575, 3107), Tile(2575, 3111), Direction.NORTH)
    tunnel(9311, "Climb-into", 21, Tile(3138, 3516), Tile(3143, 3514), Direction.EAST)
    tunnel(9312, "Climb-into", 21, Tile(3144, 3514), Tile(3139, 3516), Direction.WEST)
    tunnel(9310, "Climb-into", 26, Tile(2948, 3313), Tile(2948, 3310), Direction.SOUTH)
    tunnel(9309, "Climb-into", 26, Tile(2948, 3309), Tile(2948, 3312), Direction.NORTH)

    // Level/xp corrected 2026-09-18: 2009scape PipeShortcut sources id 5099 (Red dragons -> Black
    // demons, z~9498) at level 22/8.5xp and id 5100 (Moss giants -> Moss giants, z~9567) at level
    // 34/10.0xp; this file had the two values swapped between the ids.
    verticalPipe(5100, requirementZ = 9567, level = 34, north = 9573, middle = 9570, south = 9566, xp = 10.0)
    verticalPipe(5099, requirementZ = 9498, level = 22, north = 9499, middle = 9495, south = 9492, xp = 8.5)

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

    // ---- Trollheim rocks ----
    shortcut(9306, "Climb") {
        obstacle { target ->
            player.faceTile(target.tile)
            if (target.tile.x == 2901 && target.tile.z == 3680) {
                climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, Tile(2903, 3680), 120, Direction.WEST)
                player.addXp(Skills.AGILITY, 1.0)
            } else if (target.tile.x == 2902 && target.tile.z == 3680) {
                walkOverTile(player, Tile(2900, 3680))
                player.addXp(Skills.AGILITY, 8.0)
            }
        }
    }
    // The local 667 cache has the 3803 climb at (2876,3671); the old donor
    // coordinate was a different map revision. Keep both sourced placements.
    listOf(
        3804 to Tile(2885, 3684),
        3803 to Tile(2885, 3683),
    ).forEach { (id, westTile) ->
        shortcut(id, "Climb") {
            obstacle { target ->
                player.faceTile(target.tile)
                if ((target.tile.x == westTile.x && target.tile.z == westTile.z) ||
                    (id == 3803 && target.tile.x == 2876 && target.tile.z == 3671)
                ) {
                    if (player.tile.x >= target.tile.x) {
                        climbDownRocks(player, ANIM_ROCKS_CLIMB_DOWN, Tile(target.tile.x - 1, target.tile.z), 50, Direction.EAST)
                        player.addXp(Skills.AGILITY, 1.0)
                    } else {
                        walkOverTile(player, Tile(target.tile.x + 1, target.tile.z))
                        player.addXp(Skills.AGILITY, 8.0)
                    }
                } else if (target.tile.z == 3661) {
                    if (player.tile.z >= target.tile.z) {
                        climbDownRocks(player, ANIM_ROCKS_CLIMB_DOWN, Tile(target.tile.x, target.tile.z - 1), 50, Direction.NORTH)
                    } else {
                        walkOverTile(player, Tile(target.tile.x, target.tile.z + 1))
                    }
                    player.addXp(Skills.AGILITY, 1.0)
                }
            }
        }
    }
    shortcut(9305, "Climb") {
        if (!player.hasAgility(47)) return@shortcut
        obstacle { target ->
            player.faceTile(target.tile)
            if ((target.tile.x == 2908 && target.tile.z == 3682) ||
                (target.tile.x == 2894 && target.tile.z == 3672)
            ) {
                climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, target.tile, 40, Direction.WEST)
                val landing =
                    if (target.tile.x == 2894) Tile(2895, 3674) else Tile(2909, 3684)
                climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, landing, 120, Direction.SOUTH)
                player.addXp(Skills.AGILITY, 8.0)
            } else if ((target.tile.x == 2909 && target.tile.z == 3683) ||
                (target.tile.x == 2895 && target.tile.z == 3673)
            ) {
                val middle = if (target.tile.x == 2895) Tile(2895, 3672) else Tile(2909, 3682)
                val landing = if (target.tile.x == 2895) Tile(2893, 3672) else Tile(2907, 3682)
                walkOverTile(player, middle)
                wait(1)
                walkOverTile(player, landing)
                player.addXp(Skills.AGILITY, 8.0)
            }
        }
    }
    shortcut(3748, "Climb") {
        if (!player.hasAgility(44)) return@shortcut
        obstacle { target ->
            player.faceTile(target.tile)
            player.message("You climb onto the rock...")
            player.animate(ANIM_ROCKS_PILE_CLIMB)
            val direction = if (player.tile.x >= target.tile.x) Direction.WEST else Direction.EAST
            exactMove(player, target.tile.add(direction), delay = 94, direction = direction, startDelay = 30)
            player.message("...and step down the other side.")
        }
    }
    shortcut(9304, "Climb") {
        if (!player.hasAgility(43)) return@shortcut
        obstacle { target ->
            if (player.tile.z >= target.tile.z) {
                climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, Tile(target.tile.x, target.tile.z - 2), 120, Direction.NORTH)
            } else {
                walkOverTile(player, Tile(target.tile.x, target.tile.z + 2))
            }
            player.addXp(Skills.AGILITY, 8.0)
        }
    }
    shortcut(9303, "Climb") {
        if (!player.hasAgility(41)) return@shortcut
        obstacle { target ->
            if (player.tile.x >= target.tile.x) {
                climbDownRocks(player, ANIM_HUMAN_CLIMBING_DOWN, Tile(target.tile.x - 2, target.tile.z), 120, Direction.EAST)
            } else {
                walkOverTile(player, Tile(target.tile.x + 2, target.tile.z))
            }
            player.addXp(Skills.AGILITY, 8.0)
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
            walkOverTile(player, Tile(3307, 3315))
        }
    }
    shortcut(9331, "Climb") {
        if (!player.hasAgility(38, "You need an Agility level of 38 to negotiate these rocks.")) return@shortcut
        obstacle {
            walkOverTile(player, Tile(3305, 3315))
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
            walkOverTile(player, landing.add(direction.getOpposite(), 2))
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
            walkOverTile(player, Tile(2489, 3521))
            face(player, Direction.SOUTH)
            walkOverTile(player, Tile(2489, 3519))
            walkOverTile(player, Tile(2489, 3517))
            walkOverTile(player, Tile(2488, 3516))
            walkOverTile(player, Tile(2487, 3515))
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
                        walkOverTile(player, Tile(player.tile.x, z))
                        Tile(x, z)
                    }
                    3 -> {
                        val x = player.tile.x.coerceIn(target.tile.x, target.tile.x + 1)
                        val z = if (player.tile.z >= target.tile.z) target.tile.z - 1 else target.tile.z
                        walkOverTile(player, Tile(x, player.tile.z))
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
            walkOverTile(player, start)
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

    // ---- Falador crumbling wall (2009scape CrumblingWallShortcut, obj 11844) ----
    shortcut(11844, "Climb-over") {
        if (!player.hasAgility(5, "You need an Agility level of 5 to climb over this wall.")) return@shortcut
        obstacle {
            val east = player.tile.x >= 2936
            val dest = if (east) Tile(2934, 3355) else Tile(2936, 3355)
            player.animate(ANIM_ROCKS_PILE_CLIMB)
            exactMove(player, dest, delay = 40, direction = if (east) Direction.WEST else Direction.EAST)
            player.addXp(Skills.AGILITY, 0.5)
        }
    }

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

    // ---- Barbarian Outpost obstacle pipe (2009scape PipeShortcut, obj 20210; not part of the course) ----
    shortcut(20210, "Squeeze-through") {
        if (player.tile.x != 2552) {
            player.message("I can't get into this pipe at that angle.")
            return@shortcut
        }
        if (!player.hasAgility(35, "You need an Agility level of 35 to squeeze through the pipe.")) return@shortcut
        obstacle {
            val direction = if (player.tile.z < 3559) Direction.NORTH else Direction.SOUTH
            player.animate(ANIM_CLIMB_THROUGH_PIPE)
            exactMove(player, Tile(2552, 3559 + direction.getDeltaZ() * 3), delay = 90, direction = direction)
            player.addXp(Skills.AGILITY, 10.0)
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
                walkOverTile(player, dest)
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
                walkOverTile(player, dest)
                player.resetRenderAnimation()
                player.addXp(Skills.AGILITY, 1.0)
            }
        }
    }

    // ---- Arandar (Elven Overpass) rock climbs, three tiers (2009scape RockClimbShortcut, obj 9296/9297) ----
    val arandarTiers =
        listOf(
            Triple(59, Tile(2346, 3299), Tile(2344, 3295)),
            Triple(85, Tile(2338, 3282), Tile(2338, 3285)),
            Triple(68, Tile(2333, 3252), Tile(2337, 3253)),
        )
    listOf(9296, 9297).forEach { id ->
        shortcut(id, "Climb") {
            obstacle { target ->
                val tier = arandarTiers.firstOrNull { (_, a, b) -> target.tile == a || target.tile == b } ?: return@obstacle
                val (level, from, to) = tier
                if (!player.hasAgility(level, "You need an Agility level of at least $level to do this.")) return@obstacle
                val dest = if (target.tile == from) to else from
                val direction = Direction.between(player.tile, dest)
                player.animate(if (id == 9296) ANIM_HUMAN_CLIMBING_DOWN else ANIM_ROCKS_CLIMB_DOWN)
                exactMove(player, dest, direction = direction)
            }
        }
    }

    // ---- Eagles' Peak rocks (2009scape RockClimbShortcut, obj 19849) ----
    shortcut(19849, "Climb") {
        if (!player.hasAgility(25, "You need an Agility level of at least 25 to do this.")) return@shortcut
        obstacle {
            val west = player.tile.x <= 2322
            val dest = if (west) Tile(2324, 3497) else Tile(2322, 3502)
            player.animate(if (west) ANIM_ROCKS_CLIMB_DOWN else ANIM_HUMAN_CLIMBING_DOWN)
            exactMove(player, dest, direction = Direction.SOUTH)
        }
    }

    // ---- God Wars Dungeon rocky handholds, separate from the entrance boulder/crack (2009scape
    // RockClimbShortcut, obj 26323/26324/26327/26328) ----
    listOf(
        26327 to Tile(2942, 3768),
        26328 to Tile(2950, 3767),
        26324 to Tile(2928, 3757),
        26323 to Tile(2927, 3761),
    ).forEach { (id, dest) ->
        shortcut(id, "Climb") {
            if (!player.hasAgility(60, "You need an Agility level of at least 60 to do this.")) return@shortcut
            obstacle {
                val direction = Direction.between(player.tile, dest)
                player.animate(ANIM_ROCKS_CLIMB_DOWN)
                exactMove(player, dest, direction = direction)
            }
        }
    }

    // ---- Low-level, no-requirement rocks (2009scape RockClimbShortcut base level 1, obj 2231/9335/9336) ----
    shortcut(2231, "Climb") {
        obstacle {
            val west = player.tile.x <= 2791
            val dest = if (west) Tile(2794, player.tile.z) else Tile(2792, player.tile.z)
            player.animate(if (west) ANIM_ROCKS_CLIMB_DOWN else ANIM_HUMAN_CLIMBING_DOWN)
            exactMove(player, dest, direction = Direction.between(player.tile, dest))
        }
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
            player.animate(Anims.AGIL_JUMP_UP_BARS)
            // 2009scape AgilityHandler.hasFailed(level=57, failChance=0.01): rate falls to ~0 once the
            // player's level clears 57 by more than a couple of levels, so this is a low, level-scaled chance.
            val levelDiff = (player.skills.getCurrentLevel(Skills.AGILITY) - 57).coerceAtMost(69)
            val failed = levelDiff <= 69 && (1 + levelDiff) * 0.01 * ThreadLocalRandom.current().nextDouble() <= 0.01 * ThreadLocalRandom.current().nextDouble()
            if (failed) {
                player.animate(Anims.AGIL_SWING_BARS)
                exactMove(player, player.tile.add(direction), direction = direction)
                player.animate(768)
                player.hit(ThreadLocalRandom.current().nextInt(1, 4))
                exactMove(player, Tile(2599, 9564), delay = 60, direction = direction)
            } else {
                player.animate(Anims.AGIL_SWING_BARS)
                exactMove(player, dest, delay = 150, direction = direction, startDelay = 30)
                player.addXp(Skills.AGILITY, 20.0)
            }
        }
    }

    // ---- Wilderness ditch (border crossing, no level/xp - Void WildernessWall.kt, obj 1440-1444) ----
    // Not an agility-skill obstacle, but was entirely unbound: every "Cross" click on the border ditch
    // around the whole wilderness did nothing, so this is the shared root cause for "can't cross into
    // the wilderness here" reports at any of the five wall variants.
    (1440..1444).forEach { id ->
        shortcut(id, "Cross") {
            obstacle { target ->
                if (id == 1440 && target.tile.x == 2996 && target.tile.z == 3531) {
                    val direction = if (player.tile.x < target.tile.x) Direction.EAST else Direction.WEST
                    player.animate(6132)
                    exactMove(player, Tile(target.tile.x + if (direction == Direction.EAST) 2 else -1, player.tile.z), delay = 60, direction = direction)
                    return@obstacle
                }
                val direction = if (player.tile.z < target.tile.z) Direction.NORTH else Direction.SOUTH
                player.animate(6132)
                exactMove(player, Tile(player.tile.x, target.tile.z + if (direction == Direction.NORTH) 2 else -1), delay = 60, direction = direction)
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

    // ---- Slayer Tower spiked chains, medium (3422,3550, level61) and advanced (3447,3576, level71)
    // (Void SlayerTower.kt: obj 9319 "Climb-up" always ground->up, obj 9320 "Climb-down" always down) ----
    shortcut(9319, "Climb-up") {
        obstacle { target ->
            val req = if (target.tile.x == 3422 && target.tile.z == 3550) 61 else 71
            if (!player.hasAgility(req, "You need an Agility level of $req to negotiate this obstacle.")) return@obstacle
            // Void: Level.success(level, 90) - "Unknown success rate", flat 90/256 regardless of level.
            val success = agilitySuccess(player, 90, 90)
            if (!success) {
                player.hit(20)
                player.filterableMessage("You rip your hands to pieces on the chain as you climb.")
            }
            player.moveTo(Tile(target.tile.x, target.tile.z, target.tile.height + 1))
            player.addXp(Skills.AGILITY, if (success) 3.0 else 6.0)
        }
    }
    shortcut(9320, "Climb-down") {
        obstacle { target ->
            val req = if (target.tile.x == 3422 && target.tile.z == 3550) 61 else 71
            if (!player.hasAgility(req, "You need an Agility level of $req to negotiate this obstacle.")) return@obstacle
            val success = agilitySuccess(player, 90, 90)
            if (!success) {
                player.hit(20)
                player.filterableMessage("You rip your hands to pieces on the chain as you climb.")
            }
            player.moveTo(Tile(target.tile.x, target.tile.z, target.tile.height - 1))
            player.addXp(Skills.AGILITY, if (success) 3.0 else 6.0)
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
    shortcut(5847, "Climb-over") {
        obstacle {
            val direction = if (player.tile.z <= 3657) Direction.NORTH else Direction.SOUTH
            player.animate(ANIM_ROCKS_PILE_CLIMB)
            exactMove(player, player.tile.add(direction, 3), delay = 90, direction = direction)
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

    // ---- Gnome Stronghold agility course (2009scape GnomeStrongholdCourse.kt) ----
    // A different, unrelated OSRS location from the "Tree Gnome Stronghold rocks" shortcut already
    // above and from gnome_agility.plugin.kts's Tree Gnome Village course. No level requirement is
    // sourced for any of these obstacles - the donor gates none of them, so none is added here.
    // Trainer NPC dialogue (TRAINERS[0..4] in the donor) is flavour text only and is not ported;
    // every obstacle's own crossing/xp/animation is sourced and functional without it. Obj
    // 4058/154 (the pipe obstacle) does not exist with a matching option in this cache and is
    // skipped, same as every other cache-verified-first shortcut in this file.
    shortcut(2295, "Walk-across") {
        obstacle {
            walkToTile(player, Tile(2474, 3436))
            player.filterableMessage("You walk carefully across the slippery log...")
            player.setRenderAnimation(RENDER_ROPE_BALANCE)
            walkOverTile(player, Tile(2474, 3429))
            player.resetRenderAnimation()
            player.addXp(Skills.AGILITY, 7.5)
            player.filterableMessage("...You make it safely to the other side.")
        }
    }
    shortcut(2285, "Climb-over") {
        obstacle { target ->
            player.filterableMessage("You climb the netting...")
            player.animate(828)
            exactMove(player, Tile(target.tile.x, target.tile.z - 1, target.tile.height + 1), delay = 60, direction = Direction.NORTH)
            player.addXp(Skills.AGILITY, 7.5)
        }
    }
    shortcut(35970, "Climb") {
        obstacle {
            player.filterableMessage("You climb the tree...")
            player.animate(828)
            exactMove(player, Tile(2473, 3420, 2), delay = 60)
            player.addXp(Skills.AGILITY, 5.0)
            player.filterableMessage("...To the platform above.")
        }
    }
    shortcut(2312, "Walk-on") {
        obstacle {
            player.filterableMessage("You carefully cross the tightrope.")
            player.setRenderAnimation(RENDER_ROPE_BALANCE)
            walkOverTile(player, Tile(2483, 3420, 2))
            player.resetRenderAnimation()
            player.addXp(Skills.AGILITY, 7.5)
        }
    }
    listOf(2314, 2315).forEach { id ->
        shortcut(id, "Climb-down") {
            obstacle {
                player.filterableMessage("You climb down the tree...")
                player.animate(828)
                exactMove(player, Tile(2487, 3420, 0), delay = 60)
                player.addXp(Skills.AGILITY, 5.0)
                player.filterableMessage("You land on the ground.")
            }
        }
    }
    shortcut(2286, "Climb-over") {
        obstacle {
            face(player, Direction.SOUTH)
            player.filterableMessage("You climb the netting...")
            player.animate(828)
            exactMove(player, player.tile.add(Direction.SOUTH, 2), delay = 60, direction = Direction.SOUTH)
            player.addXp(Skills.AGILITY, 7.5)
        }
    }
    shortcut(4059, "Walk-on") {
        obstacle { player.message("You can't do that from here.") }
    }
}

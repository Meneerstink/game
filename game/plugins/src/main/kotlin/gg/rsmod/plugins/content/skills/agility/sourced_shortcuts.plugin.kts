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
// Void god_wars_dungeon.anims.toml godwars_boulder_move / godwars_boulder_rollback (loc animations of 35390).
val ANIM_GODWARS_BOULDER_MOVE = 6980
val ANIM_GODWARS_BOULDER_ROLLBACK = 6981
// Void god_wars_dungeon.sounds.toml godwars_move_boulder / godwars_boulder_rollback.
val SFX_GODWARS_MOVE_BOULDER = 3840
val SFX_GODWARS_BOULDER_ROLLBACK = 3843
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

// ---- Underwall tunnels ----

/** A climb with no landing in this cache ([chainLanding]): traced as a PROBLEM, and the player stays where he is. */
fun gg.rsmod.game.model.entity.Player.noLanding(target: gg.rsmod.game.model.entity.GameObject) {
    gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
        "rocks ${target.id} at ${target.tile.x},${target.tile.z}: no landing across from ${tile.x},${tile.z}"
    }
    message("You can't climb the rocks from here.")
}

/**
 * Darkan underwall tunnels (Falador.java:57-61 9309/9310, Yanille.java:38-42 9301/9302): AgilityShortcuts.crawlUnder(player,
 * player.transform(0, [dz])) from the route tile - one 2589 crawl 35/130 to the far side, 2591 three ticks in (DarkanShortcuts.kt).
 * Replaces the Void three-slide tunnel from a fixed lane tile.
 */
fun tunnel(
    id: Int,
    option: String,
    level: Int,
    dz: Int,
) = shortcut(id, option) {
    if (!player.hasAgility(level, "You need an Agility level of $level to negotiate this tunnel.")) return@shortcut
    obstacle {
        darkanCrawlUnder(player, player.darkanTransform(0, dz))
    }
}

/**
 * Darkan Varrock.java:175-200 (handleGrandExchangeShortcut 9311 / 9312, level 21), tick for tick: t1 forceMove(the clicked tunnel's
 * own tile, 2589, 5, 35, no unlock); t3 anim 2590; t5 anim 2591 + tele to the other tunnel's tile ((3139,3516) from 9312, (3143,3514)
 * from 9311); t6 tele one tile out ((3138,3516) / (3144,3514)) and unlock. The body is never slid through the wall: the old Void
 * port's middle exactMove ran diagonally 4x2 from 3139,3516 to 3143,3514 through the wall while 2590 played.
 */
fun geTunnel(id: Int) =
    shortcut(id, "Climb-into") {
        if (!player.hasAgility(21, "You need an Agility level of 21 to negotiate this tunnel.")) return@shortcut
        obstacle { target ->
            val withinGe = id == 9312
            val other = if (withinGe) Tile(3139, 3516, 0) else Tile(3143, 3514, 0)
            darkanForceMove(player, Tile(target.tile), startCycles = 5, speedCycles = 35, anim = ANIM_CLIMB_INTO_TUNNEL)
            player.animate(ANIM_TUNNEL_INVISIBLE)
            wait(2)
            player.animate(ANIM_CLIMB_OUT_OF_TUNNEL)
            player.moveTo(other)
            wait(1)
            player.moveTo(Tile(other.x + if (withinGe) -1 else 1, other.z, other.height))
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

/**
 * Ardougne log balance 35997 (2599,3336,1) / 35999 (2601,3336,1). Darkan Ardougne.java:235-239: level 33; AgilityShortcuts.walkLog(
 * player.transform(35999 ? -4 : +4, 0), 3) - the rope-balance walk from the route tile, no failure (Void's fall-into-the-river branch
 * and its swim are gone). 4 xp (OSRS Wiki, the old port) kept.
 */
fun ardougneLog(
    id: Int,
    dx: Int,
) = shortcut(id, "Walk-across") {
    if (!player.hasAgility(33)) return@shortcut
    obstacle { darkanWalkLog(player, player.darkanTransform(dx, 0), xp = 4.0) }
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
    // Darkan ports (owner 2026-10-04): AgilityShortcuts.climbOver(player, toTile, anim) = one forceMove from the route tile, start 33,
    // speed emoteTime / 20 - 5 (DarkanShortcuts.darkanClimbOver), with Darkan's landing per stile. Ids Darkan has no handler for
    // (993 Catherby, 3730 Death Plateau, 29460 Vinesweeper, 7527 at 1632,3570) keep the shared lane (ShortcutSupport.climbOver).
    listOf(993, 3730, 29460).forEach { id ->
        shortcut(id, "Climb-over") { obstacle { climbOver(player, it) } }
    }
    // Darkan Varrock.java:214-223 (handleStileShortcuts 45205 / 34776 / 48208): rotation 0/2 -> z -+3, 1/3 -> x -+3 (minus when the
    // player is at or past the loc). Darkan AlKharid.kt:52-56 (48208): x +3 west of the loc, else -3 - the same for its rotation 3.
    // Map (667): 45205 @3197,3276 r0 (fence between z 3276/3277), 34776 @2638,3350 r1 / 2647,3375 r3 / 2655,3347 r3, 48208
    // @3328,3161 r3 - every landing is open ground 3 tiles across from the route tile.
    listOf(45205, 34776, 48208).forEach { id ->
        shortcut(id, "Climb-over") {
            obstacle { target ->
                val landing =
                    if (target.rot == 0 || target.rot == 2) {
                        player.darkanTransform(0, if (player.tile.z >= target.tile.z) -3 else 3)
                    } else {
                        player.darkanTransform(if (player.tile.x >= target.tile.x) -3 else 3, 0)
                    }
                darkanClimbOver(player, landing)
            }
        }
    }
    // Darkan Falador.java:76-96 (handleCabbagePatchStile 7527 @3063,3282 / handleCowFieldStile 7527 @3043,3305): fixed landings
    // (obj.x, obj.z -+ 1) / (obj.x -+ 1, obj.z) by the player's side. The third 7527 (1632,3570) has no Darkan handler: shared lane.
    shortcut(7527, "Climb-over") {
        obstacle { target ->
            val t = target.tile
            when {
                t.x == 3063 && t.z == 3282 && player.tile.z > t.z -> darkanClimbOver(player, Tile(t.x, t.z - 1, t.height))
                t.x == 3063 && t.z == 3282 && player.tile.z < t.z -> darkanClimbOver(player, Tile(t.x, t.z + 1, t.height))
                t.x == 3043 && t.z == 3305 && player.tile.x > t.x -> darkanClimbOver(player, Tile(t.x - 1, t.z, t.height))
                t.x == 3043 && t.z == 3305 && player.tile.x < t.x -> darkanClimbOver(player, Tile(t.x + 1, t.z, t.height))
                t.x == 3063 && t.z == 3282 || t.x == 3043 && t.z == 3305 -> Unit
                else -> climbOver(player, target)
            }
        }
    }
    // Darkan Morytania.java:95 (handleBrokenFence 18411 @3584,3263): climbOver(player.transform(0, y > obj.y ? -1 : 1), 839).
    shortcut(18411, "Climb-over") {
        obstacle { target -> darkanClimbOver(player, player.darkanTransform(0, if (player.tile.z > target.tile.z) -1 else 1)) }
    }
    // Darkan FalconryController.java:106-122 (enterArea 19222 @2371,3620): lock(2); anim 1560 (Anims.HUMANOID_1560, player skeleton
    // 256 in the 667 cache); next tick tele(player.transform(0, y > obj.y ? -2 : 2)). Darkan's leave-the-falconry-area confirmation needs its falconry controller (not on this server).
    shortcut(19222, "Climb-over") {
        obstacle { target ->
            val landing = player.darkanTransform(0, if (player.tile.z > target.tile.z) -2 else 2)
            player.animate(1560)
            wait(1)
            player.moveTo(landing)
        }
    }
    // Darkan Falador.java:70-74 (handleCrumblingWallShortcut 11844 @2935,3355): level 5; climbOver(player.transform(x < obj.x ? 2 : -2,
    // 0)). 0.5 xp (OSRS Wiki, Void Stiles) kept.
    shortcut(11844, "Climb-over") {
        if (!player.hasAgility(5, "You need an Agility level of 5 to climb over this wall.")) return@shortcut
        obstacle { target ->
            darkanClimbOver(player, player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0))
            player.addXp(Skills.AGILITY, 0.5)
        }
    }
    // Darkan SeersVillage.java:73-77 (grubersWoodFence 51 @2662,3500, west wall): forceMove(px < ox ? (2662,3500) : (2661,3500), 3844,
    // 25, 75) from the route tile.
    shortcut(51, "Squeeze-through") {
        obstacle { target ->
            val landing = if (player.tile.x < target.tile.x) Tile(2662, 3500, 0) else Tile(2661, 3500, 0)
            darkanForceMove(player, landing, startCycles = 25, speedCycles = 75, anim = ANIM_RAILING_SQUEEZE)
        }
    }

    // Level corrected 2026-09-18: 2009scape TunnelShortcut sources ids 9301/9302 at level 16 (OSRS/RS Wiki "Yanille agility
    // shortcut": 16; Darkan Yanille.java:39 has 14 - the wiki level is kept). Landings Darkan: 9302 -5 / 9301 +5 (Yanille, 9301 @2575,3108,
    // 9302 @2575,3111), 9310 -4 / 9309 +4 (Falador, 9309 @2948,3310, 9310 @2948,3312), from the route tile.
    tunnel(9302, "Climb-into", 16, -5)
    tunnel(9301, "Climb-under", 16, 5)
    geTunnel(9311)
    geTunnel(9312)
    tunnel(9310, "Climb-into", 26, -4)
    tunnel(9309, "Climb-into", 26, 4)

    // Void Pipes.kt (2026-10-03): brimhaven_pipe_moss 5100 needs 22 at y 9567 for 8.5 xp, brimhaven_pipe_dragon 5099 needs 34 at
    // y 9498 for 10 xp - the OSRS Wiki's 22 (moss giants) / 34 (red dragons). The 2026-09-18 "correction" took 2009scape's
    // swapped numbers.
    verticalPipe(5100, requirementZ = 9567, level = 22, north = 9573, middle = 9570, south = 9566, xp = 8.5)
    verticalPipe(5099, requirementZ = 9498, level = 34, north = 9499, middle = 9495, south = 9492, xp = 10.0)

    // Taverley Dungeon pipe 9293 (667: 2887,9799 r0 / 2891,9799 r2). Darkan TaverlyDungeon.java:33-41 (handlePipeSqueeze): level 70;
    // x = player.x == 2886 ? 2892 : 2886; one forceMove((x,9799), 10580, 0, 90) from the route tile (was two 30/96 slides).
    // 10 xp (OSRS Wiki, the old port) kept.
    shortcut(9293, "Squeeze-through") {
        if (!player.hasAgility(70, "You need an Agility level of 70 to squeeze through the pipe.")) return@shortcut
        obstacle {
            val x = if (player.tile.x == 2886) 2892 else 2886
            darkanForceMove(player, Tile(x, 9799, player.tile.height), startCycles = 0, speedCycles = 90, anim = ANIM_CLIMB_THROUGH_PIPE)
            player.addXp(Skills.AGILITY, 10.0)
        }
    }

    ardougneLog(35999, -4)
    ardougneLog(35997, 4)
    brimhavenLog(5088, 2687)
    brimhavenLog(5090, 2682)

    // ---- Stepping stones (Void SteppingStones) ----
    // Stone in water, start tiles 2 tiles away: the click acts from there (batch 16: "I can't reach that!").
    // Void binds both Lumbridge swamp-cave stones with objectApproach: the handler walks to its own start tile. The 2-tile line of
    // sight fails over the water, so the router took the player round the whole cave to the far bank (live traversal run
    // 2026-10-04: 3208,9572 -> ~60 tiles -> 3204,9572, then the jump the other way). Within 6 tiles no routing (approach range).
    world.plugins.setObjApproachDistance(5948, 6)
    world.plugins.setObjApproachDistance(5949, 6)
    // Darkan LumbridgeSwampDungeon.java:48-71 (handleSteppingStone1 5948 @3206,9572, checkDistance false): west = player.x < 3206;
    // walk (with collision) to the bank, t2 face the stone, t3 forceMove((3208,9572) | (3204,9572), 1995, 0, 120), t4 anim 1603. One
    // jump over the stone, no failure (was Void's two 769 hops and its slip branch). Void's message and 3 xp kept.
    shortcut(5948, "Jump-across", lineOfSightDistance = 2) {
        obstacle { target ->
            val west = player.tile.x < target.tile.x
            walkToTile(player, Tile(if (west) 3204 else 3208, 9572))
            player.faceTile(target.tile)
            wait(1)
            player.filterableMessage("You leap across with a mighty leap!")
            player.playSound(SOUND_JUMP)
            darkanForceMoveFor(player, Tile(if (west) 3208 else 3204, 9572), startCycles = 0, speedCycles = 120, anim = 1995, resumeTicks = 1)
            player.animate(1603)
            wait(3)
            player.addXp(Skills.AGILITY, 3.0)
        }
    }

    // Shilo / Jogre waterfall stepping stones 2333-2335 (667: 2925,2948-2950). Darkan Karamja.java:129-133 (handleJogreWaterfall
    // SteppingStones): level 30; forceMove(the clicked stone's tile, 741, 0, 30) from the route tile (was anim 769 with a 48/60
    // slide + wait). Void's messages, the adjacency check and 3 xp are kept.
    intArrayOf(2333, 2334, 2335).forEach { id ->
        shortcut(id, "Cross") {
            if (player.skills.getCurrentLevel(Skills.AGILITY) < 30) {
                player.queue { messageBox("The stepping stone looks very small and slippery. You'd better have an Agility level of 30.") }
                return@shortcut
            }
            obstacle { target ->
                if (player.tile == target.tile || !player.tile.isWithinRadius(target.tile, 1)) return@obstacle
                player.filterableMessage("You attempt to balance on the stepping stone.")
                player.playSound(SOUND_JUMP)
                darkanForceMove(player, Tile(target.tile), startCycles = 0, speedCycles = 30, anim = ANIM_STEPPING_STONE_JUMP)
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

    // The stone stands in water: its start tile is 3 tiles away, no route ends beside it ("I can't reach that!", batch 15).
    // Darkan Karamja.java:204-218 (handleSteppingStone 10536 @2860,2974): route to (2860,2971) south / (2860,2977) north, then
    // forceMove((2860,2974), 741, 0, 30) and on arrival forceMove(the far bank tile, 741, 0, 30). Level 74 (OSRS Wiki; Darkan 77) kept.
    shortcut(10536, "Jump-to", lineOfSightDistance = 3) {
        obstacle { target ->
            val direction = if (target.tile.z > player.tile.z) Direction.NORTH else Direction.SOUTH
            walkToTile(player, Tile(target.tile.x, target.tile.z - direction.getDeltaZ() * 3))
            if (!player.hasAgility(74, "You need level 74 Agility to tackle this obstacle.")) return@obstacle
            player.playSound(SOUND_JUMP)
            darkanForceMove(player, Tile(target.tile), startCycles = 0, speedCycles = 30, anim = ANIM_STEPPING_STONE_JUMP)
            player.playSound(SOUND_JUMP)
            darkanForceMove(player, Tile(target.tile.x, target.tile.z + direction.getDeltaZ() * 3), startCycles = 0, speedCycles = 30, anim = ANIM_STEPPING_STONE_JUMP)
        }
    }

    // Brimhaven Dungeon stepping stones 5110 (2649,9561) / 5111 (2647,9558). Darkan BrimhavenDungeon.java:53-150
    // (handleSteppingStones): level 12 on both; forceMove(the clicked stone, 741, 0, 30), then every second tick the next stone with
    // forceMove(next, 741, 0, 30) (the tick between is Darkan's tele onto the tile it already has): 5110 (0,-1) (-1,0) (-1,0) (0,-1)
    // (0,-1), 5111 the reverse. Darkan ends on the other chain's stone (2647,9558 / 2649,9561), a water tile in this map's collision;
    // one more hop onto the bank (2647,9557 / 2649,9562, the old port's ends) keeps the player from standing in the water.
    val brimhavenStones = listOf(Direction.SOUTH, Direction.WEST, Direction.WEST, Direction.SOUTH, Direction.SOUTH)
    listOf(5110 to false, 5111 to true).forEach { (id, reverse) ->
        shortcut(id, "Jump-from") {
            if (!player.hasAgility(12, "You need an agility level of 12 to use this obstacle.")) return@shortcut
            obstacle { target ->
                walkToTile(player, if (reverse) Tile(2647, 9557) else Tile(2649, 9562))
                player.filterableMessage("You carefully start crossing the stepping stones...")
                darkanForceMove(player, Tile(target.tile), startCycles = 0, speedCycles = 30, anim = ANIM_STEPPING_STONE_JUMP)
                wait(1)
                val path = if (reverse) brimhavenStones.reversed().map { it.getOpposite() } else brimhavenStones
                for (direction in path + (if (reverse) Direction.NORTH else Direction.SOUTH)) {
                    darkanForceMove(player, player.tile.add(direction), startCycles = 0, speedCycles = 30, anim = ANIM_STEPPING_STONE_JUMP)
                    wait(1)
                }
                player.addXp(Skills.AGILITY, 7.5)
                player.filterableMessage("... You safely cross to the other side.")
            }
        }
    }

    // ---- Brimhaven (Karamja) moss giant rope swings 2322 / 2323 ----
    // Darkan Karamja.java:117-127 -> Agility.swingOnRopeSwing (Agility.java:85-95): level 10; route to the start, face the rope, anim
    // 751 + rope loc anim 497, forceMove(end, 30, 90). Void's start / end tiles of this map, 3 xp and message kept (Darkan's tiles
    // (2709,3205) -> (2704,3209) cross diagonally).
    listOf(Triple(2322, Tile(2709, 3209), Tile(2704, 3209)), Triple(2323, Tile(2705, 3205), Tile(2709, 3205))).forEach { (id, start, end) ->
        shortcut(id, "Swing-on") {
            if (!player.hasAgility(10, "You need an agility level of 10 to attempt to swing on this vine.")) return@shortcut
            obstacle { target -> darkanRopeSwing(player, target, start, end, 3.0) }
        }
    }

    // ---- Trollheim rocks (Void Trollheim.kt; this 2011 map's own placements) ----
    // The 667 map re-placed every Trollheim rock as one "Cliffside" loc (3803 @2876,3671, 9303 @2855,3663, 9304 @2873,3660,
    // 9305 @2894,3672, 9306 @2909,3685, 3748 x3); Void's tiles (2885,3683 ...) are not on this map. Each climb is the shared
    // climbCliff: a fixed lane through the loc, down = Void's climb-down sequence, up = Void's "climbing" render, levels / xp as Void
    // (9305: OSRS Wiki 47, Void has a TODO). Owner FAIL 2026-10-03 02:24 (walked through the rock) and the stuck / noclip reports
    // 09-30 .. 10-03 were the slides from wherever the click left the player and the plain walk up (OB7).
    // Owner 154847 (2026-10-04, "still noclip when crossing obstacles" at 9305): the Cliffsides now climb Jagex's own lane with
    // Jagex's own climbs (Cliffside.kt: 3393 up / 15239 down carry the body over the boulder); the climbCliff slide through the
    // rock stays only for locs without such a lane. Levels as the RuneScape Wiki per object (9305 was 47, 9306 had none).
    Cliffside.LEVELS.forEach { (id, level) ->
        shortcut(id, "Climb") {
            if (level > 0 && !player.hasAgility(level)) return@shortcut
            obstacle { target ->
                if (climbCliffside(player, target)) player.addXp(Skills.AGILITY, Cliffside.XP)
            }
        }
    }
    shortcut(3804, "Climb") {
        obstacle { target ->
            val crossing = cliffCrossing(world, target, player.tile)
            val down = crossing != null && goesDown(world, crossing)
            if (climbCliff(player, target, ANIM_ROCKS_CLIMB_DOWN)) player.addXp(Skills.AGILITY, if (down) 1.0 else 8.0)
        }
    }
    // Darkan Trollheim.kt:266-270 (handleObstacleInteractions 3748): Agility.handleObstacle(3377, 2, player.transform(+-2 along the
    // loc's axis), 1.0) - rotation 1/3: x (+2 when the player is west of the loc), else z. Start = the route tile, the body stays on it
    // while 3377 plays and is teleported after 2 ticks (no slide through the rock). Darkan's Troll Stronghold quest gate is not applied
    // (ours stays open); our level 44 and Void's messages are kept, xp 1.0 from Darkan. Map (667): 3748 at 2817,3630 r3 (lane z 3632),
    // 2846,3620 r2 (lane x 2848), 2918,3675 r0 (lane x 2920) - each lands on open ground 2 tiles across.
    shortcut(3748, "Climb") {
        if (!player.hasAgility(44)) return@shortcut
        obstacle { target ->
            // Visual check 2026-10-04 in the agent's client: Darkan's 3377 moves the body 2 tiles at ground height through the pile
            // (body sank into the rocks); Void's rocks_pile_climb 839 (body lift 200) climbs onto the pile and steps down - kept.
            player.message("You climb onto the rock...")
            if (climbCliff(player, target, ANIM_ROCKS_PILE_CLIMB)) player.message("...and step down the other side.")
        }
    }

    // Visual check 2026-10-04 (agent's client): Darkan's boulder relies on loc animation 318, which in this 667 cache is a player
    // sequence - ported, the body crawled into a boulder that never moved. Void's rolling boulder (6980/6981) is kept.
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
            // Void GodwarsBoulder "Move", step for step: the boulder rolls aside (6980) before the player passes and rolls back (6981)
            // after him. The port had dropped the boulder's own animations and the waits, so the player slid through a rock that
            // never moved (owner 2026-10-04 025931 "noclips into the object").
            walkToTile(player, start)
            face(player, if (north) Direction.NORTH else Direction.SOUTH)
            wait(2)
            player.animate(if (north) ANIM_MOVE_BOULDER_NORTH else ANIM_MOVE_BOULDER_SOUTH)
            wait(2)
            animateLoc(world, target, ANIM_GODWARS_BOULDER_MOVE)
            world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = Tile(target.tile), id = SFX_GODWARS_MOVE_BOULDER, radius = 5, volume = 1))
            wait(3)
            exactMove(player, end, delay = 210, direction = if (north) Direction.NORTH else Direction.SOUTH)
            wait(2)
            world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = Tile(target.tile), id = SFX_GODWARS_BOULDER_ROLLBACK, radius = 5, volume = 1))
            animateLoc(world, target, ANIM_GODWARS_BOULDER_ROLLBACK)
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

    // ---- Al Kharid mine rocks 9331 (3306,3315) / 9332 (3304,3315) ----
    // Darkan AlKharid.kt:59-72 (handleMiningSiteShortcut): level 38; 9331 -> forceMove((3303,3315), 2050, 10, 60); 9332 ->
    // forceMove((3307,3315), 2049, 10, 60) from the route tile. Was a climbing-render walk + a 1148 slide / walk to 3303 first.
    listOf(9331 to Pair(Tile(3303, 3315), 2050), 9332 to Pair(Tile(3307, 3315), 2049)).forEach { (id, move) ->
        shortcut(id, "Climb") {
            if (!player.hasAgility(38, "You need an Agility level of 38 to negotiate these rocks.")) return@shortcut
            obstacle { darkanForceMove(player, move.first, startCycles = 10, speedCycles = 60, anim = move.second) }
        }
    }

    // ---- Burgh de Rott / Mort Myre low fence 12776 (667: 3474,3221, wall type 0 rotation 0) ----
    // Darkan Morytania.java:93 (handleBurghDeRottToMineFence): AgilityShortcuts.climbOver(player.transform(x < obj.x ? +1 : -1, 0),
    // 2923) - one 33 / (emoteTime / 20 - 5) forced move from the route tile. Was a collision-free approach walk 2 tiles back
    // (walkOverTile Render.NONE) and a 2-tile 11574 slide. Level 25 (Void BurghDeRottLowFence) kept.
    shortcut(12776, "Jump-over") {
        if (!player.hasAgility(25)) return@shortcut
        obstacle { target -> darkanClimbOver(player, player.darkanTransform(if (player.tile.x < target.tile.x) 1 else -1, 0), 2923) }
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
                // Void Zanaris: the spear trap 12126 west of the wall fires (spear_trap_release 459) on every hurt tick.
                val trap = locAt(world, target.tile.transform(-1, 0), 12126)
                for (ouch in listOf("Ahhh...", "Owww...", "Arrgghhhh!")) {
                    trap?.let { animateLoc(world, it, 459) }
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
    // Energy barriers 47236 (667: rows at 1604-1606,5265 / 1609-1611,5289 / 1650-1652,5281 rotation 3, columns at 1625,5301-5303 /
    // 1634,5252-5254 / 1649,5301-5303 rotation 2, wall type 0). Darkan KuradalsDungeon.java:34-39 (handleBarriers): no level;
    // rotation 2 -> walkOneStep(x > obj.x ? -1 : +1, 0), else walkOneStep(0, y == obj.y ? -1 : +1), collision-free. The level 90 of
    // the old handler belongs to the wall run 47237 below.
    shortcut(47236, "Pass") {
        obstacle { target ->
            val step =
                if (target.rot == 2) {
                    player.darkanTransform(if (player.tile.x > target.tile.x) -1 else 1, 0)
                } else {
                    player.darkanTransform(0, if (player.tile.z == target.tile.z) -1 else 1)
                }
            walkOverTile(player, step, Render.NONE)
        }
    }
    // Wall run 47237 (667: gaps 1641,5261 r2 and 1641,5267 r0). Darkan KuradalsDungeon.java:41-73 (handleWallRunShortcut): level 90;
    // from (1641,5268) anim 2929, from (1641,5260) anim 2922, else nothing; the next tick forceMove(the other end (1641,5260) |
    // (1641,5268), 0, 30). Was a plain object-teleport (object-teleports.json, no level, no animation).
    shortcut(47237, "Run-across") {
        if (!player.hasAgility(90, "You need level 90 agility to use this shortcut.")) return@shortcut
        obstacle {
            val north = player.tile.x == 1641 && player.tile.z == 5268
            val south = player.tile.x == 1641 && player.tile.z == 5260
            if (!north && !south) return@obstacle
            player.animate(if (north) 2929 else 2922)
            wait(1)
            darkanForceMove(player, Tile(1641, if (north) 5260 else 5268, player.tile.height), startCycles = 0, speedCycles = 30)
        }
    }
    // Low wall 47233 (667: 1633,5293 r2). Darkan KuradalsDungeon.java:75-110 (handleLowWall): level 86; at (1633,5294) face south and
    // anim 839, the next tick tele(1633,5292); at (1633,5292) face north, 839, tele(1633,5294). Darkan does nothing from any other
    // tile; here the normal (collision) walk to the nearer of the two tiles comes first (was a collision-free walk + 839 slide).
    shortcut(47233, "Climb-over") {
        if (!player.hasAgility(86, "You need level 86 agility to use this shortcut.")) return@shortcut
        obstacle {
            val north = player.tile.z >= 5293
            walkToTile(player, Tile(1633, if (north) 5294 else 5292, player.tile.height))
            face(player, if (north) Direction.SOUTH else Direction.NORTH)
            player.animate(ANIM_ROCKS_PILE_CLIMB)
            wait(1)
            player.moveTo(Tile(1633, if (north) 5292 else 5294, player.tile.height))
        }
    }

    // ---- Fremennik Slayer Dungeon ----
    // Crevice 9321 (667: wall pieces 2731,10008 r0 and 2734,10008 r2). Darkan FremennikSlayerDungeon.java:34-38 (handleShortcut2):
    // level 62; one forceMove(player.transform(rotation 0 ? +5 : -5, 0), 3844, 25, 75) from the route tile (was three Void slides).
    // The message and 7.5 xp of the old port (2009scape) are kept.
    shortcut(9321, "Squeeze-through") {
        if (!player.hasAgility(62, "You need level 62 agility in order to contort your body through this crack.")) return@shortcut
        obstacle { target ->
            darkanForceMove(player, player.darkanTransform(if (target.rot == 0) 5 else -5, 0), startCycles = 25, speedCycles = 75, anim = ANIM_RAILING_SQUEEZE)
            player.message("You climb your way through the narrow crevice.")
            player.addXp(Skills.AGILITY, 7.5)
        }
    }
    // Chasm 44339 (667: 2769,10002 r2). Darkan FremennikSlayerDungeon.java:27-32 (handleChasm, checkDistance false): level 81; walk to
    // (2775,10002) east of x 2772 / (2768,10002) west, then forceMove(the other one, 4721, 20, 85) and anim -1. Was anim 10738 with a
    // 30/120 slide. The message and xp of the old port are kept.
    shortcut(44339, "Jump-across") {
        if (!player.hasAgility(81, "You need an agility level of 81 to tackle this obstacle.")) return@shortcut
        obstacle {
            val east = player.tile.x > 2772
            walkToTile(player, Tile(if (east) 2775 else 2768, 10002))
            darkanForceMove(player, Tile(if (east) 2768 else 2775, 10002), startCycles = 20, speedCycles = 85, anim = 4721)
            player.animate(-1)
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

    // ---- Yanille Agility Dungeon pipe 2290 (667: 2573,9506 r0 / 2577,9506 r2) ----
    // Darkan AgilityDungeon.java:99-105 (handlePipe): level 49; forceMove(objTile.transform(player.x < 2575 ? +5 : -5, 0), 10580, 35,
    // 120) from the route tile, 5 xp at the end; on !skillSuccess(level, -35, 350) a 10 % max-hitpoints hit (the crossing still
    // happens). Was a 20/90 slide from a fixed lane tile, no xp.
    shortcut(2290, "Squeeze-through") {
        if (!player.hasAgility(49, "You need an Agility level of 49 to squeeze through the pipe.")) return@shortcut
        obstacle { target ->
            val failed = !darkanSkillSuccess(player, -35, 350)
            val landing = Tile(target.tile.x + if (player.tile.x < 2575) 5 else -5, target.tile.z, target.tile.height)
            if (failed) player.darkanPercentHit(0.1)
            darkanForceMove(player, landing, startCycles = 35, speedCycles = 120, anim = ANIM_CLIMB_THROUGH_PIPE)
            player.addXp(Skills.AGILITY, 5.0)
        }
    }

    // ---- Barbarian Outpost entrance pipe 20210 (667: 2552,3559 r1) ----
    // Darkan BarbarianOutpostAgility.kt:52-57: level 35; forceMove((obj.x, player.y >= 3561 ? 3558 : 3561), 10580, 10, 60) from the
    // route tile (was a walk to a fixed start + 0/60 slide). No xp (Void; Darkan 0.05 is not kept).
    shortcut(20210, "Squeeze-through") {
        if (!player.hasAgility(35)) return@shortcut
        obstacle { target ->
            val landing = Tile(target.tile.x, if (player.tile.z >= 3561) 3558 else 3561, target.tile.height)
            darkanForceMove(player, landing, startCycles = 10, speedCycles = 60, anim = ANIM_CLIMB_THROUGH_PIPE)
        }
    }

    // ---- Taverley Dungeon strange floor 9294 (667: 2879,9813 r0) ----
    // Darkan TaverlyDungeon.java:43-71 (handleStrangeFloor): level 80; north = player.y > 9812; walk (with collision) to (2881,9814)
    // from the north / (2877,9812) from the south; t2 face the floor; t3 forceMove((2878,9812) | (2881,9814), 1995, 0, 100); t4 anim
    // 1603; t7 unlock. Was anim 10738 and a 90-cycle slide from wherever the click ended. 12.5 xp (OSRS Wiki) kept.
    shortcut(9294, "Jump-over") {
        if (!player.hasAgility(80, "You need an Agility level of 80 to tackle this obstacle.")) return@shortcut
        obstacle { target ->
            val north = player.tile.z > 9812
            walkToTile(player, if (north) Tile(2881, 9814) else Tile(2877, 9812))
            player.faceTile(target.tile)
            wait(1)
            darkanForceMoveFor(player, if (north) Tile(2878, 9812) else Tile(2881, 9814), startCycles = 0, speedCycles = 100, anim = 1995, resumeTicks = 1)
            player.animate(1603)
            wait(3)
            player.addXp(Skills.AGILITY, 12.5)
        }
    }

    // ---- Fremennik Province log balance, Sinclair Mansion <-> Rellekka (667: 9322 @2722,3595,1, 9324 @2722,3593,1) ----
    // Darkan SeersVillage.java:233-237 (handleSinclairMansionLogBalance): level 48; AgilityShortcuts.walkLog(player.transform(0,
    // 9322 ? -4 : +4), 3) - the rope-balance walk from the route tile on the player's own plane, 7.5 xp (the old port walked a plane-0
    // tile from wherever the click ended, 4 xp from 2009scape).
    listOf(9322 to -4, 9324 to 4).forEach { (id, dz) ->
        shortcut(id, "Walk-across") {
            if (!player.hasAgility(48, "You need an Agility level of 48 to cross this log.")) return@shortcut
            obstacle { darkanWalkLog(player, player.darkanTransform(0, dz)) }
        }
    }

    // ---- Isafdar / Arandar log balances 3931 / 3932 / 3933 (667 placements: Objs; Darkan's ids are the same) ----
    // Darkan Tirannwn.java:134-148: level 45 ("You need 45 agility"); 3931 / 3932 walkLog(player.transform(x > obj.x ? -6 : +6, 0),
    // 4); 3933 walkLog(player.transform(0, y > obj.y ? -7 : +7), 6) - from the route tile, 7.5 xp (was a collision-free walk to a
    // fixed end from wherever the click ended, 1 xp).
    listOf(3931, 3932, 3933).forEach { id ->
        shortcut(id, "Cross") {
            if (!player.hasAgility(45, "You need an Agility level of 45 to cross this log.")) return@shortcut
            obstacle { target ->
                val landing =
                    if (id == 3933) {
                        player.darkanTransform(0, if (player.tile.z > target.tile.z) -7 else 7)
                    } else {
                        player.darkanTransform(if (player.tile.x > target.tile.x) -6 else 6, 0)
                    }
                darkanWalkLog(player, landing)
            }
        }
    }

    // ---- Arandar (Elven Overpass) rocks 9296 / 9297, three tiers. Darkan Tirannwn.java:185-234 (handleAdvancedElvenCliffside):
    // 9296 (above) 2333,3252 lvl 85 -> forceMove((2338,3253), 2050, 0, 30); 2338,3282 lvl 68 -> (2338,3286); 2346,3299 lvl 59 ->
    // (2344,3294); 9297 (below) 2337,3253 lvl 85 -> ((2332,3252), 2049, 0, 30); 2338,3285 lvl 68 -> (2338,3281); 2344,3295 lvl 59 ->
    // (2346,3300). From the route tile; no xp. The old tiers had the levels swapped (59 / 85 / 68) and the pairCrossing lanes.
    val arandarTiers =
        listOf(
            Triple(Tile(2333, 3252), 85, Tile(2338, 3253)),
            Triple(Tile(2338, 3282), 68, Tile(2338, 3286)),
            Triple(Tile(2346, 3299), 59, Tile(2344, 3294)),
            Triple(Tile(2337, 3253), 85, Tile(2332, 3252)),
            Triple(Tile(2338, 3285), 68, Tile(2338, 3281)),
            Triple(Tile(2344, 3295), 59, Tile(2346, 3300)),
        )
    listOf(9296, 9297).forEach { id ->
        shortcut(id, "Climb") {
            val clicked = player.getInteractingGameObj()
            val tier = arandarTiers.firstOrNull { (rock, _, _) -> clicked.tile.x == rock.x && clicked.tile.z == rock.z } ?: return@shortcut
            val (_, level, landing) = tier
            if (!player.hasAgility(level, "You need level $level agility to use this shortcut.")) return@shortcut
            obstacle { darkanForceMove(player, landing, startCycles = 0, speedCycles = 30, anim = if (id == 9296) 2050 else 2049) }
        }
    }

    // ---- Eagles' Peak rocks 19849 (667: lower 2322,3501, upper 2323,3497). Darkan Piscatoris.java:83-94 (handleEaglesPeakShortcut):
    // level 25; the upper rock (2323,3497) forceMove((2322,3502), 2050, 25, 60), the lower rock (2322,3501) forceMove((2323,3496), 2049,
    // 25, 60), from the route tile. Was Void's 250 / 240-cycle slides with 4435 / 820 + 740. Void's 1 xp kept; any other placement
    // (none known) climbs down like the upper rock.
    shortcut(19849, "Climb") {
        if (!player.hasAgility(25, "You must have an Agility level of at least 25 to climb these rocks.")) return@shortcut
        obstacle { target ->
            if (target.tile.x == 2322 && target.tile.z == 3501) {
                darkanForceMove(player, Tile(2323, 3496), startCycles = 25, speedCycles = 60, anim = 2049)
            } else {
                darkanForceMove(player, Tile(2322, 3502), startCycles = 25, speedCycles = 60, anim = 2050)
            }
            player.animate(-1)
            player.addXp(Skills.AGILITY, 1.0)
        }
    }

    // ---- God Wars Dungeon rocky handholds (Trollheim <-> Wilderness route), level 60, no xp. Darkan Wilderness.java:80-101
    // (handleGWDShortcut): from the route tile 26327 forceMove((2943,3767), 2049, 25, 60); 26328 tele(2943,3767) then forceMove(
    // (2950,3767), 2050, 25, 60); 26324 forceMove((2928,3757), 2049, 25, 60); 26323 forceMove((2927,3761), 2050, 25, 60). The landings
    // are open ground on this map (667: 26327 @2949,3767, 26328 @2943,3768, 26324 @2927,3760, 26323 @2928,3758). Replaces the
    // pairCrossing lanes (climbPair).
    listOf(
        26327 to Pair(Tile(2943, 3767), 2049),
        26328 to Pair(Tile(2950, 3767), 2050),
        26324 to Pair(Tile(2928, 3757), 2049),
        26323 to Pair(Tile(2927, 3761), 2050),
    ).forEach { (id, move) ->
        val (landing, anim) = move
        shortcut(id, "Climb") {
            if (!player.hasAgility(60, "You need level 60 agility to use this shortcut.")) return@shortcut
            obstacle {
                if (id == 26328) player.moveTo(Tile(2943, 3767))
                darkanForceMove(player, landing, startCycles = 25, speedCycles = 60, anim = anim)
            }
        }
    }

    // ---- Karamja rocks 2231 to Cairn Isle (667: x 2792 r2 / 2794 r0, z 2978-2980), no requirement. Darkan Karamja.java:293-295
    // (handleRocksToCairnIsle): useStairs(-1, (obj.x == 2792 ? 2795 : 2791, 2979), 1, 2, "You climb down the slope." | "You climb up
    // the slope.") - no animation, teleport one tick later (was the pairCrossing lanes + slides).
    shortcut(2231, "Climb") {
        obstacle { target ->
            val down = target.tile.x == 2792
            wait(1)
            player.moveTo(Tile(if (down) 2795 else 2791, 2979, target.tile.height))
            player.message(if (down) "You climb down the slope." else "You climb up the slope.")
        }
    }
    // The two rocks are 9335@3425,3476 and 9336@3426,3477 (placements_667): each slid the player onto a rock's own tile (E17 noclip,
    // batch 16). The shared rock-pair crossing goes from beside the clicked rock to beside its partner (2009scape lands 3427,3478 /
    // 3424,3476 - the ends of the same lane).
    shortcut(9335, "Climb") { obstacle { target -> climbPair(player, target, Tile(3426, 3477), ANIM_ROCKS_CLIMB_DOWN) } }
    shortcut(9336, "Climb") { obstacle { target -> climbPair(player, target, Tile(3425, 3476), ANIM_ROCKS_CLIMB_DOWN) } }

    // ---- Yanille Agility Dungeon monkey bars 2321 (667: 2598/2599 x 9489/9494, rotation 2, 1x1 each) ----
    // Darkan AgilityDungeon.java:107-119 (handleMonkeybars): level 57; on !skillSuccess(level, -15, 323): crossMonkeybars(objTile,
    // objTile + (0, y > 9491 ? -2 : +2), 0) and 6 ticks after the click tele(2572,9568) + a 20 % max-hitpoints hit; else
    // crossMonkeybars(objTile, objTile + (0, y > 9491 ? -5 : +5), 20). The lane is the clicked bar's own x (the old handler always
    // used x 2599 and ignored the 2598 bars), start = the bar's own tile.
    shortcut(2321, "Swing across") {
        if (!player.hasAgility(57, "You need an Agility level of 57 to swing across the monkey bars.")) return@shortcut
        obstacle { target ->
            val dz = if (player.tile.z > 9491) -1 else 1
            if (!darkanSkillSuccess(player, -15, 323)) {
                darkanCrossMonkeyBars(player, Tile(target.tile), target.tile.transform(0, 2 * dz), 0.0)
                wait(2)
                player.moveTo(Tile(2572, 9568, 0))
                player.darkanPercentHit(0.2)
                return@obstacle
            }
            darkanCrossMonkeyBars(player, Tile(target.tile), target.tile.transform(0, 5 * dz), 20.0)
        }
    }

    // ---- Wilderness ditch 1440-1444 (667; Darkan also 65076-65087, 727-only). Darkan Wilderness.java:211-250 (entering) and
    // WildernessController.java:112-121 (leaving): one forceMove(landing, 6132, 25 | 30, 60) from the route tile, no lane:
    //  - entering: rotation 1/3 -> (obj.x - 1, player.z), rotation 0/2 -> (player.x, obj.z + 2), start cycles 25;
    //  - leaving:  rotation 1/3 -> (obj.x + 2, player.z), rotation 0/2 -> (player.x, obj.z - 1), start cycles 30.
    // Darkan tells the sides apart by its Wilderness controller; here by the side of the segment the player stands on (the 3x2
    // segments: rotation 0/2 span z obj.z..obj.z+1 with the Wilderness north; the one rotation-1 segment at 2996,3531 spans
    // x 2996..2997 with the Wilderness west). Darkan's interface-382 warning is not shown here (no change to the warning flow).
    // Replaces the shortAxisCrossing lane + 0/60 slide (with SlideTiming lead).
    (1440..1444).forEach { id ->
        shortcut(id, "Cross") {
            obstacle { target ->
                val acrossX = target.rot == 1 || target.rot == 3
                // Darkan: Wilderness.java handles the click outside the Wilderness (entering), WildernessController inside (leaving).
                // By side, not by getWildernessLevel (level 1 starts at z 3525, so 3523/3524 north of the ditch read as level 0).
                val entering = if (acrossX) player.tile.x > target.tile.x else player.tile.z <= target.tile.z
                val landing =
                    when {
                        acrossX && entering -> Tile(target.tile.x - 1, player.tile.z, target.tile.height)
                        acrossX -> Tile(target.tile.x + 2, player.tile.z, target.tile.height)
                        entering -> Tile(player.tile.x, target.tile.z + 2, target.tile.height)
                        else -> Tile(player.tile.x, target.tile.z - 1, target.tile.height)
                    }
                darkanForceMove(player, landing, startCycles = if (entering) 25 else 30, speedCycles = 60, anim = ANIM_WILD_DITCH_JUMP)
                player.faceTile(target.tile)
            }
        }
    }

    // ---- Port Phasmatys weathered wall to the Ectopool (2009scape PhasmatysZone, obj 9307/9308) ----
    // Darkan Ectofuntus.kt:55-66: player.useLadder(tile) = lock 2, anim 828, teleport one tick later (was an instant teleport).
    // Level 58 (OSRS Wiki "Ectofuntus" shortcut; Darkan 53) kept.
    shortcut(9307, "Jump-up") {
        if (!player.hasAgility(58, "You need an Agility level of at least 58 to climb up this wall.")) return@shortcut
        obstacle {
            player.animate(828)
            wait(1)
            player.moveTo(Tile(3670, 9888, 3))
        }
    }
    shortcut(9308, "Jump-down") {
        if (!player.hasAgility(58, "You need an Agility level of at least 58 to climb down this wall.")) return@shortcut
        obstacle {
            player.animate(828)
            wait(1)
            player.moveTo(Tile(3671, 9888, 2))
        }
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
                // Darkan Morytania.java:83-87: useStairs(828, player.transform(0, 0, +-1), 1, 2) - climb_up 828 both ways, the floor
                // change one tick later (was 827 down and two ticks).
                player.animate(828)
                wait(1)
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
    // The rockslide 5847 (1x5, x 2760, z 3651-3655). Darkan Rellekka.java:175 (handleMountainCampWall): AgilityShortcuts.climbOver(
    // player.transform(x < obj.x ? +2 : -2, 0)) - one 839 forced move (33 / emoteTime / 20 - 5) from the route tile, no lane. +1 xp
    // (2009scape) kept.
    shortcut(5847, "Climb-over") {
        obstacle { target ->
            darkanClimbOver(player, player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0))
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
                // The table keeps the census plane (1); the live loc sits one plane lower on these bridge tiles, so only x/z
                // identify the rock and the jump stays on the player's own plane (batch 9: the plane-1 compare always returned).
                if (target.tile.x != tile.x || target.tile.z != tile.z) return@obstacle
                val landing = Tile(tile.x, tile.z, player.tile.height)
                if (player.tile.sameAs(landing)) {
                    player.message("I can't jump from here.")
                    return@obstacle
                }
                player.animate(ANIM_STEPPING_STONE_STEP)
                exactMove(player, landing, direction = Direction.between(player.tile, landing))
            }
        }
    }
    // The Gnome Stronghold course lives in gnome_agility.plugin.kts (Void port, 2026-10-03).

    // ---- All Fired Up beacon shortcuts (Darkan BeaconConstruction.kt; Trollheim / Death Plateau / Wilderness Deadman routes) ----
    // The base locs are varbit multilocs (667 cache): Burthorpe 34893 (varbit 5160: 34894 "Repair" climbing pegs / 34904 "Climb"),
    // Death Plateau 34992 (5161: 35101 "Repair" / 35099 "Climb"), Wilderness temple 38499 (5164: 38470 "Repair" / 38471 "Climb") and
    // 38497 (5164: 38468 no option / 38469 "Climb"). The God Wars windbreak 38495 (5163) has only a Repair in Darkan - no crossing.
    // Repair (Darkan :26-48 / :72-121 / :194-243): the skill level and the items, then the varbit is set to 1 (saved with the player's
    // varps; Darkan re-sends it at login). Darkan repairs the two halves of a ladder separately; here both items are needed at once.
    // Climb: Darkan walks to a fixed start tile on the player's side, then Agility.handleObstacle(3303, 1, start +- n) (5 / 6 / 7 tiles
    // across x). Darkan computes that landing from the click tile before its fade-screen walk ends and its Burthorpe start tiles lie on
    // the far side; the start tiles here are the open tiles in front of each loc on this map: Burthorpe (2939,3559) east / (2934,3559)
    // west, Death Plateau (2953,3617) / (2948,3617) (Darkan's), Wilderness temple (2956,3835) / (2950,3835). The fade is not shown.
    data class Beacon(
        val varbit: Int,
        val east: Tile,
        val west: Tile,
        val across: Int,
    )
    val beacons =
        mapOf(
            34893 to Beacon(5160, Tile(2939, 3559), Tile(2934, 3559), 5),
            34992 to Beacon(5161, Tile(2953, 3617), Tile(2948, 3617), 6),
            38499 to Beacon(5164, Tile(2956, 3835), Tile(2950, 3835), 7),
            38497 to Beacon(5164, Tile(2956, 3835), Tile(2950, 3835), 7),
        )
    fun beaconOf(player: Player): Pair<gg.rsmod.game.model.entity.GameObject, Beacon>? {
        val obj = player.getInteractingGameObj()
        return beacons[obj.id]?.let { obj to it }
    }
    listOf(34904, 35099, 38469, 38471).forEach { id ->
        shortcut(id, "Climb") {
            val (obj, beacon) = beaconOf(player) ?: return@shortcut
            obstacle {
                val east = player.tile.x > obj.tile.x
                val start = if (east) beacon.east else beacon.west
                walkToTile(player, start)
                face(player, if (east) Direction.WEST else Direction.EAST)
                darkanHandleObstacle(player, 3303, 1, Tile(start.x + if (east) -beacon.across else beacon.across, start.z, start.height))
            }
        }
    }
    // Burthorpe climbing pegs: 56 Smithing, 2 iron bars (Darkan :31-47).
    shortcut(34894, "Repair") {
        if (player.skills.getCurrentLevel(Skills.SMITHING) < 56) {
            player.message("You do not possess the required level of Smithing(56) to repair the ladder.")
            return@shortcut
        }
        if (player.inventory.getItemCount(Items.IRON_BAR) < 2) {
            player.message("You need 2 iron bars to repair the ladder.")
            return@shortcut
        }
        if (player.inventory.remove(Items.IRON_BAR, 2).hasSucceeded()) {
            player.message("You repair the ladder using 2 iron bars.")
            player.setVarbit(5160, 1)
        }
    }
    // Death Plateau ladder (42 Construction) and Wilderness temple ladder (59 Construction): 2 planks and 4 bronze nails (Darkan :72-121,
    // :194-243).
    listOf(35101 to Pair(5161, 42), 38470 to Pair(5164, 59)).forEach { (id, req) ->
        val (varbit, level) = req
        shortcut(id, "Repair") {
            if (player.skills.getCurrentLevel(Skills.CONSTRUCTION) < level) {
                player.message("You do not possess the required level of Construction($level) to repair the ladder.")
                return@shortcut
            }
            if (player.inventory.getItemCount(Items.PLANK) < 2 || player.inventory.getItemCount(Items.BRONZE_NAILS) < 4) {
                player.message("You need 2 planks and 4 bronze nails to repair the ladder.")
                return@shortcut
            }
            if (player.inventory.remove(Items.PLANK, 2).hasSucceeded() && player.inventory.remove(Items.BRONZE_NAILS, 4).hasSucceeded()) {
                player.message("You repair the ladder using 2 planks and 4 bronze nails.")
                player.setVarbit(varbit, 1)
            }
        }
    }

    // ---- Darkan shortcuts this server had no handler for (agent-handoffs/darkan-obstacles-map.md key finding 6) ----

    // Gu'Tanoth battlement 2832 (667: 2507,3011 / 2507,3012, rotation 1). Darkan GuTanoth.kt:13-16: level 20; handleObstacle(3303, 2,
    // player.transform(x < obj.x ? +2 : -2, 0), 1.0).
    shortcut(2832, "Climb-over") {
        if (!player.hasAgility(20)) return@shortcut
        obstacle { target ->
            darkanHandleObstacle(player, 3303, 2, player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0))
            player.addXp(Skills.AGILITY, 1.0)
        }
    }

    // Herblore Habitat climbable vine 56805 (667: 2949,2916 / 2952,2901 / 2954,2941, rotation 0, 3x1). Darkan Karamja.java:257-262:
    // rotation 3/4 -> x +-2, else z +-2 (+ when the player is below / west of the loc); handleObstacle(3303, 1, landing, 0).
    shortcut(56805, "Climb") {
        obstacle { target ->
            val landing =
                if (target.rot == 3) {
                    player.darkanTransform(if (player.tile.x < target.tile.x) 2 else -2, 0)
                } else {
                    player.darkanTransform(0, if (player.tile.z < target.tile.z) 2 else -2)
                }
            darkanHandleObstacle(player, 3303, 1, landing)
        }
    }

    // Herblore Habitat vine 56840 (667: 2968,2903,2 / 2971,2903,2). Darkan HerbloreHabitat.kt:106-118: on !skillSuccess(level, 185,
    // 255) Darkan plays 15545 ("TODO: Not authentic"; in the 667 cache 15545 is not a player sequence, skeleton 268) and nothing moves;
    // else forceMove(((x > obj.x ? 2967 : 2972), 2903, 2), x < obj.x ? EAST : WEST, 14554, 0, 100).
    shortcut(56840, "Cross") {
        obstacle { target ->
            if (!darkanSkillSuccess(player, 185, 255)) return@obstacle
            val east = player.tile.x < target.tile.x
            darkanForceMove(
                player,
                Tile(if (player.tile.x > target.tile.x) 2967 else 2972, 2903, 2),
                startCycles = 0,
                speedCycles = 100,
                anim = 14554,
                direction = if (east) Direction.EAST else Direction.WEST,
            )
        }
    }

    // Yanille gaps 2830 (2530,3026,1) / 2831 (2531,3029,0). Darkan Yanille.java:95-113 (handleGrottoBridge): face the end, anim 769,
    // schedule(0) tele(2830 -> (2530,3029,0); 2831 -> (2531,3026,0)) + anim -1.
    listOf(2830 to Tile(2530, 3029, 0), 2831 to Tile(2531, 3026, 0)).forEach { (id, end) ->
        shortcut(id, "Jump-over") {
            obstacle {
                player.faceTile(end)
                player.animate(ANIM_STEPPING_STONE_STEP)
                wait(1)
                player.moveTo(end)
                player.animate(-1)
            }
        }
    }

    // Tree Gnome Village loose railing 2186 (667: 2515,3161, wall rotation 3). Darkan Yanille.java:44 -> AgilityShortcuts.sidestep
    // (AgilityShortcuts.java:35-38): lock; schedule(1) forceMove(player.transform(0, y < obj.y ? +1 : -1), 3844, 0, 120).
    shortcut(2186, "Squeeze-through") {
        obstacle { target ->
            wait(1)
            darkanForceMove(player, player.darkanTransform(0, if (player.tile.z < target.tile.z) 1 else -1), startCycles = 0, speedCycles = 120, anim = ANIM_RAILING_SQUEEZE)
        }
    }

    // Ardougne loose railing 19171 (667: 2522,3375, wall rotation 2). Darkan Ardougne.java:438-441: level 20; useStairs(-1, (x >= 2523
    // ? 2522 : 2523, 3375), 1, 2, "You easily squeeze through the railing.") - teleport one tick later.
    shortcut(19171, "Squeeze-through") {
        if (!player.hasAgility(20)) return@shortcut
        obstacle {
            wait(1)
            player.moveTo(Tile(if (player.tile.x >= 2523) 2522 else 2523, 3375, 0))
            player.message("You easily squeeze through the railing.")
        }
    }

    // Abyss / ZMI crack 26844 (3309,4819) / 26845 (3311,4818). Darkan Ardougne.java:192-197: tele(3312,4817,0) / tele(3308,4819,0).
    shortcut(26844, "Squeeze-through") { obstacle { player.moveTo(Tile(3312, 4817, 0)) } }
    shortcut(26845, "Squeeze-through") { obstacle { player.moveTo(Tile(3308, 4819, 0)) } }

    // Ancient Guthix Temple walls (667: 40261 @2525,5834,0 / 2529,5834,0, 40262 @2524,5832,1 / 2530,5832,1, 40849 jump-down @2525,5835,1
    // / 2529,5835,1 / 2524,5833,2 / 2530,5833,2). Darkan AncientGuthixTemple.java:26-28: Climb-up walks to obj + (0, 1), then
    // useStairs(-1, player.transform(0, -1, +1), 1, 1); Jump-down useStairs(-1, player.transform(0, +1, -1), 1, 1) from the route tile.
    listOf(40261, 40262).forEach { id ->
        shortcut(id, "Climb-up") {
            obstacle { target ->
                walkToTile(player, target.tile.transform(0, 1))
                wait(1)
                player.moveTo(Tile(player.tile.x, player.tile.z - 1, player.tile.height + 1))
            }
        }
    }
    shortcut(40849, "Jump-down") {
        obstacle {
            wait(1)
            player.moveTo(Tile(player.tile.x, player.tile.z + 1, player.tile.height - 1))
        }
    }

    // Brimhaven Dungeon vines 5103-5107 (667: 2690,9564 / 2683,9569 / 2673,9499 / 2675,9479 / 2694,9482). Darkan BrimhavenDungeon.java:
    // 31-51: the best hatchet the player can use (else "You need a hatchet ..."); its chop animation; 4 ticks later tele to the vine's
    // tile +-1 (rotation 1/3: x, + when the player is west of it; else z, + when south of it).
    (5103..5107).forEach { id ->
        shortcut(id, "Chop-down") {
            val axe =
                gg.rsmod.plugins.content.skills.woodcutting.AxeType.values.reversed().firstOrNull {
                    player.skills.getCurrentLevel(Skills.WOODCUTTING) >= it.level &&
                        (player.equipment.contains(it.item) || player.inventory.contains(it.item))
                }
            if (axe == null) {
                player.message("You need a hatchet that you are able to use at your Woodcutting level to chop this.")
                return@shortcut
            }
            obstacle { target ->
                val alongX = target.rot == 1 || target.rot == 3
                val landing =
                    if (alongX) {
                        target.tile.transform(if (player.tile.x < target.tile.x) 1 else -1, 0)
                    } else {
                        target.tile.transform(0, if (player.tile.z < target.tile.z) 1 else -1)
                    }
                player.animate(axe.animation)
                wait(4)
                player.animate(-1)
                player.moveTo(landing)
            }
        }
    }

    // Brimhaven Dungeon hole 55342 (2681,9537, red dragons). Darkan BrimhavenDungeon.java:152-159 (checkDistance false): level 34;
    // walk to (2681,9540), forceMove((2681,9537), 14717, 0, 30), then anim 14718 + tele(2697,9524).
    shortcut(55342, "Jump") {
        if (!player.hasAgility(34)) return@shortcut
        obstacle {
            walkToTile(player, Tile(2681, 9540))
            darkanForceMove(player, Tile(2681, 9537), startCycles = 0, speedCycles = 30, anim = 14717)
            player.animate(14718)
            player.moveTo(Tile(2697, 9524))
        }
    }

    // Kethsi energy barrier 61584 (4205,5751). Darkan Kethsi.kt:41-43: tele(player.transform(x == 4206 ? -1 : +1, 0)).
    shortcut(61584, "Pass") {
        obstacle { player.moveTo(player.darkanTransform(if (player.tile.x == 4206) -1 else 1, 0)) }
    }
    // Kethsi ledges 10372 (4016,5706,1) / 10390 (4013,5706,1). Darkan Kethsi.kt:73-77: walk onto the ledge's tile, then
    // forceMove(player.transform(10372 ? -3 : +3, 0), 11729, 20, 60).
    listOf(10372 to -3, 10390 to 3).forEach { (id, dx) ->
        shortcut(id, "Jump-from") {
            obstacle { target ->
                walkToTile(player, Tile(target.tile))
                darkanForceMove(player, player.darkanTransform(dx, 0), startCycles = 20, speedCycles = 60, anim = 11729)
            }
        }
    }

    // Lunar Isle mine passage 11399 (667: 2335,10346,2 rotation 3 / 2341,10355,2 rotation 1). Darkan LunarIsle.java:77-82:
    // useStairs(827, rotation 3 ? (2341,10356,2) : (2335,10345,2)) - anim 827, teleport one tick later. The third 11399 placement
    // (2911,5738,0) is another cave: the object-teleport table.
    shortcut(11399, "Crawl-through") {
        val obj = player.getInteractingGameObj()
        if (obj.tile.height != 2 || obj.tile.z < 10340) {
            gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
            return@shortcut
        }
        obstacle { target ->
            player.animate(827)
            wait(1)
            player.moveTo(if (target.rot == 3) Tile(2341, 10356, 2) else Tile(2335, 10345, 2))
        }
    }

    // Tirannwn tripwires 3921 (1x2; 667: 2215,3154 r2, 2220,3153 r0, 2251,3168 r3, 2285,3188 r3, 2294,3243 r0, 2343,4562 r2, 2348,4561
    // r0). Darkan Tirannwn.java:63-79 (handleTripWire): within 3 tiles; rotation 0/2 -> z: y > obj.y ? (obj.x, obj.y - 1) : (obj.x,
    // obj.y + 2); rotation 1/3 -> x: x > obj.x ? (obj.x - 1, obj.y) : (obj.x + 2, obj.y); addWalkSteps(.., 5, false) = a collision-free
    // walk of at most 5 steps, no animation.
    shortcut(3921, "Step-over") {
        obstacle { target ->
            if (!player.tile.isWithinRadius(target.tile, 3)) return@obstacle
            val t = target.tile
            val landing =
                if (target.rot == 0 || target.rot == 2) {
                    Tile(t.x, if (player.tile.z > t.z) t.z - 1 else t.z + 2, t.height)
                } else {
                    Tile(if (player.tile.x > t.x) t.x - 1 else t.x + 2, t.z, t.height)
                }
            walkOverTile(player, landing, Render.NONE)
        }
    }

    // Yanille trellis 20056 (2548,3119). Darkan Yanille.java:115-121: level 18 ("You need 18 Agility to climb that."); useLadder((2548,
    // 3118,1)) = anim 828, teleport one tick later; 31 xp. The other placement (2932,4719) stays on the object-teleport table.
    shortcut(20056, "Climb-up") {
        val obj = player.getInteractingGameObj()
        if (obj.tile.x != 2548 || obj.tile.z != 3119) {
            gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
            return@shortcut
        }
        if (!player.hasAgility(18, "You need 18 Agility to climb that.")) return@shortcut
        obstacle {
            player.animate(828)
            wait(1)
            player.moveTo(Tile(2548, 3118, 1))
            player.addXp(Skills.AGILITY, 31.0)
        }
    }

    // Mort Myre swamp shortcut ladder 5054 (667: 3477,9846, "Climb-up"; no binding and no object-teleport entry before). Darkan
    // Morytania.java:103 (handleSwampTrapdoorShortcut): ladder((3495,3466,0)) = anim 828, teleport one tick later.
    shortcut(5054, "Climb-up") {
        obstacle {
            player.animate(828)
            wait(1)
            player.moveTo(Tile(3495, 3466, 0))
        }
    }
}

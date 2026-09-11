package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.Brother
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.Corner

/**
 * Barrows bindings: mounds, crypts, tunnels, puzzle doors, chest and underground timers.
 */
val BARROWS_TIMER = TimerKey()
val WAS_UNDERGROUND = AttributeKey<Boolean>()
val CRYPT_CREATURES = intArrayOf(Npcs.BLOODWORM, Npcs.CRYPT_RAT, Npcs.GIANT_CRYPT_RAT, Npcs.CRYPT_SPIDER, 2035, 2036, Npcs.SKELETON_2037, 5381, 5422)

/* ---------------------------------------- mounds ---------------------------------------- */


/* ---------------------------------------- crypts ---------------------------------------- */

Brother.values().forEach { brother ->
    on_obj_option(obj = brother.sarcophagus, option = "search") {
        Barrows.ensureRun(player)
        val hidden = player.attr[Barrows.SELECTED_BROTHER] == brother.key
        if (hidden) {
            player.queue {
                messageBox("You've found a hidden tunnel, do you want to enter?")
                val choice = options("Yeah, I'm fearless!", "No way, looks scary!")
                if (choice == 1) {
                    val corner = Corner.values().firstOrNull { it.key == player.attr[Barrows.EXIT_CORNER] } ?: Corner.NORTH_EAST
                    player.moveTo(Tile(corner.tile.x, corner.tile.z, 0))
                    Barrows.openOverlay(player)
                }
            }
        } else if (Barrows.liveBrother(player, brother) == null && !Barrows.isKilled(player, brother)) {
            val tile = world.findRandomTileAround(brother.spawn, radius = 2) ?: brother.spawn
            Barrows.spawnBrother(player, brother, tile)
        } else {
            player.filterableMessage("You don't find anything.")
        }
    }

    on_obj_option(obj = brother.stairs, option = "climb-up") {
        Barrows.liveBrother(player, brother)?.let { if (it.isSpawned()) world.remove(it) }
        Barrows.spawned(player).remove(brother.key)
        player.queue {
            player.animate(Anims.LADDER_CLIMB)
            wait(1)
            player.moveTo(Tile(brother.hillX.first + 1, brother.hillZ.first + 1, 0))
        }
    }
}

/* ---------------------------------------- tunnels ---------------------------------------- */

on_obj_option(obj = Objs.ROPE_6708, option = "climb-up") {
    val brother = Brother.byKey(player.attr[Barrows.SELECTED_BROTHER] ?: Brother.DHAROK.key)
    player.queue {
        player.animate(Anims.LADDER_CLIMB)
        wait(2)
        player.moveTo(brother.spawn)
    }
}

(Barrows.TUNNEL_DOORS + Barrows.PUZZLE_DOORS).filter { door ->
    val options = world.definitions.getNullable(ObjectDef::class.java, door)?.options?.filterNotNull() ?: emptyList()
    options.any { it.equals("open", ignoreCase = true) }
}.forEach { door ->
    on_obj_option(obj = door, option = "open") {
        val obj = player.getInteractingGameObj()
        player.queue {
            if (door in Barrows.PUZZLE_DOORS && !Barrows.inInnerRoom(player.tile)) {
                if (!solvePuzzle(this)) {
                    Barrows.shufflePuzzle(player)
                    player.message("You got the puzzle wrong! You can hear the catacombs moving around you.")
                    return@queue
                }
                player.message("You hear the doors' locking mechanism grind open.")
            }
            val start = Tile(player.tile)
            walkThroughDoor(player, obj.tile, obj.rot)
            wait(1)
            val dx = player.tile.x - start.x
            val dz = player.tile.z - start.z
            val spawn = player.tile.transform(if (dx != 0) dx.coerceIn(-1, 1) else 0, if (dz != 0) dz.coerceIn(-1, 1) else 0)
            Barrows.onTunnelDoor(player, spawn)
        }
    }
}

suspend fun QueueTask.walkThroughDoor(player: Player, doorTile: Tile, rotation: Int) {
    val dx = player.tile.x - doorTile.x
    val dz = player.tile.z - doorTile.z
    val horizontal = rotation == 1 || rotation == 3
    val destination = when {
        horizontal && dx >= 0 -> doorTile.transform(-1, 0)
        horizontal -> doorTile.transform(1, 0)
        dz >= 0 -> doorTile.transform(0, -1)
        else -> doorTile.transform(0, 1)
    }
    if (player.tile != doorTile) {
        player.walkTo(this, doorTile, detectCollision = false)
        wait(1)
    }
    player.walkTo(this, destination, detectCollision = false)
    wait(1)
    player.moveTo(destination)
}

/**
 * Puzzle door: three shapes in a sequence; pick the fourth from three choices.
 */
suspend fun solvePuzzle(task: QueueTask): Boolean {
    val player = task.player
    val puzzle = Barrows.PUZZLES.random()
    player.openInterface(interfaceId = Barrows.PUZZLE_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
    puzzle.options.forEachIndexed { i, model -> player.setComponentModel(Barrows.PUZZLE_INTERFACE, 6 + i, model) }
    val choices = puzzle.choices.toList().shuffled()
    val components = intArrayOf(2, 3, 5)
    choices.forEachIndexed { i, model -> player.setComponentModel(Barrows.PUZZLE_INTERFACE, components[i], model) }
    player.attr[Barrows.PUZZLE_ANSWER] = choices.indexOf(puzzle.answer)
    val chosen = task.waitForPuzzleChoice()
    player.closeInterface(Barrows.PUZZLE_INTERFACE)
    return chosen == player.attr[Barrows.PUZZLE_ANSWER]
}

val PUZZLE_CHOICE = AttributeKey<Int>()

suspend fun QueueTask.waitForPuzzleChoice(): Int {
    player.attr.remove(PUZZLE_CHOICE)
    while (player.attr[PUZZLE_CHOICE] == null) {
        if (!player.isOnline || !player.interfaces.isVisible(Barrows.PUZZLE_INTERFACE)) return -1
        wait(1)
    }
    return player.attr[PUZZLE_CHOICE] ?: -1
}

intArrayOf(2, 3, 5).forEachIndexed { index, component ->
    on_button(interfaceId = Barrows.PUZZLE_INTERFACE, component = component) {
        player.attr[PUZZLE_CHOICE] = index
    }
}

/* ---------------------------------------- chest ---------------------------------------- */

on_obj_option(obj = Objs.CHEST_6774, option = "open") {
    player.attr[Barrows.CHEST_OPEN] = true
    val brother = Brother.byKey(player.attr[Barrows.SELECTED_BROTHER] ?: Brother.DHAROK.key)
    if (!Barrows.isKilled(player, brother) && Barrows.liveBrother(player, brother) == null) {
        var tile = Barrows.CHEST_TILES.random()
        if (tile == player.tile) tile = tile.transform(1, 0)
        Barrows.spawnBrother(player, brother, tile)
    }
    player.filterableMessage("You open the chest.")
}

on_obj_option(obj = Objs.CHEST_6775, option = "search") {
    if (player.attr[Barrows.LOOTED] == true || !Barrows.inTunnels(player.tile)) {
        player.message("The chest is empty.")
        return@on_obj_option
    }
    val hidden = Brother.byKey(player.attr[Barrows.SELECTED_BROTHER] ?: Brother.DHAROK.key)
    if (Barrows.liveBrother(player, hidden) != null) {
        player.message("You can't loot the chest while ${hidden.key.replaceFirstChar { it.uppercase() }} is still awake!")
        return@on_obj_option
    }
    Barrows.loot(player)
}

on_obj_option(obj = Objs.CHEST_6775, option = "close") {
    player.attr.remove(Barrows.CHEST_OPEN)
}

/* ---------------------------------------- timers ---------------------------------------- */

on_login {
    player.timers[BARROWS_TIMER] = 1
}

on_timer(BARROWS_TIMER) {
    val underground = Barrows.underground(player.tile)
    val was = player.attr[WAS_UNDERGROUND] ?: false
    if (underground && !was) {
        player.attr[WAS_UNDERGROUND] = true
        Barrows.ensureRun(player)
        Barrows.openOverlay(player)
        player.attr[PRAYER_DRAIN_TICK] = Barrows.PRAYER_DRAIN_TICKS
    } else if (!underground && was) {
        player.attr[WAS_UNDERGROUND] = false
        Barrows.resetRun(player)
        if (!Barrows.onSurface(player.tile)) Barrows.closeOverlay(player)
        player.setVarbit(Barrows.IN_TUNNEL_VARBIT, 0)
    } else if (underground) {
        val drain = (player.attr[PRAYER_DRAIN_TICK] ?: Barrows.PRAYER_DRAIN_TICKS) - 1
        if (drain <= 0) {
            Barrows.drainPrayer(player)
            player.attr[PRAYER_DRAIN_TICK] = Barrows.PRAYER_DRAIN_TICKS
        } else {
            player.attr[PRAYER_DRAIN_TICK] = drain
        }
        val collapse = player.attr[Barrows.COLLAPSE_TICKS]
        if (collapse != null && Barrows.inTunnels(player.tile)) {
            if (collapse % 9 == 0) {
                player.graphic(60)
                player.forceChat("Ouch!")
                player.hit(30 + world.random(20), HitType.REGULAR_HIT)
                player.message("Some rocks fall from the ceiling and hit you.")
            }
            player.attr[Barrows.COLLAPSE_TICKS] = collapse + 1
        }
    } else if (was.not() && !Barrows.onSurface(player.tile) && player.attr[WAS_ON_SURFACE] == true) {
        player.attr[WAS_ON_SURFACE] = false
        Barrows.closeOverlay(player)
    }
    if (Barrows.onSurface(player.tile)) player.attr[WAS_ON_SURFACE] = true
    player.timers[BARROWS_TIMER] = 1
}

val PRAYER_DRAIN_TICK = AttributeKey<Int>()
val WAS_ON_SURFACE = AttributeKey<Boolean>()

on_logout {
    Barrows.removeBrothers(player)
}

CRYPT_CREATURES.forEach { id ->
    on_npc_death(id) {
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        if (Barrows.underground(npc.tile)) Barrows.registerMonsterKill(killer, npc)
    }
}

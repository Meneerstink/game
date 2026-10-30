package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.Brother
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.Corner
import gg.rsmod.plugins.api.cfg.Sfx

/**
 * Barrows bindings: mounds, crypts, tunnels, puzzle doors, chest and underground timers.
 */
val BARROWS_TIMER = TimerKey()
val WAS_UNDERGROUND = AttributeKey<Boolean>()
val CRYPT_CREATURES = intArrayOf(Npcs.BLOODWORM, Npcs.CRYPT_RAT, Npcs.GIANT_CRYPT_RAT, Npcs.CRYPT_SPIDER, 2035, 2036, Npcs.SKELETON_2037, 5381, 5422, Npcs.GIANT_CRYPT_RAT_4920, Npcs.GIANT_CRYPT_RAT_4921)

/* ---------------------------------------- mounds ---------------------------------------- */


/* ---------------------------------------- crypts ---------------------------------------- */

Brother.values().forEach { brother ->
    on_obj_option(obj = brother.sarcophagus, option = "search") {
        Barrows.ensureRun(player)
        val result = Barrows.searchResult(player, brother)
        if (result == Barrows.SearchResult.HIDDEN_TUNNEL) {
            player.queue {
                messageBox("You've found a hidden tunnel, do you want to enter?")
                val choice = options("Yeah, I'm fearless!", "No way, looks scary!")
                if (choice == 1) {
                    val corner = Corner.values().firstOrNull { it.key == player.attr[Barrows.EXIT_CORNER] } ?: Corner.NORTH_EAST
                    player.moveTo(Tile(corner.tile.x, corner.tile.z, 0))
                    player.attr[Barrows.IN_TUNNEL] = false
                    Barrows.openOverlay(player)
                }
            }
        } else if (result == Barrows.SearchResult.WAKE_BROTHER) {
            val tile = world.findRandomTileAround(brother.spawn, radius = 2) ?: brother.spawn
            Barrows.spawnBrother(player, brother, tile)
        } else {
            player.filterableMessage("You don't find anything.")
        }
    }

    on_obj_option(obj = brother.stairs, option = "climb-up") {
        Barrows.liveBrother(player, brother)?.let { if (it.isSpawned()) world.remove(it) }
        Barrows.spawned(player).remove(brother.key)
        // F10 (owner 2026-10-03 "there is no teleport delay in barrows"): animate(828) + wait(1) moved the player on the next
        // cycle (a fresh queue's first wait resumes one cycle early). The shared ladder timing: wait(1), climb animation, two ticks.
        player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
            wait(1)
            player.animate(Anims.LADDER_CLIMB)
            wait(2)
            player.moveTo(Tile(brother.hillX.first + 1, brother.hillZ.first + 1, 0))
        }
    }
}

/* ---------------------------------------- tunnels ---------------------------------------- */

on_obj_option(obj = Objs.ROPE_6708, option = "climb-up") {
    val brother = Brother.byKey(player.attr[Barrows.SELECTED_BROTHER] ?: Brother.DHAROK.key)
    // Void BarrowsCrypts rope: anim("climb_up"), delay(2), tele. F10: the shared ladder timing (ladders.plugin.kts) - wait(1) absorbs
    // the early first resume of a fresh queue, then the climb animation and two ticks.
    player.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
        wait(1)
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
        // The click dispatches the transformed child (6714/6733); the map object is the multiloc parent (6716..6750).
        val obj = player.getInteractingGameObj()
        // E10: a locked door has no "Open" on the client; never let a forged or stale click through it on the server either.
        if (Barrows.isLockedDoor(player, obj.id)) return@on_obj_option
        if (Barrows.shouldSolvePuzzle(player.tile, obj.id)) {
            // Owner 2026-10-03 20:49 ("when u try to select one puzzle nothing happens"): the puzzle used to wait for the answer
            // inside a STANDARD queue, and a STANDARD queue is paused while a main-screen interface is open (PawnQueueTaskSet
            // isPaused) - the answer buttons set the choice but the waiting task never ran again. Void BarrowsPuzzle resumes on
            // the button; here the button handler itself finishes the door.
            openPuzzle(player, obj)
            return@on_obj_option
        }
        player.queue { passDoor(obj) }
    }
}

/**
 * Void BarrowsCrypts "Open": door sound, `enterDoor` (walk with collision to the tile in front of the door on the player's side, the
 * double door swings open, one step through, the door shuts after three ticks), toggle `barrows_in_tunnel`, then the door's monster
 * one tile further on. The crossing is the shared bounded [DoorCrossing.enter]; from anywhere it cannot cross, nothing moves.
 */
suspend fun QueueTask.passDoor(door: GameObject) {
    // N467 (owner 2026-10-30: the close sound played twice): Void's extra sound("barrows_door_close") (41) on operating is gone. The
    // door now sounds like every other door: open (62) when it swings open, close (41) to everyone nearby when it shuts (swingOpen).
    val near = DoorCrossing.nearSide(player.tile, door) ?: return
    val far = DoorCrossing.destination(near, door) ?: return
    if (!DoorCrossing.enter(this, player, door, open = { Barrows.swingOpen(player, door) })) return
    Barrows.syncTunnelDarkness(player)
    Barrows.onTunnelDoor(player, far.transform(far.x - near.x, far.z - near.z))
}

/**
 * Puzzle door: three shapes in a sequence; pick the fourth from three choices (Void BarrowsPuzzle: interface 25, sequence models on
 * components 6/7/8, the choices on the buttons 2/3/5).
 */
fun openPuzzle(player: Player, door: GameObject) {
    val puzzle = Barrows.PUZZLES.random()
    player.openInterface(interfaceId = Barrows.PUZZLE_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
    puzzle.options.forEachIndexed { i, model -> player.setComponentModel(Barrows.PUZZLE_INTERFACE, Barrows.PUZZLE_OPTION_COMPONENTS[i], model) }
    val choices = puzzle.choices.toList().shuffled()
    choices.forEachIndexed { i, model -> player.setComponentModel(Barrows.PUZZLE_INTERFACE, Barrows.PUZZLE_CHOICE_COMPONENTS[i], model) }
    player.attr[Barrows.PUZZLE_ANSWER] = choices.indexOf(puzzle.answer)
    player.attr[Barrows.PUZZLE_DOOR] = door
}

Barrows.PUZZLE_CHOICE_COMPONENTS.forEachIndexed { index, component ->
    on_button(interfaceId = Barrows.PUZZLE_INTERFACE, component = component) {
        val door = player.attr[Barrows.PUZZLE_DOOR]
        val answer = player.attr[Barrows.PUZZLE_ANSWER]
        player.attr.remove(Barrows.PUZZLE_DOOR, Barrows.PUZZLE_ANSWER)
        player.closeInterface(Barrows.PUZZLE_INTERFACE)
        if (door == null || answer == null) return@on_button
        if (!Barrows.puzzleAnswered(index, answer)) {
            // OSRS Wiki Barrows/Strategies: "If you get the puzzle wrong, the doors and rooms within the tunnels will shift around".
            Barrows.shufflePuzzle(player, incorrect = true)
            // Owner 2026-10-06 (bug 162848, choice "667 rumbling sound"): no donor plays a sound here; 667 sound 1678 "rumbling".
            player.playSound(Sfx.RUMBLING)
            player.message("You got the puzzle wrong! You can hear the catacombs moving around you.")
            return@on_button
        }
        player.playSound(Sfx.BARROWS_DOOR_UNLOCK)
        player.message("You hear the doors' locking mechanism grind open.")
        player.queue { passDoor(door) }
    }
}

/* ---------------------------------------- chest ---------------------------------------- */

on_obj_option(obj = Objs.CHEST_6774, option = "open") {
    player.attr[Barrows.CHEST_OPEN] = true
    // RCV-010 C1: map chest 10284 is a multiloc on varbit 1394; without it the chest never became 6775 (Search).
    player.setVarbit(Barrows.CHEST_VARBIT, 1)
    val brother = Brother.byKey(player.attr[Barrows.SELECTED_BROTHER] ?: Brother.DHAROK.key)
    if (!Barrows.isKilled(player, brother) && Barrows.liveBrother(player, brother) == null) {
        var tile = Barrows.CHEST_TILES.random()
        if (tile == player.tile) tile = tile.transform(1, 0)
        Barrows.spawnBrother(player, brother, tile)
    }
    // Owner 2026-10-03 20:3x (F8): the chest opened without a sound (667 sound 52 "chest_open", Void obj.sounds.toml).
    player.playSound(Sfx.CHEST_OPEN)
    player.filterableMessage("You open the chest.")
}

on_obj_option(obj = Objs.CHEST_6775, option = "search") {
    if (player.attr[Barrows.LOOTED] == true || !Barrows.inTunnels(player.tile)) {
        player.message("The chest is empty.")
        return@on_obj_option
    }
    // OSRS Wiki Barrows/Strategies: killing the brother the chest wakes "is not required to loot".
    Barrows.loot(player)
}

// The reward window's item grid (Barrows.showReward): Examine, the only option, as in 2009scape RewardChest.
on_button(interfaceId = Barrows.REWARD_INTERFACE, component = Barrows.REWARD_LAYER) {
    val item = player.attr[Barrows.REWARD_ITEMS]?.getOrNull(player.getInteractingSlot()) ?: return@on_button
    player.message(world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).examine ?: "It's a ${world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).name}.")
}

on_obj_option(obj = Objs.CHEST_6775, option = "close") {
    player.playSound(Sfx.CHEST_CLOSE)
    player.attr.remove(Barrows.CHEST_OPEN)
    player.setVarbit(Barrows.CHEST_VARBIT, 0)
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
        player.attr[Barrows.IN_TUNNEL] = false
        Barrows.ensureRun(player)
        Barrows.openOverlay(player)
        player.attr[PRAYER_DRAIN_TICK] = Barrows.nextDrainTicks()
    } else if (!underground && was) {
        player.attr[WAS_UNDERGROUND] = false
        Barrows.resetRun(player)
        if (!Barrows.onSurface(player.tile)) Barrows.closeOverlay(player)
        player.attr[Barrows.IN_TUNNEL] = false
        player.setVarbit(Barrows.IN_TUNNEL_VARBIT, 0)
        Barrows.clearHeads(player)
        player.attr.remove(HEAD_CLEAR_TICK)
    } else if (underground) {
        Barrows.syncTunnelDarkness(player)
        val drain = (player.attr[PRAYER_DRAIN_TICK] ?: Barrows.nextDrainTicks()) - 1
        if (drain <= 0) {
            Barrows.drainPrayer(player)
            player.attr[PRAYER_DRAIN_TICK] = Barrows.nextDrainTicks()
            player.attr[HEAD_CLEAR_TICK] = Barrows.HEAD_SHOW_TICKS
        } else {
            player.attr[PRAYER_DRAIN_TICK] = drain
        }
        // Darkan: the face is cleared 3 ticks after it was shown.
        val clear = player.attr[HEAD_CLEAR_TICK]
        if (clear != null) {
            if (clear <= 1) {
                player.attr.remove(HEAD_CLEAR_TICK)
                Barrows.clearHeads(player)
            } else {
                player.attr[HEAD_CLEAR_TICK] = clear - 1
            }
        }
        val collapse = player.attr[Barrows.COLLAPSE_TICKS]
        Barrows.collapseShake(player, Barrows.inTunnels(player.tile))
        if (collapse != null && Barrows.inTunnels(player.tile)) {
            if (collapse % 9 == 0) {
                player.graphic(60)
                player.forceChat("Ouch!")
                // OSRS: the collapsing roof deals "insignificant damage"; 30..50 was the x10 life-point value (ROOT_CAUSES #72).
                player.hit(3 + world.random(2), HitType.REGULAR_HIT)
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
val HEAD_CLEAR_TICK = AttributeKey<Int>()
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

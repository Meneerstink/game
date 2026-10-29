package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.plugins.content.skills.agility.doorStep

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.player

on_obj_option(obj = Objs.DOOR_15653, option = "open") {
    if (getCombinedLevels(player) < 130) {
        player.message("Your attack and strength must total 130 together or higher for you to enter.")
    } else {
        handleMainDoor(player)
    }
}

/*
 * N375 (owner 2026-10-24: "deuren bovenste verdieping, vermoedelijk ook andere, blijven open") and N373 (the cyclops hit a player
 * standing outside the top-floor door): the guild's inner doors were single-id wall doors the general door script swings open and
 * leaves open. Novite WarriorsGuild (15641 / 15644 / 15647 doors, 15658 / 15660 heavy doors) opens them for one crossing only:
 * the player walks through and the door shuts behind him - so the cyclops room is closed again and nothing sees through it.
 */
val GUILD_DOORS = intArrayOf(Objs.DOOR_15641, Objs.DOOR_15644, Objs.DOOR_15647, Objs.HEAVY_DOOR, Objs.HEAVY_DOOR_15660)

GUILD_DOORS.forEach { id ->
    on_obj_option(obj = id, option = "open") {
        val obj = player.getInteractingGameObj()
        player.lockingQueue(lockState = LockState.FULL) {
            gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
                this, player, obj,
                open = {
                    val opened = world.openDoor(obj, opened = obj.id)
                    player.playSound(Sfx.DOOR_OPEN)
                    world.queue {
                        wait(3)
                        world.remove(opened)
                        world.spawn(DynamicObject(obj))
                        world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = Tile(obj.tile), id = Sfx.DOOR_CLOSE, radius = 5, volume = 1))
                    }
                },
            )
        }
    }
}

// N374: the next defender in order (WarriorsGuildDefenders), with a red line when it drops.
on_npc_killed { killer, npc ->
    if (npc.id !in WarriorsGuildDefenders.CYCLOPES || !WarriorsGuildDefenders.inGuild(npc.tile)) return@on_npc_killed
    val defender = WarriorsGuildDefenders.next(killer, npc.id) ?: return@on_npc_killed
    if (world.random(WarriorsGuildDefenders.chance(defender) - 1) != 0) return@on_npc_killed
    WarriorsGuildDefenders.earn(killer, defender)
    val tile = npc.tile
    world.queue {
        wait(4)
        world.spawn(gg.rsmod.game.model.entity.GroundItem(defender, 1, tile, killer))
        val name = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, defender).name
        killer.message("<col=ef1020>The cyclops dropped a $name!")
    }
}

fun getCombinedLevels(player: Player): Int {
    val strengthLevel = player.skills.getCurrentLevel(Skills.STRENGTH)
    val attackLevel = player.skills.getCurrentLevel(Skills.ATTACK)
    return strengthLevel + attackLevel
}

fun handleMainDoor(player: Player) {
    val closedDoor = DynamicObject(id = 15653, type = 0, rot = 0, tile = Tile(x = 2877, z = 3542))
    player.lock = LockState.DELAY_ACTIONS
    world.remove(closedDoor)
    val door = DynamicObject(id = 15653, type = 0, rot = 1, tile = Tile(x = 2876, z = 3542))
    player.playSound(Sfx.DOOR_OPEN)
    world.spawn(door)

    player.queue {
        val x = if (player.tile.x == 2877) 2876 else 2877
        val z = 3542
        doorStep(player, Tile(x = x, z = z))
        wait(3)
        world.remove(door)
        player.lock = LockState.NONE
        world.spawn(closedDoor)
        player.playSound(Sfx.DOOR_CLOSE)
    }
}

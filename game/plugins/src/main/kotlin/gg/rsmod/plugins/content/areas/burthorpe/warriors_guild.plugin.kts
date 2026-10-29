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

// ---- K06 N372 part 1: the Magical animator (WarriorsGuildAnimator) ----
WarriorsGuildAnimator.Set.values().flatMap { it.pieces }.forEach { piece ->
    on_item_on_obj(obj = WarriorsGuildAnimator.ANIMATOR, item = piece) {
        animate(player)
    }
}

fun animate(player: Player) {
    if (WarriorsGuildAnimator.active(player) != null) {
        player.message(WarriorsGuildAnimator.MSG_BUSY)
        return
    }
    val used = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get()?.id
    val set = used?.let { WarriorsGuildAnimator.setOf(it) }
    if (set == null || set.pieces.any { !player.inventory.contains(it) }) {
        player.message(WarriorsGuildAnimator.MSG_INCOMPLETE)
        return
    }
    val animator = player.getInteractingGameObj()
    set.pieces.forEach { player.inventory.remove(it, 1) }
    player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {
        player.faceTile(animator.tile)
        player.animate(WarriorsGuildAnimator.PUT_ANIM)
        player.message(WarriorsGuildAnimator.MSG_HUMS)
        wait(2)
        player.message(WarriorsGuildAnimator.MSG_STAND_BACK)
        wait(2)
        // On the tile in front of the animator (the room side): the animator itself is solid, an npc on it found no route out.
        val armour = Npc(player, set.npc, animator.tile.transform(0, 1), world)
        armour.respawns = false
        world.spawn(armour)
        player.attr[WarriorsGuildAnimator.ANIMATION] = java.lang.ref.WeakReference(armour)
        armour.animate(WarriorsGuildAnimator.RISE_ANIM)
        armour.forceChat("I'm ALIVE!")
        armour.attack(player)
        watch(player, armour, set)
    }
}

/** Novite `AnimatedArmor.finish`: the fight ends without a kill (the owner left, logged out or died) - the armour comes back. */
fun watch(player: Player, armour: Npc, set: WarriorsGuildAnimator.Set) {
    world.queue {
        while (world.npcs.contains(armour) && !armour.isDead()) {
            wait(5)
            val gone = !player.isOnline || player.isDead() || player.tile.height != armour.tile.height || !player.tile.isWithinRadius(armour.tile, 16)
            if (gone && world.npcs.contains(armour) && !armour.isDead()) {
                world.remove(armour)
                player.attr.remove(WarriorsGuildAnimator.ANIMATION)
                set.pieces.forEach { piece ->
                    if (!player.inventory.add(piece, 1).hasSucceeded()) player.bank.add(piece, 1)
                }
                return@queue
            }
        }
    }
}

WarriorsGuildAnimator.Set.values().forEach { set ->
    // The set's own table (the Drop Viewer reads it, and the bulk table - 5 tokens and one random piece - no longer rolls here);
    // the drop itself is below, 1 in 16 without one piece.
    gg.rsmod.plugins.content.drops.DropTableFactory.register(
        gg.rsmod.plugins.content.drops.DropTableFactory.build {
            guaranteed {
                obj(Items.WARRIOR_GUILD_TOKEN, quantity = set.tokens)
                set.pieces.forEach { obj(it) }
            }
        },
        set.npc,
    )
    on_npc_death(set.npc) {
        val owner = npc.owner ?: return@on_npc_death
        owner.attr.remove(WarriorsGuildAnimator.ANIMATION)
        val roll = if (world.random(WarriorsGuildAnimator.BREAK_ONE_IN - 1) == 0) world.random(2) else -1
        WarriorsGuildAnimator.returned(set, roll).forEach { piece ->
            world.spawn(gg.rsmod.game.model.entity.GroundItem(piece, 1, npc.tile, owner))
        }
        world.spawn(gg.rsmod.game.model.entity.GroundItem(Items.WARRIOR_GUILD_TOKEN, set.tokens, npc.tile, owner))
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

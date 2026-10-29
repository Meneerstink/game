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
        // N372 part 2: into the cyclops room only with 100 tokens (OSRS Wiki "Warriors' Guild"); an Attack cape (or the max cape,
        // all perks) needs none.
        val intoCyclops = obj.tile.sameAs(WarriorsGuildAnimator.CYCLOPS_DOOR) && !WarriorsGuildAnimator.inCyclopsRoom(player.tile)
        val free = freeCyclopsEntry(player)
        if (intoCyclops && !free && player.inventory.getItemCount(Items.WARRIOR_GUILD_TOKEN) < WarriorsGuildAnimator.TOKENS_TO_ENTER) {
            player.message(WarriorsGuildAnimator.MSG_NOT_ENOUGH)
            return@on_obj_option
        }
        player.lockingQueue(lockState = LockState.FULL) {
            val crossed = gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
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
            if (crossed && intoCyclops && !free) {
                // "Ten tokens are spent upon entering the room, and another ten every minute inside."
                player.inventory.remove(Items.WARRIOR_GUILD_TOKEN, WarriorsGuildAnimator.TOKENS_PER_MINUTE)
                player.timers[CYCLOPS_TOKEN_TIMER] = WarriorsGuildAnimator.MINUTE_TICKS
            }
        }
    }
}

fun freeCyclopsEntry(player: Player): Boolean =
    gg.rsmod.plugins.content.skills.SkillcapePerks.wornCape(player).let { it == Items.ATTACK_CAPE || it == Items.ATTACK_CAPE_T } ||
        gg.rsmod.plugins.content.skills.SkillcapePerks.wearingMaxCape(player)

/**
 * N372 part 2: 10 tokens a minute in the cyclops room; with none left the player gets one more minute, then goes back to the lobby
 * (OSRS Wiki: "You get one minute before being teleported out"). Leaving the room ends it.
 */
val CYCLOPS_TOKEN_TIMER = gg.rsmod.game.model.timer.TimerKey()

on_timer(CYCLOPS_TOKEN_TIMER) {
    if (!WarriorsGuildAnimator.inCyclopsRoom(player.tile)) return@on_timer
    val held = player.inventory.getItemCount(Items.WARRIOR_GUILD_TOKEN)
    if (held <= 0) {
        player.moveTo(WarriorsGuildAnimator.OUTSIDE_CYCLOPS)
        player.message(WarriorsGuildAnimator.MSG_RUN_OUT)
        return@on_timer
    }
    player.inventory.remove(Items.WARRIOR_GUILD_TOKEN, minOf(held, WarriorsGuildAnimator.TOKENS_PER_MINUTE))
    player.timers[CYCLOPS_TOKEN_TIMER] = WarriorsGuildAnimator.MINUTE_TICKS
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

// ---- K06 N372 part 3: the dummy room (WarriorsGuildAnimator.DUMMY_POSTS) ----
/** The dummy up now (one at a time, as Novite's guild timer) and the players who already hit it. */
var dummyUp: gg.rsmod.game.model.entity.DynamicObject? = null
val dummyHitBy = HashSet<String>()

on_world_init {
    world.queue {
        while (true) {
            wait(WarriorsGuildAnimator.DUMMY_EVERY)
            if (world.players.none { WarriorsGuildAnimator.inDummyRoom(it.tile) }) continue
            val (tile, rot) = WarriorsGuildAnimator.DUMMY_POSTS[world.random(WarriorsGuildAnimator.DUMMY_POSTS.size - 1)]
            val dummy = DynamicObject(id = WarriorsGuildAnimator.DUMMIES.random(), type = 10, rot = rot, tile = tile)
            dummyHitBy.clear()
            dummyUp = dummy
            world.spawn(dummy)
            wait(WarriorsGuildAnimator.DUMMY_UP_TICKS)
            world.remove(dummy)
            if (dummyUp === dummy) dummyUp = null
        }
    }
}

WarriorsGuildAnimator.DUMMIES.forEach { id ->
    on_obj_option(obj = id, option = "hit") {
        val obj = player.getInteractingGameObj()
        if (dummyUp == null || !obj.tile.sameAs(dummyUp!!.tile)) return@on_obj_option
        if (player.username in dummyHitBy) {
            player.message(WarriorsGuildAnimator.MSG_DUMMY_AGAIN)
            return@on_obj_option
        }
        player.faceTile(obj.tile)
        player.animate(gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(player))
        val right =
            WarriorsGuildAnimator.rightStyle(
                obj.id,
                gg.rsmod.plugins.content.combat.CombatConfigs.getXpMode(player),
                gg.rsmod.plugins.content.combat.CombatConfigs.getCombatStyle(player),
            )
        if (!right) {
            player.message(WarriorsGuildAnimator.MSG_DUMMY_WRONG)
            return@on_obj_option
        }
        dummyHitBy += player.username
        player.addXp(Skills.ATTACK, WarriorsGuildAnimator.DUMMY_XP)
        player.inventory.add(Items.WARRIOR_GUILD_TOKEN, WarriorsGuildAnimator.DUMMY_TOKENS)
        player.message(WarriorsGuildAnimator.MSG_DUMMY_HIT)
    }
}

// ---- K06 N372 part 4: shot put (WarriorsGuildAnimator.shotX ... OSRS Wiki "Calculator:Shot put") ----
listOf(WarriorsGuildAnimator.SHOT_18LB, WarriorsGuildAnimator.SHOT_22LB).forEach { shot ->
    on_obj_option(obj = shot, option = "throw") {
        if (player.getEquipment(EquipmentType.WEAPON) != null || player.getEquipment(EquipmentType.SHIELD) != null) {
            player.message(WarriorsGuildAnimator.MSG_SHOT_HANDS)
            return@on_obj_option
        }
        val weight = WarriorsGuildAnimator.shotWeight(shot)
        player.queue {
            player.animate(WarriorsGuildAnimator.PUT_ANIM)
            val choice = options(*WarriorsGuildAnimator.Throw.values().map { it.label }.toTypedArray(), title = "Select a throw")
            val style = WarriorsGuildAnimator.Throw.values().getOrNull(choice - 1) ?: return@queue
            player.lock = LockState.DELAY_ACTIONS
            player.message(WarriorsGuildAnimator.MSG_SHOT_BREATH)
            player.animate(style.anim)
            val x = WarriorsGuildAnimator.shotX(player.skills.getCurrentLevel(Skills.STRENGTH), player.runEnergy.toInt(), style, weight)
            player.runEnergy = (player.runEnergy - WarriorsGuildAnimator.shotEnergy(x)).coerceAtLeast(0.0)
            player.sendRunEnergy(player.runEnergy.toInt())
            wait(3)
            if (world.randomDouble() >= WarriorsGuildAnimator.shotSuccess(x)) {
                player.message(WarriorsGuildAnimator.MSG_SHOT_FUMBLE)
                player.hit(1)
                player.lock = LockState.NONE
                return@queue
            }
            val distance = WarriorsGuildAnimator.shotDistance(x)
            wait(distance / 2 + 1)
            player.addXp(Skills.STRENGTH, WarriorsGuildAnimator.shotXp(x))
            val tokens = WarriorsGuildAnimator.shotTokens(weight, distance)
            player.inventory.add(Items.WARRIOR_GUILD_TOKEN, tokens)
            player.message("You throw the shot $distance yard${if (distance == 1) "" else "s"} and earn $tokens token${if (tokens == 1) "" else "s"}.")
            player.lock = LockState.NONE
        }
    }
}

// ---- K06 N372 part 5: keg balance (WarriorsGuildAnimator.KEG_VARBITS ... OSRS Wiki "Keg") ----
val KEGS_ON_HEAD = gg.rsmod.game.model.attr.AttributeKey<Int>()
val KEG_TICKS = gg.rsmod.game.model.attr.AttributeKey<Int>()
val KEG_TOKEN_PROGRESS = gg.rsmod.game.model.attr.AttributeKey<Int>()
val KEG_TIMER = gg.rsmod.game.model.timer.TimerKey()

/** Takes every keg off the player (head slot or, after an unequip, the inventory) and puts the kegs back on the table. */
fun resetKegs(player: Player) {
    val head = player.equipment[EquipmentType.HEAD.id]
    if (head != null && head.id in WarriorsGuildAnimator.BARRELS) player.equipment[EquipmentType.HEAD.id] = null
    WarriorsGuildAnimator.BARRELS.forEach { barrel ->
        val held = player.inventory.getItemCount(barrel)
        if (held > 0) player.inventory.remove(barrel, held)
    }
    if ((player.attr[KEGS_ON_HEAD] ?: 0) > 0) player.resetRenderAnimation()
    WarriorsGuildAnimator.KEG_VARBITS.values.forEach { player.setVarbit(it, 0) }
    player.attr.remove(KEGS_ON_HEAD)
    player.attr.remove(KEG_TICKS)
    player.attr.remove(KEG_TOKEN_PROGRESS)
    player.timers.remove(KEG_TIMER)
}

fun dropKegs(player: Player) {
    val kegs = player.attr[KEGS_ON_HEAD] ?: 0
    resetKegs(player)
    player.graphic(WarriorsGuildAnimator.KEG_FALL_GFX - kegs)
    player.message(WarriorsGuildAnimator.MSG_KEG_FALL)
    player.forceChat("Ouch!")
    player.hit(1 + world.random(WarriorsGuildAnimator.KEG_MAX_HIT - 1))
}

on_obj_option(obj = WarriorsGuildAnimator.KEG, option = "pick-up") {
    val obj = player.getInteractingGameObj()
    val varbit = WarriorsGuildAnimator.KEG_VARBITS[obj.id] ?: return@on_obj_option
    if (player.getVarbit(varbit) != 0) return@on_obj_option
    val kegs = player.attr[KEGS_ON_HEAD] ?: 0
    if (kegs >= WarriorsGuildAnimator.MAX_KEGS) {
        player.message(WarriorsGuildAnimator.MSG_KEG_MAX)
        return@on_obj_option
    }
    val head = player.equipment[EquipmentType.HEAD.id]
    val headFree = if (kegs == 0) head == null else head?.id in WarriorsGuildAnimator.BARRELS
    if (!headFree || listOf(EquipmentType.WEAPON, EquipmentType.SHIELD, EquipmentType.GLOVES).any { player.equipment[it.id] != null }) {
        player.message(WarriorsGuildAnimator.MSG_KEG_FREE)
        return@on_obj_option
    }
    player.queue {
        player.lock = LockState.DELAY_ACTIONS
        player.faceTile(obj.tile)
        player.animate(WarriorsGuildAnimator.KEG_PICK_ANIM)
        wait(2)
        player.lock = LockState.NONE
        if ((player.attr[KEGS_ON_HEAD] ?: 0) != kegs || player.getVarbit(varbit) != 0) return@queue
        val now = kegs + 1
        player.setVarbit(varbit, 1)
        player.attr[KEGS_ON_HEAD] = now
        player.equipment[EquipmentType.HEAD.id] = gg.rsmod.game.model.item.Item(WarriorsGuildAnimator.barrel(now))
        player.setRenderAnimation(WarriorsGuildAnimator.KEG_RENDER)
        player.runEnergy = (player.runEnergy - WarriorsGuildAnimator.KEG_PICK_ENERGY).coerceAtLeast(0.0)
        player.sendRunEnergy(player.runEnergy.toInt())
        if (kegs == 0) player.timers[KEG_TIMER] = 1
    }
}

on_timer(KEG_TIMER) {
    val kegs = player.attr[KEGS_ON_HEAD] ?: 0
    if (kegs == 0) return@on_timer
    if (!WarriorsGuildAnimator.inKegRoom(player.tile) || player.equipment[EquipmentType.HEAD.id]?.id != WarriorsGuildAnimator.barrel(kegs)) {
        resetKegs(player)
        return@on_timer
    }
    if (player.isRunning() && player.hasMoveDestination()) {
        player.message(WarriorsGuildAnimator.MSG_KEG_RUN)
        dropKegs(player)
        return@on_timer
    }
    player.runEnergy = (player.runEnergy - WarriorsGuildAnimator.kegDrain(kegs)).coerceAtLeast(0.0)
    player.sendRunEnergy(player.runEnergy.toInt())
    if (world.randomDouble() < WarriorsGuildAnimator.kegFallChance(player.runEnergy)) {
        dropKegs(player)
        return@on_timer
    }
    val ticks = (player.attr[KEG_TICKS] ?: 0) + 1
    player.attr[KEG_TICKS] = ticks
    if (ticks % WarriorsGuildAnimator.KEG_REWARD_EVERY == 0) {
        player.addXp(Skills.STRENGTH, WarriorsGuildAnimator.kegXp(kegs))
        val progress = (player.attr[KEG_TOKEN_PROGRESS] ?: 0) + kegs
        player.attr[KEG_TOKEN_PROGRESS] = progress % WarriorsGuildAnimator.KEG_TOKEN_PROGRESS
        val tokens = progress / WarriorsGuildAnimator.KEG_TOKEN_PROGRESS
        if (tokens > 0) player.inventory.add(Items.WARRIOR_GUILD_TOKEN, tokens)
    }
    player.timers[KEG_TIMER] = 1
}

WarriorsGuildAnimator.BARRELS.forEach { barrel ->
    on_item_unequip(item = barrel) { resetKegs(player) }
}

on_logout { resetKegs(player) }

on_login {
    val head = player.equipment[EquipmentType.HEAD.id]
    if (head?.id in WarriorsGuildAnimator.BARRELS || WarriorsGuildAnimator.BARRELS.any { player.inventory.getItemCount(it) > 0 }) resetKegs(player)
}

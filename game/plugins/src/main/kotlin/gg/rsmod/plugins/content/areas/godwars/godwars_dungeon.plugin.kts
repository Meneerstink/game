package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.sendRunEnergy
import gg.rsmod.plugins.content.areas.godwars.GodWars.God
import gg.rsmod.plugins.content.combat.CombatConfigs

/**
 * God Wars Dungeon (2011): overlay, kill counts, faction aggression, altars, boss chamber doors,
 * the Ancient Prison door and every agility/strength shortcut inside the dungeon.
 *
 * Sources: Void donor (GodwarsDoors, GodwarsAggression, GodwarsAltars, BandosDoor, ZamorakBridge,
 * SaradominRock, GodwarsBoulder, ArmadylPillar and the god_wars data files) and the Novite donor
 * GodWars controller (Zaros wing object ids and kill count varbit).
 */
val OVERLAY_INTERFACE = 601
val KILLCOUNT_REQUIRED = 40
val GWD_REGIONS = intArrayOf(11346, 11347, 11601, 11602, 11603)
val GWD_CHILL_REGIONS = intArrayOf(11322, 11323, 11578, 11579)
val GWD_CHILL_TIMER = TimerKey()

val ROPE_ENTRANCE_VARBIT = 3932
val SARADOMIN_ROPE_TOP_VARBIT = 3933
val SARADOMIN_ROPE_BOTTOM_VARBIT = 3934

val ANIM_CLIMB_DOWN = 827
val ANIM_CLIMB_UP = 828
val ANIM_HAMMER_BANG = 7002
val ANIM_ARMADYL_PILLAR_SWING = 6067
val ANIM_MOVE_BOULDER_NORTH = 6978
val ANIM_MOVE_BOULDER_SOUTH = 6979
val ANIM_HUMAN_CRAWL = 7023
val GFX_BIG_SPLASH = 68
val ANIM_PRAYER_ALTAR = 645

/* ------------------------------------------------------------------------------------------
 * Overlay + kill counts
 * ---------------------------------------------------------------------------------------- */

fun openOverlay(player: Player) {
    GodWars.refreshKillCounts(player)
    GodWars.refreshProtection(player)
    player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = OVERLAY_INTERFACE)
}

GWD_REGIONS.forEach { region ->
    on_enter_region(region) {
        if (GodWars.inDungeon(player.tile)) {
            openOverlay(player)
        }
    }
    on_exit_region(region) {
        if (!GodWars.inDungeon(player.tile)) {
            player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
            GodWars.resetKillCounts(player)
        }
    }
}

on_login {
    if (GodWars.inDungeon(player.tile)) {
        openOverlay(player)
    }
    if (player.tile.regionId in GWD_CHILL_REGIONS) {
        player.timers[GWD_CHILL_TIMER] = 1
    }
}

GWD_CHILL_REGIONS.forEach { regionId ->
    on_enter_region(regionId) { player.timers[GWD_CHILL_TIMER] = 1 }
    on_exit_region(regionId) { player.timers.remove(GWD_CHILL_TIMER) }
}

/** Void's 2011 Wind Chill: drain every ten ticks while in the sourced polygon. */
on_timer(GWD_CHILL_TIMER) {
    if (GodWars.inGodWarsChillArea(player.tile)) {
        player.playSound(Sfx.WINDY)
        player.runEnergy = 0.0
        player.sendRunEnergy(0)
        for (skill in 0..Skills.DUNGEONEERING) {
            if (skill == Skills.CONSTITUTION) {
                if (player.skills.getCurrentLevel(Skills.CONSTITUTION) > 10) {
                    player.hit(damage = 10, type = HitType.REGULAR_HIT)
                }
            } else {
                val level = player.skills.getCurrentLevel(skill)
                player.skills.setCurrentLevel(skill, (level - 1).coerceAtLeast(0))
            }
        }
    }
    if (player.tile.regionId in GWD_CHILL_REGIONS) {
        player.timers[GWD_CHILL_TIMER] = 10
    }
}

on_npc_killed { killer, npc ->
    val god = God.forNpc(npc) ?: return@on_npc_killed
    if (GodWars.inDungeon(npc.tile) || god == God.ZAROS) {
        GodWars.incrementKillCount(killer, god)
    }
}

/* ------------------------------------------------------------------------------------------
 * Faction protection recomputed whenever equipment changes.
 * ---------------------------------------------------------------------------------------- */

EquipmentType.values.forEach { slot ->
    on_equip_to_slot(slot.id) { GodWars.refreshProtection(player) }
    on_unequip_from_slot(slot.id) { GodWars.refreshProtection(player) }
}

/**
 * Aviansies (and Kree'arra's whole flock) fly too high to be hit with melee.
 */
can_attack { attacker, target ->
    if (attacker is Player && target is Npc && GodWars.isFlyingArmadylNpc(target.id)) {
        if (CombatConfigs.getCombatClass(attacker) == CombatClass.MELEE) {
            attacker.message("The Aviansie is flying too high for you to attack using melee.")
            return@can_attack false
        }
    }
    true
}

/* ------------------------------------------------------------------------------------------
 * Boss chamber doors: 40 kills of the faction to enter, free to leave.
 * ---------------------------------------------------------------------------------------- */

God.values().filter { it.doorId != -1 && it != God.ZAROS }.forEach { god ->
    on_obj_option(obj = god.doorId, option = "open") {
        val obj = player.getInteractingGameObj()
        if (god.inChamber(player.tile)) {
            walkThroughDoor(player, obj.tile, god.chamberExit)
            return@on_obj_option
        }
        val kc = GodWars.getKillCount(player, god)
        if (kc < KILLCOUNT_REQUIRED) {
            player.message("You don't have enough kills to enter the lair of the gods.")
            return@on_obj_option
        }
        GodWars.setKillCount(player, god, kc - KILLCOUNT_REQUIRED)
        walkThroughDoor(player, obj.tile, god.chamberEntry)
    }
}

fun walkThroughDoor(player: Player, doorTile: Tile, destination: Tile) {
    player.queue(TaskPriority.STRONG) {
        player.lock = LockState.FULL
        player.walkTo(this, doorTile, detectCollision = false)
        wait(1)
        player.moveTo(destination)
        player.lock = LockState.NONE
    }
}

/* ------------------------------------------------------------------------------------------
 * Altars: Pray recharges prayer once every ten minutes; Teleport leaves the chamber.
 * ---------------------------------------------------------------------------------------- */

God.values().filter { it.altarId != -1 }.forEach { god ->
    on_obj_option(obj = god.altarId, option = "Pray-at") {
        val last = player.attr[GodWars.ALTAR_RECHARGE]
        if (!GodWars.canRechargeAltar(world.currentCycle, last)) {
            player.message("The gods blessed you with their power not long ago. You must wait before they will do so again.")
            return@on_obj_option
        }
        if (player.timers.has(ACTIVE_COMBAT_TIMER)) {
            player.message("You cannot recharge your prayer while under attack.")
            return@on_obj_option
        }
        player.attr[GodWars.ALTAR_RECHARGE] = world.currentCycle
        player.queue {
            player.animate(ANIM_PRAYER_ALTAR)
            player.restorePrayer(player.skills.getMaxLevel(Skills.PRAYER))
            player.message("You recharge your prayer points.")
        }
    }
    on_obj_option(obj = god.altarId, option = "teleport") {
        player.queue {
            player.lock()
            player.message("The gods pity you and allow you to leave their encampment.")
            wait(1)
            player.moveTo(god.chamberExit)
            player.unlock()
        }
    }
}

/* ------------------------------------------------------------------------------------------
 * Entrance: rope on the hole outside, climb down; rope at the bottom to leave.
 * ---------------------------------------------------------------------------------------- */

on_item_on_obj(obj = Objs.HOLE_26340, item = Items.ROPE) {
    if (player.getVarbit(ROPE_ENTRANCE_VARBIT) == 1) {
        player.message("There is already a rope attached to the hole.")
        return@on_item_on_obj
    }
    if (player.inventory.remove(Items.ROPE).hasSucceeded()) {
        player.setVarbit(ROPE_ENTRANCE_VARBIT, 1)
        player.message("You tie the rope to a rock and lower it into the hole.")
    }
}

on_obj_option(obj = Objs.HOLE_26340, option = "Tie-rope") {
    if (player.getVarbit(ROPE_ENTRANCE_VARBIT) != 1) {
        player.message("I'll need a rope to climb down there.")
        return@on_obj_option
    }
    descendIntoDungeon(player)
}

// Real cache option confirmed via `ObjectDefProbeTool 26341` -> OBJECT_26341 name='Hole'
// options=[1:'Climb-down'] (option matching is case-insensitive, so the original "climb-down"
// would have matched too - the TODO predates this session's probe tooling, not a real mismatch).
on_obj_option(obj = Objs.HOLE_26341, option = "Climb-down") {
    descendIntoDungeon(player)
}

fun descendIntoDungeon(player: Player) {
    player.queue {
        player.lock()
        player.animate(ANIM_CLIMB_DOWN)
        wait(2)
        player.moveTo(Tile(2881, 5310, 2))
        player.unlock()
    }
}

on_obj_option(obj = Objs.ROPE_26293, option = "climb") {
    player.queue {
        player.lock()
        player.animate(ANIM_CLIMB_UP)
        wait(2)
        player.moveTo(Tile(2916, 3747, 0))
        player.unlock()
    }
}

/* ------------------------------------------------------------------------------------------
 * Boulder (60 Strength, 60 Agility) and the little crack (60 Agility) on the surface route.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.BOULDER_26338, option = "move") {
    val obj = player.getInteractingGameObj()
    if (player.skills.getCurrentLevel(Skills.STRENGTH) < 60) {
        player.message("You need a Strength level of 60 to move this boulder.")
        return@on_obj_option
    }
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 60) {
        player.message("You need an Agility level of 60 to climb past this boulder.")
        return@on_obj_option
    }
    val north = player.tile.z > obj.tile.z
    val start = Tile(obj.tile.x, obj.tile.z + if (north) 3 else -1)
    val end = Tile(obj.tile.x, obj.tile.z + if (north) -1 else 3)
    player.queue {
        player.walkTo(this, start)
        wait(2)
        player.lock()
        player.faceTile(obj.tile)
        player.animate(if (north) ANIM_MOVE_BOULDER_SOUTH else ANIM_MOVE_BOULDER_NORTH)
        wait(3)
        player.moveTo(end)
        player.unlock()
    }
}

on_obj_option(obj = Objs.LITTLE_CRACK, option = "crawl-through") {
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 60) {
        player.message("You need an Agility level of 60 to squeeze through this crack.")
        return@on_obj_option
    }
    val obj = player.getInteractingGameObj()
    val destination = if (obj.tile == Tile(2900, 3713)) Tile(2904, 3720) else Tile(2899, 3713)
    player.queue {
        player.lock()
        player.animate(ANIM_HUMAN_CRAWL)
        wait(2)
        player.moveTo(destination)
        player.unlock()
    }
}

/* ------------------------------------------------------------------------------------------
 * Bandos big door: 70 Strength and a hammer to ring the gong.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.BIG_DOOR_26384, option = "bang") {
    val obj = player.getInteractingGameObj()
    val insideBandos = player.tile.x >= obj.tile.x
    if (!insideBandos) {
        if (player.skills.getCurrentLevel(Skills.STRENGTH) < 70) {
            player.message("You need a Strength level of 70 to ring the gong.")
            return@on_obj_option
        }
        if (!player.inventory.contains(Items.HAMMER)) {
            player.message("You need a suitable hammer to ring the gong.")
            return@on_obj_option
        }
    }
    val destination = Tile(if (insideBandos) 2850 else 2851, 5334, 2)
    player.queue {
        player.lock()
        if (!insideBandos) {
            player.animate(ANIM_HAMMER_BANG)
            wait(3)
        }
        player.moveTo(destination)
        player.unlock()
    }
}

/* ------------------------------------------------------------------------------------------
 * Zamorak river: 70 Constitution, swimming into the evil side drains all prayer.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.ICE_BRIDGE, option = "climb-off") {
    val obj = player.getInteractingGameObj()
    if (player.skills.getCurrentLevel(Skills.CONSTITUTION) < 70) {
        player.message("You need a Constitution level of 70 to survive the river's freezing water.")
        return@on_obj_option
    }
    val north = player.tile.z <= obj.tile.z
    val step = if (north) 1 else -1
    player.queue {
        player.walkTo(this, obj.tile, detectCollision = false)
        wait(1)
        player.lock()
        player.moveTo(Tile(obj.tile.x, obj.tile.z + step * 2, obj.tile.height))
        world.spawn(TileGraphic(player.tile, GFX_BIG_SPLASH, 0))
        wait(4)
        player.message("Dripping, you climb out of the water.")
        if (north) {
            player.skills.setCurrentLevel(Skills.PRAYER, 0)
            player.message("The extreme evil of this area leaves your Prayer drained.")
        }
        player.moveTo(Tile(obj.tile.x, obj.tile.z + step * 12, obj.tile.height))
        player.unlock()
    }
}

/* ------------------------------------------------------------------------------------------
 * Saradomin rocks: tie a rope (70 Agility), then climb the ropes on both ledges.
 * ---------------------------------------------------------------------------------------- */

data class RopeLedge(val rockId: Int, val ropeId: Int, val varbit: Int, val top: Tile, val bottom: Tile)

val SARADOMIN_LEDGES = listOf(
    RopeLedge(Objs.ROCK_26296, Objs.ROCK_26295, SARADOMIN_ROPE_TOP_VARBIT, Tile(2912, 5300, 2), Tile(2915, 5300, 1)),
    RopeLedge(Objs.ROCK_26300, Objs.ROCK_26299, SARADOMIN_ROPE_BOTTOM_VARBIT, Tile(2920, 5276, 1), Tile(2919, 5274, 0)),
)

fun tieSaradominRope(player: Player, ledge: RopeLedge) {
    if (player.getVarbit(ledge.varbit) == 1) {
        player.message("There is already a rope tied to this rock.")
        return
    }
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 70) {
        player.message("You need an Agility level of 70 to climb down these rocks.")
        return
    }
    if (!player.inventory.remove(Items.ROPE).hasSucceeded()) {
        player.message("You need a rope to tie to the rock.")
        return
    }
    player.setVarbit(ledge.varbit, 1)
    player.message("You tie the rope securely to the rock.")
}

fun climbSaradominRope(player: Player, ledge: RopeLedge) {
    if (player.getVarbit(ledge.varbit) != 1) {
        player.message("You need to tie a rope to the rock before you can climb down.")
        return
    }
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 70) {
        player.message("You need an Agility level of 70 to climb these ropes.")
        return
    }
    val goingDown = player.tile.height >= ledge.top.height && player.tile.getDistance(ledge.top) <= player.tile.getDistance(ledge.bottom)
    player.queue {
        player.lock()
        player.animate(if (goingDown) ANIM_CLIMB_DOWN else ANIM_CLIMB_UP)
        wait(2)
        player.moveTo(if (goingDown) ledge.bottom else ledge.top)
        player.unlock()
    }
}

SARADOMIN_LEDGES.forEach { ledge ->
    on_obj_option(obj = ledge.rockId, option = "tie-rope") { tieSaradominRope(player, ledge) }
    on_item_on_obj(obj = ledge.rockId, item = Items.ROPE) { tieSaradominRope(player, ledge) }
    // Real cache options confirmed via `ObjectDefProbeTool 26295 26296 26299 26300`: the rock
    // (ledge.rockId, 26296/26300) only carries "Tie-rope"; the climb option ("Climb-down") lives
    // on the tied rope itself (ledge.ropeId, 26295/26299), matching the already-working
    // ROPE_26297 handler below.
    on_obj_option(obj = ledge.ropeId, option = "Climb-down") { climbSaradominRope(player, ledge) }
}

on_obj_option(obj = Objs.ROPE_26297, option = "Climb-up") {
    val obj = player.getInteractingGameObj()
    val ledge = SARADOMIN_LEDGES.minByOrNull { minOf(obj.tile.getDistance(it.top), obj.tile.getDistance(it.bottom)) }!!
    climbSaradominRope(player, ledge)
}

/* ------------------------------------------------------------------------------------------
 * Armadyl pillar: 70 Ranged, crossbow and mithril grapple to swing across the chasm.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.PILLAR_26303, option = "grapple") {
    val obj = player.getInteractingGameObj()
    if (player.skills.getCurrentLevel(Skills.RANGED) < 70) {
        player.message("You need a Ranged level of 70 to fire a grapple across the chasm.")
        return@on_obj_option
    }
    if (!player.hasWeaponType(WeaponType.CROSSBOW)) {
        player.message("You need a crossbow equipped to fire a grapple.")
        return@on_obj_option
    }
    if (!player.hasEquipped(EquipmentType.AMMO, Items.MITH_GRAPPLE)) {
        player.message("You need a mithril grapple equipped to fire at the pillar.")
        return@on_obj_option
    }
    val middle = Tile(2872, 5274, 2)
    val south = player.tile.z > obj.tile.z
    val offset = if (south) 5 else -5
    player.queue {
        player.walkTo(this, middle.transform(0, offset))
        wait(1)
        player.lock()
        player.faceTile(middle)
        player.filterableMessage("You fire your grapple at the pillar...")
        player.animate(ANIM_ARMADYL_PILLAR_SWING)
        wait(4)
        player.moveTo(middle)
        wait(1)
        player.moveTo(middle.transform(0, -offset))
        player.filterableMessage("...and swing safely to the other side.")
        player.unlock()
    }
}

on_obj_option(obj = Objs.CRATE_26440, option = "search") {
    val hasCrossbow = player.hasWeaponType(WeaponType.CROSSBOW) || player.inventory.contains(Items.BRONZE_CROSSBOW) || player.inventory.contains(Items.CROSSBOW)
    val hasGrapple = player.inventory.contains(Items.MITH_GRAPPLE) || player.hasEquipped(EquipmentType.AMMO, Items.MITH_GRAPPLE)
    if (hasCrossbow && hasGrapple) {
        player.message("You search the crate but find nothing of use.")
        return@on_obj_option
    }
    if (player.inventory.freeSlotCount < 2) {
        player.message("You need more inventory space to take anything from the crate.")
        return@on_obj_option
    }
    if (!hasCrossbow) player.inventory.add(Items.BRONZE_CROSSBOW)
    if (!hasGrapple) player.inventory.add(Items.MITH_GRAPPLE)
    player.message("You find a crossbow and a grapple in the crate.")
}

/* ------------------------------------------------------------------------------------------
 * Ancient Prison (Zaros wing): stairs and the 40 kill door. The landslide (Nex arena entrance)
 * lives in nex/nex_arena.plugin.kts.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.HOLE_57256, option = "climb-down") {
    player.queue {
        player.lock()
        player.animate(ANIM_CLIMB_UP)
        wait(2)
        player.moveTo(Tile(2855, 5222, 0))
        player.unlock()
    }
}

on_obj_option(obj = Objs.ROPE_57260, option = "climb") {
    player.queue {
        player.lock()
        player.animate(ANIM_CLIMB_UP)
        wait(2)
        player.moveTo(Tile(2887, 5276, 0))
        player.unlock()
    }
}

on_obj_option(obj = Objs.BIG_DOOR_57258, option = "open") {
    val obj = player.getInteractingGameObj()
    val insidePrison = player.tile.x >= 2900
    if (!insidePrison) {
        val hasCeremonial = GodWars.hasFullAncientCeremonial(player)
        val kc = GodWars.getKillCount(player, God.ZAROS)
        if (kc < KILLCOUNT_REQUIRED && !hasCeremonial) {
            player.message("You don't have enough kills to enter the lair of Zaros.")
            return@on_obj_option
        }
        if (hasCeremonial) {
            player.message("The door recognises your familiarity with the area and allows you to pass through.")
        } else {
            GodWars.setKillCount(player, God.ZAROS, kc - KILLCOUNT_REQUIRED)
        }
    }
    walkThroughDoor(player, obj.tile, if (insidePrison) God.ZAROS.chamberExit else God.ZAROS.chamberEntry)
}

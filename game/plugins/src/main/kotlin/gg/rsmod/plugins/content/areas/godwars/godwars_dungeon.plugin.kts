package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.LockState
import gg.rsmod.plugins.content.skills.agility.*
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
// Void god_wars_dungeon *.toml: godwars_grapple_shoot gfx, godwars_grapple_swing / _reverse (loc 26302), crossbow_grappling / land_flatter.
val GFX_GODWARS_GRAPPLE_SHOOT = 1036
val ANIM_GRAPPLE_SWING = 6070
val ANIM_GRAPPLE_SWING_REVERSE = 6072
val SFX_CROSSBOW_GRAPPLING = 2928
val SFX_LAND_FLATTER = 2455
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
            player.message(FrozenKey.MSG_KILLCOUNT_DRAINED)
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
    on_exit_region(regionId) {
        if (player.tile.regionId in GWD_CHILL_REGIONS) return@on_exit_region
        player.timers.remove(GWD_CHILL_TIMER)
        // Void WindChill exited("godwars_chill_area"): leaving with his notes, Sir Gerry has died (varbit 3936 hides npc 6202).
        if (player.inventory.contains(Items.KNIGHTS_NOTES) || player.inventory.contains(Items.KNIGHTS_NOTES_11735)) {
            player.setVarbit(Varbits.VARBIT_3936, 1)
        }
    }
}

/** Void's 2011 Wind Chill: drain every ten ticks while in the sourced polygon. */
on_timer(GWD_CHILL_TIMER) {
    if (GodWars.inGodWarsChillArea(player.tile)) {
        player.playSound(Sfx.WINDY)
        player.runEnergy = 0.0
        player.sendRunEnergy(0)
        for (skill in 0..Skills.DUNGEONEERING) {
            if (skill == Skills.CONSTITUTION) {
                // Void WindChill: "if life points > 10, directHit(10)" in x10 life points - 1 hitpoint above 1 (ROOT_CAUSES #72;
                // it took 10 every ten ticks).
                if (player.skills.getCurrentLevel(Skills.CONSTITUTION) > 1) {
                    player.hit(damage = 1, type = HitType.REGULAR_HIT)
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
            if (attacker.world.plugins.notifyAttackRefusal) {
                attacker.message("The Aviansie is flying too high for you to attack using melee.")
            }
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
            // Owner decision RCV-005 2026-09-13: the chamber door is entered once and cannot be used
            // to leave; the altar's Teleport option is the way out. (The old exit tiles also put the
            // player inside a wall.) Message text is provisional - no 667 source found yet.
            player.message("The door won't open from this side.")
            return@on_obj_option
        }
        val kc = GodWars.getKillCount(player, god)
        if (kc < KILLCOUNT_REQUIRED) {
            player.message("You don't have enough kills to enter the lair of the gods.")
            return@on_obj_option
        }
        // Void GodwarsDoors `enterDoor`: up to the door with collision, the door swings open (big_door_*_opened 26851, 3 ticks), the
        // player walks through the doorway. The old route walked onto the shut door's tile through its model and then teleported
        // to a fixed chamber tile (audit 2026-10-04 section 1). The kill count is paid once the player is through.
        player.lockingQueue(lockState = LockState.FULL) {
            val crossed =
                gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
                    this, player, obj,
                    open = { gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.swingOpenFor(player, obj, GodWars.CHAMBER_DOOR_OPENED, ticks = 3) },
                )
            if (crossed) GodWars.setKillCount(player, god, GodWars.getKillCount(player, god) - KILLCOUNT_REQUIRED)
        }
    }
}

/* ------------------------------------------------------------------------------------------
 * Altars: Pray recharges prayer once every ten minutes; Teleport leaves the chamber.
 * ---------------------------------------------------------------------------------------- */

God.values().filter { it.altarId != -1 }.forEach { god ->
    on_obj_option(obj = god.altarId, option = "Pray-at") {
        // RCV-011 Q-043-b: Void GodwarsAltars.kt order and texts, persisted 10-minute clock (see GodWars).
        val refusal =
            GodWars.altarRefusal(
                prayerFull = player.getCurrentPrayerPoints() >= player.getMaximumPrayerPoints(),
                recharging = player.timers.has(GodWars.ALTAR_RECHARGE_TIMER),
                underAttack = player.timers.has(ACTIVE_COMBAT_TIMER),
            )
        if (refusal != null) {
            player.message(refusal)
            return@on_obj_option
        }
        val worn = (0 until player.equipment.capacity).mapNotNull { player.equipment[it]?.getName(world.definitions)?.lowercase() }
        val bonus = GodWars.altarBonus(god, worn)
        player.timers[GodWars.ALTAR_RECHARGE_TIMER] = GodWars.ALTAR_RECHARGE_TICKS
        player.queue {
            player.animate(ANIM_PRAYER_ALTAR)
            // Owner 2026-09-30: the recharge sound with the prayer animation - the same cue every altar here plays
            // (prayer_altar.plugin.kts) and Void pairs with `altar_pray` (Drezel.kt: anim altar_pray + sound prayer_recharge).
            player.playSound(Sfx.PRAYER_RECHARGE)
            val restoreTo = player.getMaximumPrayerPoints() + bonus
            player.alterPrayerPoints(value = (restoreTo - player.getCurrentPrayerPoints()).coerceAtLeast(0), capValue = bonus)
            player.message(GodWars.MSG_ALTAR_DONE)
        }
    }
    // Teleport sound: SOURCE_BLOCKED (2026-09-30) - Void GodwarsAltars (tele), Novite GodWars (useStairs), 2009scape
    // GodwarsMapzone (setTeleportLocation) and the OSRS Wiki give no sound for this exit, so none is invented here.
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

fun ownsKnightsNotes(player: Player): Boolean =
    player.inventory.contains(Items.KNIGHTS_NOTES) || player.inventory.contains(Items.KNIGHTS_NOTES_11735) ||
        player.bank.contains(Items.KNIGHTS_NOTES) || player.bank.contains(Items.KNIGHTS_NOTES_11735)

// Void SirGerry Tie-rope: godwars_knights_notes (varbit 3936) || ownsItem(knights_notes / knights_notes_opened).
fun hasKnightsNotes(player: Player): Boolean = player.getVarbit(Varbits.VARBIT_3936) == 1 || ownsKnightsNotes(player)

// Owner E12 2026-10-03: "there should be an npc which i can talk to then enter the dungeon". Sir Gerry was never spawned, so the
// notes could not be had and the rope always answered "Cough... Hey, over here.". Void god_wars_dungeon.npc-spawns.toml: sir_gerry
// (6202) at 2912,3748 facing south. Cache (NpcDefProbeTool): 6202 transforms by varbit 3936 - 0 = 6201 'Knight' [Talk-to], 1 = none
// (the knight has died, Void WindChill sets it once you leave the area with his notes).
spawn_npc(npc = 6202, x = 2912, z = 3748, walkRadius = 0, direction = Direction.SOUTH)

// Void SirGerry npcOperate("Talk-to", "sir_gerry_*") - same lines, same order.
on_npc_option(npc = Npcs.KNIGHT_6201, option = "Talk-to") {
    if (hasKnightsNotes(player)) {
        player.message("He's still alive, but in no condition to talk. He appears to be almost unconscious.")
        return@on_npc_option
    }
    player.queue {
        chatPlayer("Who are you? What are you doing here in the snow?", facialExpression = FacialExpression.CONFUSED)
        chatNpc(
            "My name is...Sir Gerry. I am...a member of a secret...society of knights. My time is short and I need...your help.",
            facialExpression = FacialExpression.SCARED,
        )
        if (player.getVarbit(Varbits.RECRUITMENT_DRIVE_PROGRESS) >= 2) {
            chatPlayer("A secret society of knights? You don't mean the Temple Knights, do you?", facialExpression = FacialExpression.CONFUSED)
            chatNpc(
                "Yes! Praise Saradomin! You...have been sent in...my hour of need. Please, take...this scroll to Sir Tiffy in Falador park... You should not...read it.",
                facialExpression = FacialExpression.HAPPY,
            )
        } else {
            chatPlayer(
                "A secret society of knights? What a surprise! Is there an old charter or decree that says if you're a knight you have to belong to a secret order?",
                facialExpression = FacialExpression.CONFUSED,
            )
            chatNpc(
                "I'm sorry, my friend... I do not understand your meaning. Please, time is short... Take this scroll to Sir Tiffy. You will find him in Falador park... You should not...read it... It contains information for his eyes only.",
                facialExpression = FacialExpression.SAD,
            )
        }
        if (player.inventory.freeSlotCount < 1) {
            messageBox("You need an empty inventory slot to take the scroll.")
            return@queue
        }
        messageBox("The knight hands you a scroll.")
        player.inventory.add(Items.KNIGHTS_NOTES)
    }
}

// Void's Sir Gerry route: searching the dying knight supplies the notes needed to prepare the
// permanent entrance rope. A full dialogue implementation is not available in this cache build.
on_obj_option(obj = Objs.KNIGHT, option = "search") {
    // OSRS Wiki (God Wars Dungeon): "You don't even need the notes to enter - just speak to the knight before entering the first
    // time." Owner E12 2026-10-03: the rope kept answering "Cough... Hey, over here." because only the notes in the inventory counted
    // and nothing remembered the knight. Remembered now (varbit 3936, saved), whatever happens to the notes.
    player.setVarbit(Varbits.VARBIT_3936, 1)
    if (hasKnightsNotes(player) && (player.inventory.contains(Items.KNIGHTS_NOTES) || player.inventory.contains(Items.KNIGHTS_NOTES_11735) || player.bank.contains(Items.KNIGHTS_NOTES) || player.bank.contains(Items.KNIGHTS_NOTES_11735))) {
        player.message("You find nothing of value on the knight.")
        return@on_obj_option
    }
    if (player.inventory.freeSlotCount < 1) {
        player.message("You need an empty inventory slot to take the knight's notes.")
        return@on_obj_option
    }
    player.inventory.add(Items.KNIGHTS_NOTES)
    player.message("You find some handwritten notes on the knight.")
}

on_item_on_obj(obj = Objs.HOLE_26340, item = Items.ROPE) {
    tieEntranceRope(player)
}

// The production 667 map uses the base hole at (2917,3745,0) (id 26342),
// not the Void child id 26340.  Item-on-object is the real initial action;
// the base definition has no menu option until the varbit transforms it.
on_item_on_obj(obj = 26342, item = Items.ROPE) {
    tieEntranceRope(player)
}

fun tieEntranceRope(player: Player) {
    if (!hasKnightsNotes(player)) {
        player.message("Cough... Hey, over here.")
        return
    }
    if (player.getVarbit(ROPE_ENTRANCE_VARBIT) == 1) {
        player.message("There is already a rope attached to the hole.")
        return
    }
    if (player.inventory.remove(Items.ROPE).hasSucceeded()) {
        player.setVarbit(ROPE_ENTRANCE_VARBIT, 1)
        player.message("You tie the rope to a rock and lower it into the hole.")
    }
}

on_obj_option(obj = Objs.HOLE_26340, option = "Tie-rope") {
    if (!hasKnightsNotes(player)) {
        player.message("Cough... Hey, over here.")
        return@on_obj_option
    }
    if (player.getVarbit(ROPE_ENTRANCE_VARBIT) == 1) {
        descendIntoDungeon(player)
        return@on_obj_option
    }
    // Void SirGerry objectOperate("Tie-rope"): a rope in the inventory is tied on (the old handler never used it and kept
    // answering "I'll need a rope" even with one in the pack).
    if (!player.inventory.contains(Items.ROPE)) {
        player.message("I'll need a rope to climb down there.")
        return@on_obj_option
    }
    tieEntranceRope(player)
}

// Cache placement at the white-wolf entrance: 26345 is the placed rope with
// the actual Climb-down option on the upper plane.
on_obj_option(obj = Objs.ROPE_26345, option = "Climb-down") {
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
        player.moveTo(Tile(2913, 3741, 0))
        player.unlock()
    }
}

/* ------------------------------------------------------------------------------------------
 * Boulder (60 Strength, 60 Agility) and the little crack (60 Agility) on the surface route.
 * ---------------------------------------------------------------------------------------- */

// REVISION_ABSENT (RCV-010 D8): Boulder 26338 is not placed anywhere on the revision-667 map (production and pristine openrs2
// 667 caches, `GodWarsCacheProbeTests`); Void 634 binds it. Binding kept, unreachable in 667 - SOURCE_CONFLICT.
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

// REVISION_ABSENT (RCV-010 D8): Little crack 26305 is not placed anywhere on the revision-667 map (production and pristine openrs2
// 667 caches, `GodWarsCacheProbeTests`); Void 634 binds it. Binding kept, unreachable in 667 - SOURCE_CONFLICT.
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
    }
}

/* ------------------------------------------------------------------------------------------
 * Bandos big door: 70 Strength and a hammer to ring the gong.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.BIG_DOOR_26384, option = "bang") {
    val obj = player.getInteractingGameObj()
    // RCV-011 Q-043-b: the stronghold is west of the door; requirements apply from outside (Void + Novite).
    val outside = GodWars.outsideBandosStronghold(player.tile.x, obj.tile.x)
    if (outside) {
        if (!GodWars.hasUnboostedLevel(player, Skills.STRENGTH, 70)) {
            player.message(GodWars.MSG_BANDOS_STRENGTH)
            return@on_obj_option
        }
        if (!player.inventory.contains(Items.HAMMER)) {
            player.message(GodWars.MSG_BANDOS_HAMMER)
            return@on_obj_option
        }
    }
    // Void BandosDoor: the bang (from outside), then the door is taken away for 2 ticks and the player walks through the doorway
    // (walkOverDelay(doorTarget)). The old script teleported to Novite's 2850/2851,5334 - on this map the walled pockets beside
    // the doorway, so the player stood inside the door frame and could not move (owner 030006 / 025931, 2850,5334,2).
    player.lockingQueue(lockState = LockState.FULL) {
        val near = gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.nearSide(player.tile, obj) ?: return@lockingQueue
        try {
            walkToTile(player, near)
        } catch (e: ObstacleApproachFailed) {
            player.message("I can't reach that!")
            return@lockingQueue
        }
        player.faceTile(obj.tile)
        if (outside) {
            player.animate(ANIM_HAMMER_BANG)
            wait(3)
        }
        gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
            this, player, obj, farSide = GodWars.bandosDoorDestination(outside),
            open = { gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.removeFor(world, obj, ticks = 2) },
        )
    }
}

/* ------------------------------------------------------------------------------------------
 * Zamorak river: 70 Constitution (current life points), swimming into the evil side drains all prayer.
 * ---------------------------------------------------------------------------------------- */

on_obj_option(obj = Objs.ICE_BRIDGE, option = "climb-off") {
    val obj = player.getInteractingGameObj()
    if (!GodWars.canCrossZamorakRiver(player.getCurrentLifepoints())) {
        player.message(GodWars.MSG_ZAMORAK_RIVER)
        return@on_obj_option
    }
    val north = player.tile.z <= obj.tile.z
    player.lockingQueue(lockState = LockState.FULL) {
        // Void ZamorakBridge: walkToDelay(target.tile) - with collision and arrival - before the jump (the old walk was collision-free
        // and unlocked from the click tile).
        try {
            gg.rsmod.plugins.content.skills.agility.walkToTileFrom(this, player, obj.tile)
        } catch (e: gg.rsmod.plugins.content.skills.agility.ObstacleApproachFailed) {
            player.message("I can't reach that!")
            return@lockingQueue
        }
        // Void ZamorakBridge: into the river two tiles past the bridge end, swimming (render "swim"), splash, 4 ticks, out on the far
        // bank 12 tiles on, on the bridge's own plane. The port stood the player on the ice bridge loc itself (no swim) and landed on
        // Novite's plane-0 tiles (audit 2026-10-04 section 1).
        face(player, if (north) Direction.NORTH else Direction.SOUTH)
        wait(1)
        player.moveTo(GodWars.zamorakBridgeSwimTile(obj.tile, north))
        player.setRenderAnimation(Render.SWIM)
        world.spawn(TileGraphic(Tile(player.tile), GFX_BIG_SPLASH, 0))
        wait(4)
        player.message("Dripping, you climb out of the water.")
        if (north) {
            player.skills.setCurrentLevel(Skills.PRAYER, 0)
            player.message("The extreme evil of this area leaves your Prayer drained.")
        }
        player.moveTo(GodWars.zamorakBridgeLanding(obj.tile, north))
        player.resetRenderAnimation()
    }
}

/* ------------------------------------------------------------------------------------------
 * Saradomin rocks: tie a rope (70 Agility), then climb the ropes on both ledges.
 * ---------------------------------------------------------------------------------------- */

// Novite's processObjectClick1 handles the placed bottom-rock base directly. The target cache
// places this base as 26445 without a menu option, so bind the first object-click index explicitly.
on_obj_option(obj = 26445, option = 1) {
    player.moveTo(Tile(2920, 5273, 0))
}

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
    if (!GodWars.hasUnboostedLevel(player, Skills.AGILITY, 70)) {
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
    if (!GodWars.hasUnboostedLevel(player, Skills.AGILITY, 70)) {
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
    if (!GodWars.hasUnboostedLevel(player, Skills.RANGED, 70)) {
        player.message("You need a Ranged level of 70 to fire a grapple across the chasm.")
        return@on_obj_option
    }
    if (!player.hasWeaponType(WeaponType.CROSSBOW)) {
        player.message("You need a crossbow equipped to fire a grapple.")
        return@on_obj_option
    }
    if (!player.hasEquipped(EquipmentType.AMMO, Items.MITH_GRAPPLE_9419)) {
        player.message("You need a mithril grapple equipped to fire at the pillar.")
        return@on_obj_option
    }
    val middle = Tile(2872, 5274, 2)
    val south = player.tile.z > obj.tile.z
    val offset = if (south) 5 else -5
    val direction = if (south) Direction.SOUTH else Direction.NORTH
    // Void ArmadylPillar: walk to the ledge (with arrival), fire, the grapple loc 26302 swings, and the player swings across in two
    // exact moves over the chasm. The port teleported twice (moveTo middle, moveTo far side) without the grapple's animation, and
    // started from wherever the unlocked walk had got to (audit 2026-10-04 section 1).
    player.lockingQueue(lockState = LockState.FULL) {
        try {
            walkToTile(player, middle.transform(0, offset))
        } catch (e: ObstacleApproachFailed) {
            player.message("I can't reach that!")
            return@lockingQueue
        }
        face(player, direction)
        player.filterableMessage("You fire your grapple at the pillar...")
        wait(1)
        player.playSound(SFX_CROSSBOW_GRAPPLING)
        player.animate(ANIM_ARMADYL_PILLAR_SWING, delay = 6)
        player.graphic(GFX_GODWARS_GRAPPLE_SHOOT, delay = 6)
        locAt(world, Tile(2872, 5270, 2), Objs.PILLAR_26302)?.let { animateLoc(world, it, if (south) ANIM_GRAPPLE_SWING_REVERSE else ANIM_GRAPPLE_SWING) }
        wait(3)
        exactMove(player, middle, delay = 29, direction = direction, startDelay = 15)
        exactMove(player, middle.transform(0, -offset), delay = 21, direction = direction)
        player.filterableMessage("...and swing safely to the other side.")
        player.playSound(SFX_LAND_FLATTER)
    }
}

on_obj_option(obj = Objs.CRATE_26440, option = "search") {
    val hasCrossbow = player.hasWeaponType(WeaponType.CROSSBOW) || player.inventory.contains(Items.BRONZE_CROSSBOW) || player.inventory.contains(Items.CROSSBOW)
    val hasGrapple = player.inventory.contains(Items.MITH_GRAPPLE_9419) || player.hasEquipped(EquipmentType.AMMO, Items.MITH_GRAPPLE_9419)
    if (hasCrossbow && hasGrapple) {
        player.message("You search the crate but find nothing of use.")
        return@on_obj_option
    }
    if (player.inventory.freeSlotCount < 2) {
        player.message("You need more inventory space to take anything from the crate.")
        return@on_obj_option
    }
    if (!hasCrossbow) player.inventory.add(Items.BRONZE_CROSSBOW)
    if (!hasGrapple) player.inventory.add(Items.MITH_GRAPPLE_9419)
    player.message("You find a crossbow and a grapple in the crate.")
}

/* ------------------------------------------------------------------------------------------
 * Ancient Prison (Zaros wing): stairs and the 40 kill door. The landslide (Nex arena entrance)
 * lives in nex/nex_arena.plugin.kts.
 * ---------------------------------------------------------------------------------------- */

// Novite's 70 Agility obstacle-pipe shortcut (GodWars.java 57234: anim 1133, a 1-tick forced move, then the tile). Novite's tiles
// 2863 <-> 2860 are one tile east of this map's pipe (clip 2026-10-03: the 2x1 pipe covers 2860-2861,5219, the walkable ends are
// 2859,5219 west and 2862,5219 east): from the east end Novite "travelled east" one tile away from the pipe, from the west it
// landed on the pipe itself. Now the shared lane through the pipe's footprint, from the tile in front to the tile behind it.
on_obj_option(obj = Objs.OBSTACLE_PIPE_57234, option = "Squeeze-through") {
    if (!player.hasAgility(70)) return@on_obj_option
    obstacle { target ->
        val crossing = longAxisCrossing(world, target, player.tile) ?: throw ObstacleApproachFailed("no lane through the pipe", target.tile)
        walkToTile(player, crossing.start)
        face(player, crossing.direction)
        player.animate(1133)
        exactMove(player, crossing.landing, delay = 30, direction = crossing.direction)
    }
}

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

/*
 * RCV-010 D8: the Frozen door. Novite 667 `GodWars.java` handles it under id 75089, which is not a revision-667 object
 * (absent from both the production and the pristine openrs2 667 cache), so the door was never bound. The 667 map places
 * `Frozen door` 57211 (op1 Open) at (2884,5276,2); Novite's semantics are applied to that id: with the Frozen key the
 * player is flung into the Ancient Prison at (2887,5278,0), otherwise the key-hole message. Novite's inside-gate branch
 * (y <= 5278 on its plane-0 object) cannot occur for the plane-2 667 door and is not ported.
 */
on_obj_option(obj = Objs.FROZEN_DOOR, option = "open") {
    // Q-043-e: one charge per opening; a key without charges needs recharging (FrozenKey, 2011 wiki).
    val keySlot = FrozenKey.chargedSlot(player)
    if (keySlot == null) {
        player.queue { messageBox("You try to push the door open, but it wont budge.... It looks like there is some kind of key hole.") }
        return@on_obj_option
    }
    val key = player.inventory[keySlot]!!
    player.inventory[keySlot] = FrozenKey.withCharges(key, FrozenKey.charges(key) - 1)
    player.message("You flash the key in front of the door")
    player.queue {
        player.lock()
        player.animate(1133)
        wait(1)
        player.moveTo(Tile(2887, 5278, 0))
        wait(1)
        player.unlock()
        player.message("...and a strange force flings you in.")
    }
}

on_obj_option(obj = Objs.BIG_DOOR_57258, option = "open") {
    val obj = player.getInteractingGameObj()
    val insidePrison = player.tile.x >= 2900
    val hasCeremonial = GodWars.hasFullAncientCeremonial(player)
    if (!insidePrison) {
        val kc = GodWars.getKillCount(player, God.ZAROS)
        if (kc < KILLCOUNT_REQUIRED && !hasCeremonial) {
            player.message("You don't have enough kills to enter the lair of Zaros.")
            return@on_obj_option
        }
        if (hasCeremonial) player.message("The door recognises your familiarity with the area and allows you to pass through.")
    }
    // Void enterDoor over big_door_11_closed / _opened (57258 / 57257): through the doorway with the door swung open, no teleport.
    player.lockingQueue(lockState = LockState.FULL) {
        val crossed =
            gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.enter(
                this, player, obj,
                open = { gg.rsmod.plugins.content.mechanics.doors.DoorCrossing.swingOpenFor(player, obj, Objs.BIG_DOOR_57257, ticks = 3) },
            )
        if (crossed && !insidePrison && !hasCeremonial) GodWars.setKillCount(player, God.ZAROS, GodWars.getKillCount(player, God.ZAROS) - KILLCOUNT_REQUIRED)
    }
}

package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.getInteractingGameObj
import gg.rsmod.plugins.api.ext.getInteractingNpc
import gg.rsmod.plugins.api.ext.message

/**
 * Castle Wars (2011). Core join/team/round/flag/traversal loop ported from Novite's
 * `CastleWars.java` + `CastleWarsPlaying.java`/`CastleWarsWaiting.java` (see
 * CastleWarsState.kt/CastleWarsHandler.kt for the ported logic; every id here was verified
 * against this project's own 667 cache via `runObjectDefProbeTool`/`runNpcDefProbeTool` before
 * use - none guessed).
 *
 * Known, disclosed gaps (not silently skipped):
 * - Barricade PLACEMENT and bandage SELF-HEAL are both triggered in Novite by a plain inventory
 *   left-click on the item (not an object/npc/item-on-X interaction), which needs the item's real
 *   `ItemDef.inventoryMenu` option text to wire safely (`on_item_option` throws at server boot if
 *   the text is wrong). No probe tool for that field exists yet in this project (only
 *   `runObjectDefProbeTool`/`runNpcDefProbeTool` exist, both object/npc-scoped) - would need a
 *   small new ItemDef-inventoryMenu probe tool (same shape as the existing two) before this can be
 *   wired without guessing. Barricade DESTRUCTION (item-on-npc, no menu-text validation needed) IS
 *   wired below.
 * - The ticket-exchange rewards shop (Novite interface 60, ~48 items) is not ported: its per-item
 *   ticket cost comes from Novite's own clientscript maps 3059/3061, confirmed ABSENT from this
 *   project's real cache via `runConfigDefProbeTool struct 3059 3061` - the donor's own numbering
 *   doesn't carry over, and no alternative source for the real prices was found this batch.
 * - Hint icons (minimap flag-carrier tracking), hood/cape unequip-lock, and barricades physically
 *   blocking movement are all omitted: this engine's `can_unequip_from_slot` hook is a single-slot
 *   map (last registration wins, not additive - confirmed via `practicepvp.plugin.kts` already
 *   claiming every slot), so adding a Castle Wars registration would silently break Practice PvP's
 *   existing temp-gear protection or vice versa depending on plugin load order; and there is no
 *   movement-interception hook in this DSL to block a tile the way Novite's `canMove` did. Both
 *   are real architecture limits, not oversights.
 * - The Saradomin/Zamorak escort-cape "who owns which barricade" attribution isn't tracked (see
 *   `CastleWarsHandler.destroyBarricade`'s comment) - the 10-per-team cap is approximated as one
 *   shared cap across both teams.
 */

val CW_ROUND_TIMER = TimerKey()

on_world_init {
    world.timers[CW_ROUND_TIMER] = CastleWarsRound.TICKS_PER_MINUTE
}

on_timer(CW_ROUND_TIMER) {
    CastleWarsHandler.tickMinute(world)
    world.timers[CW_ROUND_TIMER] = CastleWarsRound.TICKS_PER_MINUTE
}

// Teammates can't attack each other.
can_attack { attacker, target ->
    if (attacker is Player && target is Player) {
        val a = attacker.attr[CW_TEAM]
        val t = target.attr[CW_TEAM]
        if (a != null && t != null && a == t) return@can_attack false
    }
    true
}

// Castle Wars is a safe minigame (OSRS Wiki "Castle Wars": "a safe minigame"; Ferox: "Castle Wars ... they have their own
// magic to keep you safe"). Owner 2026-09-26: register it on the match attribute itself - no map bounding box is needed.
gg.rsmod.plugins.content.mechanics.death.SafeDeath.register { it.attr[CW_PLAYING] == true }

// A carrier who dies drops the flag and respawns at their own base, staying in the match - matches
// Novite's controller-driven respawn.
on_player_death {
    if (player.attr[CW_PLAYING] != true) return@on_player_death
    val team = player.attr[CW_TEAM] ?: return@on_player_death
    val weaponId = player.equipment[EquipmentType.WEAPON.id]?.id
    val carriedFlag = CastleWarsTeam.forFlagWeapon(weaponId ?: -1)
    if (carriedFlag != null) {
        player.equipment[EquipmentType.WEAPON.id] = null
        CastleWarsHandler.dropFlag(player, carriedFlag)
    }
    player.moveTo(team.baseTile)
}

on_logout {
    if (player.attr[CW_PLAYING] == true) {
        CastleWarsHandler.removePlayingPlayer(player)
    } else if (player.attr[CW_TEAM] != null) {
        CastleWarsHandler.removeWaitingPlayer(player)
    }
}

// Portals: Saradomin, Zamorak, balanced (Guthix).
on_obj_option(obj = Objs.SARADOMIN_PORTAL, option = "Enter") { CastleWarsHandler.joinPortal(player, CastleWarsTeam.SARADOMIN) }
on_obj_option(obj = Objs.ZAMORAK_PORTAL, option = "Enter") { CastleWarsHandler.joinPortal(player, CastleWarsTeam.ZAMORAK) }
on_obj_option(obj = Objs.GUTHIX_PORTAL, option = "Enter") { CastleWarsHandler.joinPortal(player, null) }

// Leave portals (waiting room and in-game).
on_obj_option(obj = Objs.EXIT_PORTAL_4389, option = "Exit") { CastleWarsHandler.removeWaitingPlayer(player) }
on_obj_option(obj = Objs.EXIT_PORTAL_4390, option = "Exit") { CastleWarsHandler.removeWaitingPlayer(player) }
on_obj_option(obj = Objs.EXIT_PORTAL_4406, option = "Exit") { CastleWarsHandler.removePlayingPlayer(player) }
on_obj_option(obj = Objs.EXIT_PORTAL_4407, option = "Exit") { CastleWarsHandler.removePlayingPlayer(player) }

// Scoreboard: simplified to a chat message (real interface 55 population not wired this batch).
on_obj_option(obj = Objs.SCOREBOARD_4484, option = "View") {
    player.message("Season wins - Saradomin: ${CastleWarsRound.seasonWins[CastleWarsTeam.SARADOMIN]}, Zamorak: ${CastleWarsRound.seasonWins[CastleWarsTeam.ZAMORAK]}.")
}

// Home-castle flag objects: real flag (steal target for the enemy, score target for the owner)
// and the empty stand left after it's taken (score target for the owner only).
on_obj_option(obj = Objs.SARADOMIN_FLAG_4902, option = "Capture") {
    CastleWarsHandler.homeFlagInteraction(world, player, player.getInteractingGameObj(), CastleWarsTeam.SARADOMIN)
}
on_obj_option(obj = Objs.ZAMORAK_FLAG_4903, option = "Capture") {
    CastleWarsHandler.homeFlagInteraction(world, player, player.getInteractingGameObj(), CastleWarsTeam.ZAMORAK)
}
on_obj_option(obj = Objs.FLAG_STAND, option = "Capture") {
    CastleWarsHandler.homeFlagInteraction(world, player, player.getInteractingGameObj(), CastleWarsTeam.SARADOMIN)
}
on_obj_option(obj = Objs.FLAG_STAND_4378, option = "Capture") {
    CastleWarsHandler.homeFlagInteraction(world, player, player.getInteractingGameObj(), CastleWarsTeam.ZAMORAK)
}

// A carrier who dies or logs out drops the flag; another player of either team can pick it back
// up (own team makes it safe again, enemy team keeps carrying it).
on_obj_option(obj = Objs.SARADOMIN_FLAG_4900, option = "Take") {
    CastleWarsHandler.takeDroppedFlag(world, player, player.getInteractingGameObj(), CastleWarsTeam.SARADOMIN)
}
on_obj_option(obj = Objs.ZAMORAK_FLAG_4901, option = "Take") {
    CastleWarsHandler.takeDroppedFlag(world, player, player.getInteractingGameObj(), CastleWarsTeam.ZAMORAK)
}

// Cave-in trap.
on_obj_option(obj = Objs.CAVE_WALL, option = "Collapse") {
    CastleWarsHandler.collapseCave(world, player.getInteractingGameObj())
}

// Barricade destruction (item-on-npc: no cache option-text validation needed, unlike
// on_item_option, so this is safe to wire without a dedicated probe tool).
on_item_on_npc(item = Items.TINDERBOX_590, npc = Npcs.BARRICADE) {
    val npc = player.getInteractingNpc()
    npc.setTransmogId(Npcs.BARRICADE_1533)
    CastleWarsHandler.destroyBarricade(world, npc)
}
on_item_on_npc(item = Items.EXPLOSIVE_POTION, npc = Npcs.BARRICADE) {
    player.inventory.remove(Items.EXPLOSIVE_POTION, amount = 1)
    CastleWarsHandler.destroyBarricade(world, player.getInteractingNpc())
}

// Castle traversal - ladders, steps, trapdoors, energy barriers, stepping stone, and the
// multi-branch "Staircase"/generic ladder objects that map to different destinations depending on
// which physical copy was clicked. Every (objId, tile) pair and destination tile below is copied
// verbatim from Novite's `CastleWarsPlaying.processObjectClick1`, not guessed.
on_obj_option(obj = Objs.TRAPDOOR_36691, option = "Climb-down") {
    val obj = player.getInteractingGameObj()
    if (obj.tile.z == 9508) player.moveTo(Tile(2400, 3106, 0))
    else if (obj.tile.z == 9499) player.moveTo(Tile(2399, 3100, 0))
}
on_obj_option(obj = 36644, option = "Climb-up") {
    val obj = player.getInteractingGameObj()
    if (obj.tile.z == 3099) player.moveTo(Tile(2399, 9500, 0))
    else if (obj.tile.z == 3108) player.moveTo(Tile(2400, 9507, 0))
}
on_obj_option(obj = 36693, option = "Climb-down") { player.moveTo(Tile(2430, 9483, 0)) }
on_obj_option(obj = 36694, option = "Climb-down") { player.moveTo(Tile(2369, 9524, 0)) }
on_obj_option(obj = 36645, option = "Climb-up") { player.moveTo(Tile(2430, 3081, 0)) }
on_obj_option(obj = 36646, option = "Climb-up") { player.moveTo(Tile(2369, 3126, 0)) }

on_obj_option(obj = 4415, option = "Climb-down") {
    val obj = player.getInteractingGameObj()
    val t = obj.tile
    when {
        t.x == 2417 && t.z == 3075 && t.height == 0 -> player.moveTo(Tile(2417, 3078, 0))
        t.x == 2419 && t.z == 3080 && t.height == 1 -> player.moveTo(Tile(2419, 3077, 0))
        t.x == 2430 && t.z == 3081 && t.height == 2 -> player.moveTo(Tile(2427, 3081, 1))
        t.x == 2425 && t.z == 3074 && t.height == 3 -> player.moveTo(Tile(2425, 3077, 2))
        t.x == 2380 && t.z == 3127 && t.height == 1 -> player.moveTo(Tile(2380, 3130, 0))
        t.x == 2417 && t.z == 3075 && t.height == 1 -> player.moveTo(Tile(2417, 3078, 0))
        t.x == 2382 && t.z == 3132 && t.height == 1 -> player.moveTo(Tile(2382, 3129, 0))
        t.x == 2369 && t.z == 3126 && t.height == 2 -> player.moveTo(Tile(2372, 3126, 1))
        t.x == 2374 && t.z == 3133 && t.height == 3 -> player.moveTo(Tile(2374, 3130, 2))
    }
}

on_obj_option(obj = 36481, option = "Climb-up") { player.moveTo(Tile(2417, 3075, 0)) }
on_obj_option(obj = 36495, option = "Climb-up") { if (player.tile.height == 0) player.moveTo(Tile(2420, 3080, 1)) }
on_obj_option(obj = 36480, option = "Climb-up") { if (player.tile.height == 1) player.moveTo(Tile(2430, 3080, 2)) }
on_obj_option(obj = 36484, option = "Climb-up") { if (player.tile.height == 2) player.moveTo(Tile(2426, 3074, 3)) }
on_obj_option(obj = 36532, option = "Climb-up") { if (player.tile.height == 0) player.moveTo(Tile(2379, 3127, 1)) }
on_obj_option(obj = 36540, option = "Climb-up") { player.moveTo(Tile(2383, 3132, 0)) }
on_obj_option(obj = 36521, option = "Climb-up") { if (player.tile.height == 1) player.moveTo(Tile(2369, 3127, 2)) }
on_obj_option(obj = 36523, option = "Climb-up") { if (player.tile.height == 2) player.moveTo(Tile(2373, 3133, 3)) }

on_obj_option(obj = Objs.ENERGY_BARRIER, option = "Pass") { CastleWarsHandler.passBarrier(player, player.getInteractingGameObj()) }
on_obj_option(obj = Objs.ENERGY_BARRIER_4470, option = "Pass") { CastleWarsHandler.passBarrier(player, player.getInteractingGameObj()) }

on_obj_option(obj = Objs.STEPPING_STONE, option = "Jump-to") {
    val obj = player.getInteractingGameObj()
    if (obj.tile != player.tile) {
        player.animate(741)
        player.moveTo(obj.tile)
    }
}

// Equipment/supply tables - option text ("Take-1"/"Take-5") already verified real via
// runObjectDefProbeTool, so these are safe unlike the inventory-click barricade/bandage actions.
on_obj_option(obj = Objs.TOOLKIT_TABLE, option = "Take-1") { player.inventory.add(Items.TOOLKIT_4051, 1) }
on_obj_option(obj = Objs.TOOLKITS_TABLE, option = "Take-1") { player.inventory.add(Items.TOOLKIT_4051, 1) }
on_obj_option(obj = Objs.ROCKS_TABLE, option = "Take-1") { player.inventory.add(Items.ROCK_4043, 1) }
on_obj_option(obj = Objs.ROCKS_TABLE, option = "Take-5") { player.inventory.add(Items.ROCK_4043, 5) }
on_obj_option(obj = Objs.ROCKS_TABLE_36581, option = "Take-1") { player.inventory.add(Items.ROCK_4043, 1) }
on_obj_option(obj = Objs.ROCKS_TABLE_36581, option = "Take-5") { player.inventory.add(Items.ROCK_4043, 5) }
on_obj_option(obj = Objs.BARRICADES_TABLE, option = "Take-1") { player.inventory.add(Items.BARRICADE, 1) }
on_obj_option(obj = Objs.BARRICADES_TABLE, option = "Take-5") { player.inventory.add(Items.BARRICADE, 5) }
on_obj_option(obj = Objs.BARRICADES_TABLE_36582, option = "Take-1") { player.inventory.add(Items.BARRICADE, 1) }
on_obj_option(obj = Objs.BARRICADES_TABLE_36582, option = "Take-5") { player.inventory.add(Items.BARRICADE, 5) }
on_obj_option(obj = Objs.ROPES_TABLE, option = "Take-1") { player.inventory.add(Items.CLIMBING_ROPE, 1) }
on_obj_option(obj = Objs.ROPES_TABLE, option = "Take-5") { player.inventory.add(Items.CLIMBING_ROPE, 5) }
on_obj_option(obj = Objs.ROPES_TABLE_36583, option = "Take-1") { player.inventory.add(Items.CLIMBING_ROPE, 1) }
on_obj_option(obj = Objs.ROPES_TABLE_36583, option = "Take-5") { player.inventory.add(Items.CLIMBING_ROPE, 5) }
on_obj_option(obj = Objs.EXPLOSIVE_POTIONS_TABLE, option = "Take-1") { player.inventory.add(Items.EXPLOSIVE_POTION, 1) }
on_obj_option(obj = Objs.EXPLOSIVE_POTIONS_TABLE, option = "Take-5") { player.inventory.add(Items.EXPLOSIVE_POTION, 5) }
on_obj_option(obj = Objs.EXPLOSIVE_POTIONS_TABLE_36584, option = "Take-1") { player.inventory.add(Items.EXPLOSIVE_POTION, 1) }
on_obj_option(obj = Objs.EXPLOSIVE_POTIONS_TABLE_36584, option = "Take-5") { player.inventory.add(Items.EXPLOSIVE_POTION, 5) }
on_obj_option(obj = Objs.PICKAXES_TABLE, option = "Take-1") { player.inventory.add(Items.BRONZE_PICKAXE, 1) }
on_obj_option(obj = Objs.PICKAXES_TABLE_36585, option = "Take-1") { player.inventory.add(Items.BRONZE_PICKAXE, 1) }
on_obj_option(obj = Objs.BANDAGES_TABLE, option = "Take-1") { player.inventory.add(Items.BANDAGES, 1) }
on_obj_option(obj = Objs.BANDAGES_TABLE, option = "Take-5") { player.inventory.add(Items.BANDAGES, 5) }
on_obj_option(obj = Objs.BANDAGES_TABLE_36586, option = "Take-1") { player.inventory.add(Items.BANDAGES, 1) }
on_obj_option(obj = Objs.BANDAGES_TABLE_36586, option = "Take-5") { player.inventory.add(Items.BANDAGES, 5) }

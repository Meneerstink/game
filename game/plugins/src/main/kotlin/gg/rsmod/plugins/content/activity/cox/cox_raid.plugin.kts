package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.inter.bank.openBank
import kotlin.random.Random

/*
 * CoX fase 5, first slice (dossier COX.txt): the entrance steps (RAIDS_ENTRANCE_STEPS 29777, op 'Enter') make a raid instance from a random
 * OSRS layout and put the player in the starting room; the exit steps (RAIDS_EXIT_STEPS 29778, op 'Climb') lead back out.
 * Release gate (like ToA 07-11 'closed on live except admins'): until the raid is complete and live-proven only the test server and the
 * own check accounts may enter (CoxTestGate); anyone else gets the rev-667 'Nothing interesting happens.' as for any unhandled option.
 */

CoxIds.loc(29777)?.let { steps ->
    on_obj_option(obj = steps, option = "enter") {
        if (!CoxTestGate.allowed(player)) {
            player.message("Nothing interesting happens.")
            return@on_obj_option
        }
        CoxRaids.start(player)
    }
}

CoxIds.loc(29778)?.let { exit ->
    on_obj_option(obj = exit, option = "climb") {
        CoxRaids.leave(player)
    }
}

/*
 * Passages between rooms (RAIDS_DOORWAY 29789, op 'Enter'; placed by the cache map on every room edge - e.g. 6458,47 rot 0 in the
 * Muttadile room of layout 6/42, test server 15-11). tsps chambers-of-xeric.md 'Getting around the raid, from rsprox captures':
 * 'Passages: the player faces it and is through 2 ticks after arriving. Before the start the leader gets "Use the controls on the
 * <col=7f0000>side-panel</col><br>when you are ready to begin the raid." and the rest "You cannot proceed until <leader> has started the
 * raid." (message boxes).' Combat rooms' exit crystals (server-spawned, gone when the room is beaten) come with the rooms.
 */
CoxIds.loc(29789)?.let { passage ->
    on_obj_option(obj = passage, option = "enter") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_obj_option
        val obj = player.getInteractingGameObj()
        if (!raid.started && CoxRaids.inStartRoom(raid, player.tile)) {
            player.queue {
                if (player.username == raid.leader) {
                    messageBox("Use the controls on the <col=7f0000>side-panel</col><br>when you are ready to begin the raid.")
                } else {
                    messageBox("You cannot proceed until ${raid.leader} has started the raid.")
                }
            }
            return@on_obj_option
        }
        val def = obj.getDef(world.definitions)
        val exit = CoxRaids.passageExit(player.tile, obj.tile, def.getRotatedWidth(obj), def.getRotatedLength(obj))
        player.faceTile(obj.tile, def.getRotatedWidth(obj), def.getRotatedLength(obj))
        player.lock()
        player.queue {
            wait(2)
            player.moveTo(exit)
            player.unlock()
        }
    }
}

/*
 * Between floors (import log cox-map: RAIDS_DESCENTTO2 29734 / RAIDS_BOSSENTRANCE 29735 'Hole' op 'Enter' 2x2; RAIDS_ASCENTTO3 29995 'Rope'
 * op 'Climb-up' 1x3). tsps chambers-of-xeric.md (rsprox): 'Holes down: 2 ticks after arriving, the climb and fade; "<col=ff289d>Upper level
 * complete! Duration: </col><col=ff0000>m:ss</col>" (Middle, Lower)'; climb animations 827/828; landings move to the nearest open tile.
 * The screen fade is not done here (no 667 fade interface found yet - COX.txt).
 */
val LEVEL_NAMES = listOf("Upper", "Middle", "Lower")

listOfNotNull(CoxIds.loc(29734), CoxIds.loc(29735)).forEach { hole ->
    on_obj_option(obj = hole, option = "enter") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_obj_option
        val floor = CoxMap.roomAt(raid.map, raid.layout, player.tile)?.first ?: return@on_obj_option
        val landing = CoxRaids.holeLanding(raid, floor)
        val last = floor + 1 >= raid.layout.floors.size
        // Normal mode has two floors: leaving the top one completes 'Upper', reaching the Olm completes 'Lower'.
        val level = if (last) "Lower" else LEVEL_NAMES[floor]
        player.lock()
        player.queue {
            wait(2)
            player.animate(827)
            wait(1)
            player.teleportTo(landing)
            player.unlock()
            player.message("<col=ff289d>$level level complete! Duration: </col><col=ff0000>${CoxRaids.duration(raid)}</col>")
            val reached = if (last) 4 else floor + 2
            if (reached > raid.progress) { raid.progress = reached; CoxPartyTab.updateAll(raid) }
        }
    }
}

CoxIds.loc(29995)?.let { rope ->
    on_obj_option(obj = rope, option = "climb-up") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_obj_option
        val floor = CoxMap.roomAt(raid.map, raid.layout, player.tile)?.first ?: return@on_obj_option
        val landing = CoxRaids.ropeLanding(raid, floor) ?: return@on_obj_option
        player.lock()
        player.queue {
            player.animate(828)
            wait(2)
            player.teleportTo(landing)
            player.unlock()
        }
    }
}

/*
 * The first room's bank chest (cox-map import: 47420 'Bank chest' ops Use / Collect). OSRS Wiki Chambers of Xeric: 'The entrance room
 * contains a bank chest to allow players to adjust their inventories ... the bank chest will be closed shut once the raid begins'
 * (tsps rsprox: 47420 -> 47419 as the raid begins). Until the closed chest 47419 is imported the open one stays and refuses after the start.
 * 'Collect' = the Grand Exchange collection box like every Bank+Collect chest/banker (banker_collect / ToA Bank Camel).
 */
CoxIds.loc(47420)?.let { chest ->
    on_obj_option(obj = chest, option = "use") {
        val raid = CoxRaids.raidAt(player.tile)
        if (raid != null && raid.started) return@on_obj_option
        player.openBank()
    }
    on_obj_option(obj = chest, option = "collect") {
        val raid = CoxRaids.raidAt(player.tile)
        if (raid != null && raid.started) return@on_obj_option
        gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.openCollectionBox(player)
    }
}

/*
 * Death in the chambers (OSRS Wiki Chambers of Xeric 'Death mechanics' - owner besluit 6a 'dood zoals OSRS'): safe (items kept, never
 * ejected), raid-only items dropped where the raider fell, respawn at the floor's respawn point (CoxRaids.respawnTile). The points
 * penalty (-40%, or the team -5%) comes with the points system.
 */
val COX_DIED_IN = gg.rsmod.game.model.attr.AttributeKey<Long>()

gg.rsmod.plugins.content.mechanics.death.SafeDeath.register { player -> CoxRaids.raidAt(player.tile) != null }

on_player_death_start {
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_player_death_start
    player.attr[COX_DIED_IN] = raid.id
    if (raid.started) CoxRaids.deathPenalty(raid, player.username)
    val raidOnly = CoxRaids.raidOnlyLocal()
    val tile = player.tile
    listOf(player.inventory, player.equipment).forEach { container ->
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            if (item.id !in raidOnly) continue
            container.remove(item.id, item.amount, beginSlot = slot)
            world.spawn(gg.rsmod.game.model.entity.GroundItem(item.id, item.amount, tile, player))
        }
    }
}

on_player_death {
    val id = player.attr[COX_DIED_IN] ?: return@on_player_death
    player.attr.remove(COX_DIED_IN)
    val raid = CoxRaids.byId(id) ?: return@on_player_death
    player.teleportTo(CoxRaids.respawnTile(raid))
}

/*
 * The raid's rules (tsps chambers-of-xeric.md from rsprox captures, word for word): 'no teleporting ("You cannot teleport from the Chambers
 * of Xeric."); no dropping items before the raid starts ("You should keep your raiding dungeon tidy until your raid has begun.")'.
 * Teleports go through ActivityRestrictions (every teleport route asks it). Dropping: the global drop confirmation of OpHeld5Handler, wrapped
 * at world init so the untradeable drop warning (drop_warning.plugin.kts) keeps working outside the raid. Escape crystals: later (COX.txt).
 */
gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.register { player, action ->
    if (action == gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.TELEPORT && CoxRaids.raidAt(player.tile) != null)
        "You cannot teleport from the Chambers of Xeric." else null
}

on_world_init {
    val previous = gg.rsmod.game.message.handler.OpHeld5Handler.confirm
    gg.rsmod.game.message.handler.OpHeld5Handler.confirm = { player, item, slot ->
        val raid = CoxRaids.raidAt(player.tile)
        if (raid != null && !raid.started) {
            player.message("You should keep your raiding dungeon tidy until your raid has begun.")
            true
        } else {
            previous?.invoke(player, item, slot) ?: false
        }
    }
}

/** Test server / check accounts: ::coxbegin = the leader starts the raid (the party tab's start button follows with the party UI). */
on_command("coxbegin", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    CoxRaids.begin(player, raid)
}

/** The party tab's 'Start raid' (OSRS 500:14, rebuilt as component 24 - CoxPartyTab): the leader begins the raid. */
CoxInterfaces.of(CoxInterfaces.PARTY_TAB)?.let { tab ->
    on_button(tab, CoxPartyTab.START) {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_button
        CoxRaids.begin(player, raid)
    }
}

on_world_init {
    world.queue {
        while (true) {
            wait(10)
            CoxRaids.releaseEmpty(world)
        }
    }
    world.queue {
        while (true) {
            wait(1)
            CoxRaids.tick(world)
        }
    }
}

/** Test server / check accounts: ::coxstart [pattern index 0-26] [seed] = a raid with that OSRS layout code (CoxLayouts.LAYOUT_CODES). */
on_command("coxstart", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val args = player.getCommandArgs()
    val seed = args.getOrNull(1)?.toIntOrNull() ?: Random.Default.nextInt()
    val pattern = args.getOrNull(0)?.toIntOrNull()?.takeIf { it in CoxLayouts.PATTERNS.indices }
    val layout = if (pattern != null) CoxLayouts.normal(Random(seed), listOf(CoxLayouts.PATTERNS[pattern])) else CoxLayouts.normal(Random(seed))
    if (CoxRaids.raidAt(player.tile) != null) CoxRaids.leave(player)
    val raid = CoxRaids.start(player, layout)
    player.message("CoX test raid ${raid?.id}: ${layout.pattern} seed $seed")
}

/** Test server / check accounts: ::coxroom <floor 1|2> <room index> | ::coxroom olm = teleport to the middle of that room of your raid. */
on_command("coxroom", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: run { player.message("Not in a CoX raid."); return@on_command }
    val args = player.getCommandArgs()
    if (args.firstOrNull() == "olm") {
        player.teleportTo(CoxMap.olmTile(raid.map, CoxMap.OLM_CENTRE_SOURCE))
        return@on_command
    }
    val floor = (args.getOrNull(0)?.toIntOrNull() ?: 1) - 1
    val index = args.getOrNull(1)?.toIntOrNull() ?: 0
    val room = raid.layout.floors.getOrNull(floor)?.getOrNull(index) ?: run { player.message("No room $floor/$index."); return@on_command }
    val middle = gg.rsmod.game.model.Tile(room.sourceChunkX * 8 + 16, room.room.chunkZ * 8 + 16)
    player.teleportTo(CoxMap.roomTile(raid.map, floor, room, middle))
    player.message("CoX room ${floor + 1}/$index ${room.room} entry=${room.entry} v${room.variant}")
}

/** Test server / check accounts: ::coxpoints [n] = add n personal points (until the rooms give points), always tells the points. */
on_command("coxpoints", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    player.getCommandArgs().firstOrNull()?.toIntOrNull()?.let { raid.addPoints(player.username, it) }
    player.message("CoX points: mine ${raid.points[player.username.lowercase()] ?: 0}, total ${raid.totalPoints()}")
}

on_command("coxleave", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    CoxRaids.leave(player)
}

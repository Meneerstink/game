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

/** The entrance warning was accepted once (tsps: only the first time). */
val COX_WARNED = gg.rsmod.game.model.attr.AttributeKey<Boolean>(persistenceKey = "cox_entrance_warned")

CoxIds.loc(29777)?.let { steps ->
    on_obj_option(obj = steps, option = "enter") {
        if (!CoxTestGate.allowed(player)) {
            player.message("Nothing interesting happens.")
            return@on_obj_option
        }
        // tsps 'The entrance' (rsprox): the first time a warning, "Accept warning and proceed." or "Cancel.".
        if (player.attr[COX_WARNED] == true) {
            CoxRaids.enter(player)
            return@on_obj_option
        }
        player.queue {
            messageBox("Items left in a dungeon cannot be retrieved. Participants may be ejected at any time. Don't risk dropping items that you want to keep.")
            if (options("Accept warning and proceed.", "Cancel.") == 1) {
                player.attr[COX_WARNED] = true
                CoxRaids.enter(player)
            }
        }
    }
}

CoxIds.loc(29778)?.let { exit ->
    on_obj_option(obj = exit, option = "climb") {
        // tsps (rsprox): the first room's steps ask "You will not be able to rejoin this raid again." (Leave the raid. / Stay.); the exit
        // steps by the ancient chest in the Olm's chamber ask "You won't be able to re-enter." (Leave. / Stay.).
        val raid = CoxRaids.raidAt(player.tile)
        val olmChamber = raid != null && CoxMap.roomAt(raid.map, raid.layout, player.tile) == null
        player.queue {
            val leave = if (olmChamber) options("Leave.", "Stay.", title = "You won't be able to re-enter.")
                else options("Leave the raid.", "Stay.", title = "You will not be able to rejoin this raid again.")
            if (leave == 1) CoxRaids.leave(player)
        }
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
                if (player.username.lowercase() == CoxRaids.leaderOf(raid)) {
                    messageBox("Use the controls on the <col=7f0000>side-panel</col><br>when you are ready to begin the raid.")
                } else {
                    messageBox("You cannot proceed until ${CoxRaids.leaderName(raid)} has started the raid.")
                }
            }
            return@on_obj_option
        }
        gg.rsmod.plugins.content.activity.cox.rooms.CoxRooms.passRefusal(raid, player, obj.tile)?.let { refusal ->
            player.message(refusal)
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

/*
 * The Ancient chest (RAIDS_REWARD_CHEST 30028 'Search', OsrsLocImportTool 'cox-locs'): opens the rewards (CoxRewards, OSRS 539). The chest
 * replaces the crystal 30027 when the Olm dies (with the Olm). ::coxloot (test server / check accounts) = the raid's completion now.
 */
CoxIds.loc(30028)?.let { chest ->
    on_obj_option(obj = chest, option = "search") { if (CoxRewards.has(player)) CoxRewards.open(player) }
}

CoxInterfaces.of(CoxInterfaces.REWARDS)?.let { iface ->
    (listOf(CoxRewards.BANK_ALL, CoxRewards.TAKE_ALL, CoxRewards.DISCARD_ALL, CoxRewards.BOOK) +
        (CoxRewards.FIRST_SLOT until CoxRewards.FIRST_SLOT + CoxRewards.SLOTS)).forEach { component ->
        on_button(iface, component) { CoxRewards.click(player, component) }
    }
}

on_command("coxloot", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    // The raid's end as the Olm's death does it (CoxChest): loot rolled, the crystal by the steps turns into the chest 6 ticks later.
    CoxChest.complete(raid)
    CoxRewards.open(player)
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
    val raid = CoxRaids.enter(player, layout)
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

/*
 * The recruiting board (RAIDS_RECRUITING_BOARD 29776 'Read' on Mount Quidamortem, OSRS 499) and the party details (OSRS 507): CoxPartyBoard.
 * Parties are 78legacy's own (besluit 5a, ToA pattern) instead of OSRS's chat-channel parties.
 */
CoxIds.loc(29776)?.let { board ->
    on_obj_option(obj = board, option = "read") {
        if (!CoxTestGate.allowed(player)) {
            player.message("Nothing interesting happens.")
            return@on_obj_option
        }
        CoxPartyBoard.openBoard(player)
    }
}

CoxInterfaces.of(CoxInterfaces.PARTY_LIST)?.let { iface ->
    (listOf(CoxPartyBoard.REFRESH, CoxPartyBoard.MAKE_PARTY, CoxPartyBoard.MY_PARTY) +
        (0 until CoxPartyBoard.MAX_ROWS).map { CoxPartyBoard.ROW_CONTAINER + it }).forEach { component ->
        on_button(iface, component) { CoxPartyBoard.clickBoard(player, component) }
    }
}

CoxInterfaces.of(CoxInterfaces.PARTY_DETAILS)?.let { iface ->
    listOf(CoxPartyBoard.D_JOIN, CoxPartyBoard.D_ADVERTISE, CoxPartyBoard.D_DISBAND, CoxPartyBoard.D_BACK, CoxPartyBoard.D_REFRESH).forEach { component ->
        on_button(iface, component) { CoxPartyBoard.clickDetails(player, component) }
    }
}

/*
 * CoX weapons' stand / walk / run (OsrsBasImportTool stances 'elder_maul' -> BAS 2530 = HUMAN_ELDER_MAUL_READY 7518 / _WALK 7520 / _RUN 7519,
 * 'dinhs_bulwark' -> BAS 2531 = HUMAN_DINHS_BULWARK_READY 7508 / _WALK 7510 / _RUN 7509; cache round 5 tx in COX.txt).
 */
CoxLootItems.local(21003)?.let { gg.rsmod.game.model.RenderAnimations.register(2530, it) }
CoxLootItems.local(21015)?.let { gg.rsmod.game.model.RenderAnimations.register(2531, it) }

/*
 * CoX weapons' attack / block (OsrsWeaponLooks): Elder maul = HUMAN_ELDER_MAUL_ATTACK 7516 / _BLOCK 7517 on every style (OsrsSeq, imported
 * with the Colossal blade); Dinh's bulwark = HUMAN_DINHS_BULWARK_CRUSH 7507 (Pummel) / _BLOCK 7512 (fx 'cox-weapons-seq').
 * ONZEKER: Dinh's 'Block' style (no attack) and its special 'Shield bash' (HUMAN_DINHS_BULWARK_BASH 7511) come with the K-ITEM work.
 */
CoxLootItems.local(21003)?.let { maul ->
    val a = gg.rsmod.plugins.content.items.osrs.OsrsSeq.ELDER_MAUL_ATTACK
    gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.register(
        gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.Look(intArrayOf(a, a, a, a), block = gg.rsmod.plugins.content.items.osrs.OsrsSeq.ELDER_MAUL_DEFEND), maul)
}
CoxLootItems.local(21015)?.let { bulwark ->
    val crush = CoxIds.seq(7507) ?: return@let
    gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.register(
        gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.Look(intArrayOf(crush, crush, crush, crush), block = CoxIds.seq(7512)), bulwark)
}

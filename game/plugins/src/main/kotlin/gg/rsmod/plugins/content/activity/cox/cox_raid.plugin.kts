package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.priv.Privilege
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

on_world_init {
    world.queue {
        while (true) {
            wait(10)
            CoxRaids.releaseEmpty(world)
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

on_command("coxleave", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    CoxRaids.leave(player)
}

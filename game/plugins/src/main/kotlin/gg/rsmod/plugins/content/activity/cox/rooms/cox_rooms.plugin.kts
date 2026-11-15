package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxRaids
import gg.rsmod.plugins.content.activity.cox.CoxTestGate
import gg.rsmod.plugins.content.combat.dealHit

/**
 * CoX rooms (dossier COX.txt fase 'bazen + puzzels'): the room classes drive their npcs (ToA pattern); the generic NPC auto-retaliation
 * loop is switched off for them so nothing swings twice.
 */
CoxRooms.DRIVEN_OSRS.mapNotNull(CoxIds::npc).distinct().forEach { id -> on_npc_combat(npc = id) { } }

/** Skeletal Mystics' portals (tsps rsprox: 49996 Travel-End, 49997 Travel / Travel-End / Travel-Start, 49998 Travel-Start). */
fun mysticsRoom(p: gg.rsmod.game.model.entity.Player): MysticsRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<MysticsRoom>().firstOrNull { it.contains(p.tile) } }

CoxIds.loc(49996)?.let { id -> on_obj_option(obj = id, option = "Travel-End") { mysticsRoom(player)?.travel(player, true) } }
CoxIds.loc(49998)?.let { id -> on_obj_option(obj = id, option = "Travel-Start") { mysticsRoom(player)?.travel(player, false) } }
CoxIds.loc(49997)?.let { id ->
    on_obj_option(obj = id, option = "Travel-End") { mysticsRoom(player)?.travel(player, true) }
    on_obj_option(obj = id, option = "Travel-Start") { mysticsRoom(player)?.travel(player, false) }
    on_obj_option(obj = id, option = "Travel") {
        val room = mysticsRoom(player) ?: return@on_obj_option
        player.queue {
            when (options("End of the room", "Start of the room", title = "Choose location")) {
                1 -> room.travel(player, true)
                2 -> room.travel(player, false)
            }
        }
    }
}

/**
 * Tendrils (Spirit tendrils 29768 / Noxious 29767 / Septic 30024, op 'Pass'): tsps rsprox 'passing the tendrils is a two-tile walk through
 * them with sound 3728'.
 */
listOf(29768, 29767, 30024).mapNotNull(CoxIds::loc).forEach { id ->
    on_obj_option(obj = id, option = "Pass") {
        val o = player.getInteractingGameObj()
        val here = player.tile
        val dx = Integer.signum(o.tile.x - here.x)
        val dz = if (dx == 0) Integer.signum(o.tile.z - here.z) else 0
        player.playSound(3728)
        player.walkTo(here.transform(dx * 2, dz * 2), detectCollision = false)
    }
}

/** Tekton's fire at the way in (30021, op 'Cross'): through to its other side (tsps crossFire). */
CoxIds.loc(30021)?.let { id ->
    on_obj_option(obj = id, option = "Cross") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_obj_option
        val room = CoxRooms.rooms(raid).filterIsInstance<TektonRoom>().firstOrNull { it.contains(player.tile) } ?: return@on_obj_option
        room.crossFire(player, player.getInteractingGameObj())
    }
}

/** Prayer blocks of the rooms (all three books ask ActivityRestrictions PRAYER). */
gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.register { p, action ->
    if (action == gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER) CoxRooms.prayerRefusal(p) else null
}

/** The meat tree (Muttadiles' room, npc 7564 'Chop'). */
CoxIds.npc(7564)?.let { id ->
    on_npc_option(npc = id, option = "Chop") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_npc_option
        CoxRooms.rooms(raid).filterIsInstance<MuttadilesRoom>().firstOrNull { it.contains(player.tile) }?.chop(player)
    }
}

/** Test server / check accounts: ::coxclear = every npc of the room you stand in dies (puzzles: their own ending, later). */
on_command("coxclear", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    val room = CoxRooms.rooms(raid).firstOrNull { it.contains(player.tile) } ?: run { player.message("No CoX encounter here."); return@on_command }
    room.npcs.toList().filter { room.alive(it) }.forEach { n ->
        val hp = n.getCurrentLifepoints().toDouble()
        player.dealHit(n, minHit = hp, maxHit = hp, landHit = true, delay = 0, hitType = gg.rsmod.plugins.api.HitType.REGULAR_HIT)
    }
    player.message("CoX: cleared ${room.laid.room}.")
}

/** Test server / check accounts: ::coxrooms = the raid's rooms with their encounter state. */
on_command("coxrooms", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    CoxRooms.rooms(raid).forEach { r ->
        player.message("${r.floor + 1}: ${r.laid.room} v${r.variant} npcs=${r.npcs.count { r.alive(it) }} done=${r.completed}")
    }
}

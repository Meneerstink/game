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

/** The meat tree (Muttadiles room, npc 7564 op Cut). */
CoxIds.npc(7564)?.let { id ->
    on_npc_option(npc = id, option = "Cut") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_npc_option
        CoxRooms.rooms(raid).filterIsInstance<MuttadilesRoom>().firstOrNull { it.contains(player.tile) }?.chop(player)
    }
}

/** Vespula's room: medivaemia roots (30068 'Pick') and blossoms (20892) on the lux grubs (7534-7536). */
fun vespulaRoom(p: gg.rsmod.game.model.entity.Player): VespulaRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<VespulaRoom>().firstOrNull { it.contains(p.tile) } }
CoxIds.loc(30068)?.let { id -> on_obj_option(obj = id, option = "Pick") { vespulaRoom(player)?.pick(player, player.getInteractingGameObj()) } }
CoxIds.item(20892)?.let { blossom ->
    listOf(7534, 7535, 7536).mapNotNull(CoxIds::npc).forEach { grub ->
        on_item_on_npc(item = blossom, npc = grub) { vespulaRoom(player)?.feed(player, player.getInteractingNpc()) }
    }
}

/** Ice Demon braziers: unlit 29747 'Light' (map loc), lit 29748 'Fuel'. */
fun iceRoom(p: gg.rsmod.game.model.entity.Player): IceDemonRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<IceDemonRoom>().firstOrNull { it.contains(p.tile) } }
CoxIds.loc(29747)?.let { id -> world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.firstOrNull { it != null && it.isNotBlank() }?.let { op ->
    on_obj_option(obj = id, option = op) { iceRoom(player)?.light(player, player.getInteractingGameObj()) }
} }
CoxIds.loc(29748)?.let { id -> on_obj_option(obj = id, option = "Fuel") { iceRoom(player)?.fuel(player, player.getInteractingGameObj()) } }

/** The first cache option of an OSRS loc (template locs whose op names we read from the definition, never guessed). */
fun firstOp(osrsLoc: Int): Pair<Int, String>? = CoxIds.loc(osrsLoc)?.let { id ->
    world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.firstOrNull { !it.isNullOrBlank() }?.let { id to it }
}

/** Tightrope room: rope 29750, keystone 29751, barrier 29749 'Dispel' (+ keystone on it). */
fun ropeRoom(p: gg.rsmod.game.model.entity.Player): TightropeRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<TightropeRoom>().firstOrNull { it.contains(p.tile) } }
firstOp(29750)?.let { (id, op) -> on_obj_option(obj = id, option = op) { ropeRoom(player)?.cross(player, player.getInteractingGameObj()) } }
firstOp(29751)?.let { (id, op) -> on_obj_option(obj = id, option = op) { ropeRoom(player)?.take(player) } }
CoxIds.loc(29749)?.let { id ->
    on_obj_option(obj = id, option = "Dispel") { ropeRoom(player)?.dispel(player) }
    CoxIds.item(20884)?.let { key -> on_item_on_obj(obj = id, item = key) { ropeRoom(player)?.dispel(player) } }
}

/** Jewelled crabs: 'Smash' (only registered when the cache npc has that option). */
listOf(7576, 7577, 7578, 7579).mapNotNull(CoxIds::npc).forEach { id ->
    if (world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, id).options.any { it == "Smash" }) on_npc_option(npc = id, option = "Smash") {
        val raid = CoxRaids.raidAt(player.tile) ?: return@on_npc_option
        CoxRooms.rooms(raid).filterIsInstance<CrabsRoom>().firstOrNull { it.contains(player.tile) }?.smash(player, player.getInteractingNpc())
    }
}

/** Thieving room: chests 29742 (first cache op), troughs 29746 / 29874 'Deposit'. */
fun thiefRoom(p: gg.rsmod.game.model.entity.Player): ThievingRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<ThievingRoom>().firstOrNull { it.contains(p.tile) } }
firstOp(29742)?.let { (id, op) -> on_obj_option(obj = id, option = op) { thiefRoom(player)?.search(player, player.getInteractingGameObj()) } }
listOf(29746, 29874).mapNotNull(::firstOp).forEach { (id, op) -> on_obj_option(obj = id, option = op) { thiefRoom(player)?.deposit(player, player.getInteractingGameObj()) } }

/** The Olm: the mystical barrier 29879 ('Pass' asks, 'Quick-pass' doesn't) and the fire walls' 'Douse' (npc 7558). */
CoxIds.loc(29879)?.let { id ->
    val ops = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.filterNotNull()
    if ("Pass" in ops) on_obj_option(obj = id, option = "Pass") { CoxRaids.raidAt(player.tile)?.let { CoxRooms.olm(it) }?.passBarrier(player, player.getInteractingGameObj(), true) }
    if ("Quick-pass" in ops) on_obj_option(obj = id, option = "Quick-pass") { CoxRaids.raidAt(player.tile)?.let { CoxRooms.olm(it) }?.passBarrier(player, player.getInteractingGameObj(), false) }
}
CoxIds.npc(7558)?.let { id ->
    if (world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, id).options.any { it == "Douse" }) on_npc_option(npc = id, option = "Douse") {
        CoxRaids.raidAt(player.tile)?.let { CoxRooms.olm(it) }?.douse(player, player.getInteractingNpc())
    }
}

/** Test server / check accounts: ::coxclear = every npc of the room you stand in dies (puzzles: their own ending, later). */
on_command("coxclear", Privilege.ADMIN_POWER) {
    if (!CoxTestGate.allowed(player)) return@on_command
    val raid = CoxRaids.raidAt(player.tile) ?: return@on_command
    val room = CoxRooms.rooms(raid).firstOrNull { it.contains(player.tile) } ?: run { player.message("No CoX encounter here."); return@on_command }
    if (room is OlmRoom) { room.testKill(); player.message("CoX: the Olm dies (test)."); return@on_command }
    room.testClear = true
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

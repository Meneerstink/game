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

/** The raid's gourd potions (CoxPotions). */
gg.rsmod.plugins.content.activity.cox.CoxPotions.DOSES.forEach { (osrs, dose) ->
    gg.rsmod.plugins.content.activity.cox.CoxPotions.local(dose)?.let { id ->
        on_item_option(item = id, option = "Drink") { gg.rsmod.plugins.content.activity.cox.CoxPotions.drink(player, player.getInteractingItemSlot(), osrs) }
    }
}

/** Gathering and food (CoxSupplies, ResourcesRoom): op names from the cache (tools/cache667 locs.py / npcs.py, 15-11 23:5x). */
fun resRoom(p: gg.rsmod.game.model.entity.Player): ResourcesRoom? =
    CoxRaids.raidAt(p.tile)?.let { raid -> CoxRooms.rooms(raid).filterIsInstance<ResourcesRoom>().firstOrNull { it.contains(p.tile) } }
CoxIds.loc(29889)?.let { id -> on_obj_option(obj = id, option = "Fish") { CoxSupplies.fish(player, player.getInteractingGameObj()) } }
CoxIds.loc(29763)?.let { id -> on_obj_option(obj = id, option = "Chop") { CoxSupplies.chop(player, player.getInteractingGameObj()) } }
CoxIds.loc(29772)?.let { id ->
    on_obj_option(obj = id, option = "Pick") { CoxSupplies.pickGourds(player, false) }
    on_obj_option(obj = id, option = "Pick-lots") { CoxSupplies.pickGourds(player, true) }
}
CoxIds.loc(29878)?.let { id ->
    on_obj_option(obj = id, option = "Use") { CoxSupplies.fillGourds(player) }
    CoxIds.item(CoxSupplies.EMPTY_GOURD)?.let { g -> on_item_on_obj(obj = id, item = g) { CoxSupplies.fillGourds(player) } }
}
CoxIds.loc(29773)?.let { id -> on_obj_option(obj = id, option = "Rake") { CoxSupplies.rake(player) } }
CoxIds.loc(29771)?.let { id ->
    on_obj_option(obj = id, option = "Take Rake") { CoxSupplies.takeTool(player, 5341) }
    on_obj_option(obj = id, option = "Take Spade") { CoxSupplies.takeTool(player, 952) }
    on_obj_option(obj = id, option = "Take Seed Dibber") { CoxSupplies.takeTool(player, 5343) }
}
mapOf(31862 to 1351, 29711 to 2347, 31634 to 590, 41754 to 1267).forEach { (loc, item) ->
    CoxIds.loc(loc)?.let { id -> on_obj_option(obj = id, option = "Take") { CoxSupplies.takeTool(player, item) } }
}
CoxIds.loc(30066)?.let { id -> on_obj_option(obj = id, option = "Touch") { CoxSupplies.touchWell(player) } }
(7587..7593).mapNotNull(CoxIds::npc).forEach { id -> on_npc_option(npc = id, option = "Catch") { resRoom(player)?.catchBat(player, player.getInteractingNpc()) } }
CoxIds.item(CoxSupplies.KINDLING)?.let { k -> on_item_on_item(item1 = 590, item2 = k) { CoxSupplies.lightFire(player) } }
CoxSupplies.RAW.keys.mapNotNull(CoxIds::item).forEach { raw ->
    on_item_on_obj(obj = CoxSupplies.FIRE_LOC, item = raw) { CoxSupplies.cook(player, raw) }
}
CoxSupplies.FOOD.keys.forEach { osrs ->
    CoxIds.item(osrs)?.let { id -> on_item_option(item = id, option = "Eat") { CoxSupplies.eat(player, osrs, player.getInteractingItemSlot()) } }
}

/** Herb patches: a seed on the empty patch 29765, Pick / Clear on the grown ones (30009-30011). */
CoxIds.loc(29765)?.let { patch -> CoxSupplies.HERBS.mapNotNull { CoxIds.item(it.seed) }.forEach { seed ->
    on_item_on_obj(obj = patch, item = seed) { CoxSupplies.plant(player, player.getInteractingGameObj(), seed) }
} }
(30009..30011).mapNotNull(CoxIds::loc).forEach { id ->
    on_obj_option(obj = id, option = "Pick") { CoxSupplies.pick(player, player.getInteractingGameObj()) }
    on_obj_option(obj = id, option = "Clear") { CoxSupplies.clear(player, player.getInteractingGameObj()) }
}
on_world_init { world.queue { while (true) { wait(1); CoxSupplies.growTick(world) } } }

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

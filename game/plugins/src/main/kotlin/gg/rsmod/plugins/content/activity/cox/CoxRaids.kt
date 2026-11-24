package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.api.ext.message

import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** One running Chambers of Xeric instance (fase 5, first slice: solo entry into a generated layout; party/rooms follow). */
class CoxRaid(val id: Long, val layout: CoxLayout, val map: InstancedMap, val world: World, val leader: String) {
    @Volatile var closed = false
    /** tsps r118: the first room's passage stays shut until the leader starts the raid (party tab; ::coxbegin on the test server). */
    @Volatile var started = false
    var startCycle = -1
    /** raids_client_progress (CoxPartyTab): 0 before the start, 1 upper level, 2 next level, 4 the Olm, 5 complete. */
    @Volatile var progress = 0
    /** Personal points by lower-case username (tsps: 'Only raiders there at the start earn points'). */
    val points = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun totalPoints(): Int = points.values.sum()

    fun addPoints(username: String, amount: Int) {
        if (amount <= 0) return
        points.merge(username.lowercase(), amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    }
}

/**
 * CoX raids (dossier COX.txt fase 5; same lifetime pattern as ToaLobby: the map is MANAGED_LIFETIME and released here once nobody of the
 * raid is inside, so [isRaidTile] never answers for a stale map).
 * OSRS Wiki Chambers of Xeric: 'the leader should enter the chambers, where a starting room is located'; the entrance is the steps
 * RAIDS_ENTRANCE_STEPS 29777 on Mount Quidamortem (op 'Enter', cache 2686), placed at 1232,3573 (imported in place, testserver 15-11).
 * The starting room's spawn per version (west / straight / east) = CoxMap.RAID_START_SPAWN (tsps BOUND.RAID_START).
 */
object CoxRaids : KLogging() {
    /**
     * Where leaving puts you: 1246,3558 on the mountain (RSPSApp/tsps chambers-of-xeric.md 'Out is (1246, 3558)', from rsprox OSRS
     * captures; the OSRS Wiki map pin of the Chambers of Xeric is 1245,3557 next to it). Walkable: the bot walked BLS -> 1233,3571 past it.
     */
    val OUTSIDE = Tile(1246, 3558, 0)

    private val raids = ConcurrentHashMap<InstancedMap, CoxRaid>()
    private var nextId = 1L

    /** Guarded like the ToA raid (GuardedZones): party members fight the chambers, never each other. */
    fun isRaidTile(tile: Tile): Boolean = raids.values.any { !it.closed && it.map.area.contains(tile) }

    /**
     * The gathering plateau at the entrance on Mount Quidamortem (recruiting boards 1224,3562 / 1240,3549 / 1246,3562, the steps, the
     * storage unit 1256,3572, the second board 1232,3573): guarded like the ToA lobby (N609 owner report 'de entree lobby toa dangerous
     * zone'). Eigen advies 16-11 (dossier COX.txt): the same rule for the CoX party area; the bounds are the plateau around those locs.
     */
    fun inLobby(tile: Tile): Boolean = tile.height == 0 && tile.x in 1220..1260 && tile.z in 3545..3578

    fun raidAt(tile: Tile): CoxRaid? = raids.values.firstOrNull { !it.closed && it.map.area.contains(tile) }

    fun byId(id: Long): CoxRaid? = raids.values.firstOrNull { !it.closed && it.id == id }

    /**
     * Raid-only items can't leave the raid (tsps CoxRaid.dropRaidItems, rsprox): taken from the inventory and equipment and dropped
     * where the raider stood - on death, on leaving and on logging out (16-11 breaktest: only death did it, so an overload walked out
     * of the raid with a coxleave or a logout).
     */
    fun dropRaidItems(player: Player, at: Tile) {
        val raidOnly = raidOnlyLocal()
        listOf(player.inventory, player.equipment).forEach { container ->
            for (slot in 0 until container.capacity) {
                val item = container[slot] ?: continue
                if (item.id !in raidOnly) continue
                container.remove(item.id, item.amount, beginSlot = slot)
                player.world.spawn(gg.rsmod.game.model.entity.GroundItem(item.id, item.amount, Tile(at), player))
            }
        }
    }

    /** Who logged out of a raid that had begun (username -> raid id): logging in brings them back while it lasts. */
    private val rejoining = HashMap<String, Long>()

    /**
     * A raider logged out inside a raid (cox_raid.plugin.kts on_logout; the engine has already put them on the instance's exit tile,
     * [inside] is where they stood). tsps CoxRaid.playerLeftArea/removePlayer (rsprox): raid-only items drop there; a member of a raid that
     * has begun may come back ('takeRejoin'); Wiki: 'the raid can also be exited through ... logging out until the raid party has all
     * left' - when nobody is left the instance goes (releaseEmpty) and there is nothing to come back to.
     */
    fun loggedOut(player: Player, inside: Tile) {
        val raid = raidAt(inside) ?: return
        dropRaidItems(player, inside)
        CoxPotions.clear(player)
        val key = player.username.lowercase()
        if (raid.started && raid.points.containsKey(key)) rejoining[key] = raid.id
    }

    /**
     * Logging in after [loggedOut]: back into the raid at its respawn point if it is still on (tsps Raid.returnOnLogin -> admit(): the
     * raid's respawn), else outside - where the engine's exit tile already put them - with tsps' 'Your party could not be re-found.'
     */
    fun loggedIn(player: Player) {
        val id = rejoining.remove(player.username.lowercase()) ?: return
        val raid = byId(id)
        if (raid == null) {
            player.message("Your party could not be re-found.")
            return
        }
        player.moveTo(respawnTile(raid))
        CoxMusic.resume(player)
        CoxPartyTab.open(player, raid)
        CoxPointsOverlay.open(player)
    }

    /** Creates a raid with [layout] (default: a random OSRS small layout, besluit 9a) and moves [player] to the starting room. */
    fun start(player: Player, layout: CoxLayout = CoxLayouts.normal(Random.Default)): CoxRaid? {
        val world = player.world
        val map = CoxMap.allocate(world, layout, OUTSIDE) ?: run {
            logger.warn { "CoX: no free instance space for ${player.username}" }
            return null
        }
        val raid = CoxRaid(nextId++, layout, map, world, player.username)
        raids[map] = raid
        logger.info { "[cox] raid ${raid.id} ${player.username} base=${map.area.bottomLeftX},${map.area.bottomLeftZ} pattern=${layout.pattern} floors=${layout.floors.map { f -> f.map { "${it.room}@${it.gridX},${it.gridZ}/${it.entry}/v${it.variant}" } }}" }
        // The first room's steps are RAIDS_EXIT_STEPS_MULTI 49999, a multiloc (RAIDS_EXIT_STEPS 29778, or _RELOAD 50000 for the leader
        // before the start - tsps 'Reload'). The 667 loc type stores multiloc variants as u16 and our imported locs are >= 65536 (16-11:
        // the cache table wrapped to 1142/1372, rolled back), so the room gets plain 29778 and the leader is SHOWN 50000 on that tile per
        // player (LocAddChange + engine LOC_ALIAS_ATTR, [syncReload]).
        val multi = CoxIds.loc(49999)
        val steps = CoxIds.loc(29778)
        if (multi != null && steps != null) replaceInRoom(raid, 0, layout.floors[0][0], multi, steps).firstOrNull()?.let { reloadSteps[raid.id] = it }
        player.teleportTo(spawnTile(raid))
        CoxMusic.floor(player, 0)
        CoxPartyTab.open(player, raid)
        syncReload(raid, player)
        return raid
    }

    /** The first room's steps (29778 in the world) per raid id - the tile the leader is shown the 'Reload' steps 50000 on. */
    private val reloadSteps = ConcurrentHashMap<Long, gg.rsmod.game.model.entity.GameObject>()

    /** Shows 'Reload' on the first room's steps to the raid's leader until the raid begins (and [extra], who may not be inside yet). */
    fun syncReload(raid: CoxRaid, extra: Player? = null) {
        val leader = leaderOf(raid)
        val steps = reloadSteps[raid.id] ?: return
        val reload = CoxIds.loc(50000)
        (raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) } + listOfNotNull(extra)).distinct().forEach {
            showSteps(it, steps, if (reload != null && !raid.started && !raid.closed && it.username.lowercase() == leader) reload else null)
        }
    }

    /** Sends [player] the steps as [shown] (or back to the world's own id when null) and keeps the engine alias in step with it. */
    private fun showSteps(player: Player, steps: gg.rsmod.game.model.entity.GameObject, shown: Int?) {
        val key = gg.rsmod.game.model.attr.locAliasKey(steps.tile, steps.id)
        val aliases = player.attr[gg.rsmod.game.model.attr.LOC_ALIAS_ATTR]
        if (shown == null && aliases?.containsKey(key) != true) return
        if (shown == null) aliases!!.remove(key)
        else (aliases ?: HashMap<Long, Int>().also { player.attr[gg.rsmod.game.model.attr.LOC_ALIAS_ATTR] = it })[key] = shown
        // Sent 2 ticks later: right after a teleport into the raid the client first rebuilds the region (which sends the world's 29778).
        player.world.queue {
            wait(2)
            sendSteps(player, steps, shown)
        }
    }

    private fun sendSteps(player: Player, steps: gg.rsmod.game.model.entity.GameObject, shown: Int?) {
        val base = player.lastKnownRegionBase ?: return
        if (!player.isOnline || !player.tile.isWithinRadius(steps.tile, 32)) return
        val local = base.toLocal(steps.tile)
        val settings = (steps.type shl 2) or (steps.rot and 3)
        val hash = (steps.tile.chunkOffsetX shl 4) or (steps.tile.chunkOffsetZ and 0x7)
        player.write(gg.rsmod.game.message.impl.UpdateZonePartialFollowsMessage(local.x shr 3, local.z shr 3, steps.tile.height))
        player.write(gg.rsmod.game.message.impl.LocAddChangeMessage(shown ?: steps.id, settings, hash))
    }

    /**
     * 'Reload' (the leader, before the start): Wiki (update) 'allowing the raid leader to generate a new layout and boot anyone in the
     * current instance'. Everyone else is put out (they may come back in through the entrance - the party stays), the leader gets a fresh
     * raid of a new random layout.
     */
    fun reload(leader: Player, raid: CoxRaid) {
        if (raid.started || leader.username.lowercase() != leaderOf(raid)) return
        raid.world.players.entries.filterNotNull().filter { it !== leader && raid.map.area.contains(it.tile) }.forEach { leave(it) }
        leave(leader)
        enter(leader)
    }

    /**
     * The entrance steps (tsps 'The entrance': you need a party; you can't enter a started raid; the raid is built on the first entry).
     * 78legacy has no raid chat-channel (besluit 5a, ToA pattern): without a party the steps make one of you alone (advies, COX.txt 21:3x).
     */
    fun enter(player: Player, layout: CoxLayout = CoxLayouts.normal(Random.Default)): CoxRaid? {
        val party = CoxParties.of(player.username) ?: CoxParties.create(player.username) ?: return null
        val existing = byId(party.raidId)
        if (existing != null) {
            if (existing.started) {
                // tsps 'Starting and stages': "No-one may join the party after the raid begins."
                player.message("No-one may join the party after the raid begins.")
                return null
            }
            player.teleportTo(spawnTile(existing))
            CoxMusic.floor(player, 0)
            CoxPartyTab.open(player, existing)
            existing.world.players.entries.filterNotNull().filter { it !== player && existing.map.area.contains(it.tile) }
                .forEach { CoxPartyTab.update(it, existing) }
            return existing
        }
        val raid = start(player, layout) ?: return null
        party.raidId = raid.id
        // tsps: 'Settings can't change once the party has entered' - the advert goes with it (ONZEKER: no capture of the board then).
        party.advertisedUntil = -1
        return raid
    }

    /** The raid's leader: its party's leader (a leader who left hands it on, CoxParty.leave), else whoever built it. */
    fun leaderOf(raid: CoxRaid): String =
        CoxParties.all().firstOrNull { it.raidId == raid.id }?.leader ?: raid.leader.lowercase()

    /** The leader's name as written (the online player's own spelling), for messages. */
    fun leaderName(raid: CoxRaid): String {
        val key = leaderOf(raid)
        return raid.world.players.entries.filterNotNull().firstOrNull { it.username.lowercase() == key }?.username
            ?: if (raid.leader.lowercase() == key) raid.leader else key
    }

    /** The leader starts the raid ('Start raid' in the party tab; ::coxbegin on the test server). */
    fun begin(player: Player, raid: CoxRaid): Boolean {
        if (raid.started || player.username.lowercase() != leaderOf(raid)) return false
        raid.started = true
        raid.progress = 1
        raid.startCycle = raid.world.currentCycle
        syncReload(raid)
        // tsps r123 (rsprox): the raid's announcements are friendschatnotification messages (type 11).
        raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.forEach {
            it.message("The raid has begun!", gg.rsmod.plugins.api.ChatMessageType.FRIENDS_CHAT_NOTIFICATION)
            raid.points.putIfAbsent(it.username.lowercase(), 0)
            CoxPointsOverlay.open(it)
        }
        CoxPartyTab.updateAll(raid)
        // tsps rsprox: 'The first room's bank chest shuts as the raid begins (47420 to 47419).'
        val open = CoxIds.loc(47420)
        val shut = CoxIds.loc(47419)
        if (open != null && shut != null) replaceInRoom(raid, 0, raid.layout.floors[0][0], open, shut)
        // The rooms' encounters (tsps 'The raid has started: spawn what the room holds').
        gg.rsmod.plugins.content.activity.cox.rooms.CoxRooms.load(raid)
        return true
    }

    /** Swaps every loc [from] in [room] of floor [floor] for [to] (same shape and rotation). */
    fun replaceInRoom(raid: CoxRaid, floor: Int, room: CoxLaidRoom, from: Int, to: Int): List<gg.rsmod.game.model.entity.DynamicObject> {
        val placed = ArrayList<gg.rsmod.game.model.entity.DynamicObject>()
        val world = raid.world
        val x0 = raid.map.area.bottomLeftX + room.gridX * CoxMap.ROOM_TILES
        val z0 = raid.map.area.bottomLeftZ + room.gridZ * CoxMap.ROOM_TILES
        val h = CoxLayouts.FLOOR_PLANES[floor]
        for (cx in 0 until CoxMap.ROOM_TILES step 8) for (cz in 0 until CoxMap.ROOM_TILES step 8) {
            val chunk = world.chunks.get(Tile(x0 + cx, z0 + cz, h), createIfNeeded = false) ?: continue
            chunk.getEntities<gg.rsmod.game.model.entity.GameObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                .filter { it.id == from && it.tile.height == h }
                .forEach { o ->
                    world.remove(o)
                    val d = gg.rsmod.game.model.entity.DynamicObject(to, o.type, o.rot, o.tile)
                    world.spawn(d)
                    placed += d
                }
        }
        return placed
    }

    /**
     * OSRS Wiki Chambers of Xeric 'Reward system': 'If a player dies in a raid, they will lose 40% of the points that they had before their
     * death. If the player dies with none, or extremely few, personal points (less than 5% of the total points before death), the team's
     * overall points are reduced instead by 5%.' The team's 5% is taken from every raider's points.
     */
    fun deathPenalty(raid: CoxRaid, username: String) {
        val key = username.lowercase()
        val mine = raid.points[key] ?: 0
        val total = raid.totalPoints()
        if (total > 0 && mine * 20 < total) {
            raid.points.replaceAll { _, v -> v - v / 20 }
        } else {
            raid.points[key] = mine - mine * 2 / 5
        }
    }

    /** Every tick (cox_raid.plugin.kts): the points overlay of every raider (texts only on a change). */
    fun tick(world: World) {
        raids.values.filter { it.world === world && !it.closed && it.started }.forEach { raid ->
            world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.forEach { CoxPointsOverlay.update(it, raid) }
            gg.rsmod.plugins.content.activity.cox.rooms.CoxRooms.tick(raid)
        }
        CoxPotions.tick(world)
    }

    /** Is [tile] in the first room of [raid] (the start room, floor 1 room 0)? */
    fun inStartRoom(raid: CoxRaid, tile: Tile): Boolean = CoxMap.roomAt(raid.map, raid.layout, tile)?.let { (f, room) ->
        f == 0 && room === raid.layout.floors[0][0]
    } ?: false

    /**
     * Where a passage (RAIDS_DOORWAY 29789, op 'Enter') puts [from]: just past the passage's footprint on the opposite side, along the axis
     * the player stands off it (the passages sit on a room's edge: crossing one changes room).
     */
    fun passageExit(from: Tile, obj: Tile, width: Int, length: Int): Tile {
        val cx2 = obj.x * 2 + width - 1
        val cz2 = obj.z * 2 + length - 1
        val dx2 = from.x * 2 - cx2
        val dz2 = from.z * 2 - cz2
        return if (kotlin.math.abs(dx2) >= kotlin.math.abs(dz2)) {
            val z = from.z.coerceIn(obj.z, obj.z + length - 1)
            if (dx2 < 0) Tile(obj.x + width, z, from.height) else Tile(obj.x - 1, z, from.height)
        } else {
            val x = from.x.coerceIn(obj.x, obj.x + width - 1)
            if (dz2 < 0) Tile(x, obj.z + length, from.height) else Tile(x, obj.z - 1, from.height)
        }
    }

    /** tsps r115 (rsprox): 'Landings on ropes and holes move to the nearest open tile'. Ring by ring up to [r]. */
    fun nearestOpen(world: World, t: Tile, r: Int = 6): Tile {
        // 16-11: first a tile open in ALL four directions - a half-wall edge tile (open one way) left a raider stuck on the Vespula ledge
        // ('I can't reach that!'); only when none is near, any tile one can step off.
        fun fully(c: Tile) = gg.rsmod.game.model.Direction.NESW.all { d -> world.collision.canTraverse(tile = c, direction = d, projectile = false, water = false) }
        fun open(c: Tile) = gg.rsmod.game.model.Direction.NESW.any { d -> world.collision.canTraverse(tile = c, direction = d, projectile = false, water = false) }
        for (test in listOf(::fully, ::open)) {
            if (test(t)) return t
            for (d in 1..r) for (dx in -d..d) for (dz in -d..d) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != d) continue
                val c = t.transform(dx, dz)
                if (test(c)) return c
            }
        }
        return t
    }

    /** Where the hole of floor [floor]'s end room leads: the next floor's start room (its rope tile) or, from the last floor, the Olm. */
    fun holeLanding(raid: CoxRaid, floor: Int): Tile {
        val next = floor + 1
        val tile = if (next < raid.layout.floors.size) {
            val start = raid.layout.floors[next][0]
            CoxMap.roomTile(raid.map, next, start, CoxMap.FLOOR_START_ROPE[start.variant])
        } else {
            CoxMap.olmTile(raid.map, CoxMap.OLM_ENTRANCE_SOURCE)
        }
        return nearestOpen(raid.world, tile)
    }

    /** Where floor [floor]'s start rope leads: the floor above's end room, at the bottom of its hole. */
    fun ropeLanding(raid: CoxRaid, floor: Int): Tile? {
        val above = floor - 1
        if (above < 0) return null
        val end = raid.layout.floors[above].last()
        val source = CoxMap.FLOOR_END_HOLE[end.variant] ?: return null
        return nearestOpen(raid.world, CoxMap.roomTile(raid.map, above, end, source))
    }

    /**
     * Where a raider who dies respawns (OSRS Wiki Chambers of Xeric/Strategies: 'dying on the first floor places players back at its
     * beginning, while having entered the second floor will move it to the end of the first floor, and having entered the final floor
     * likewise moves the respawn point to the end of the second floor'): progress 0/1 -> the first room's spawn; floor n reached -> the
     * end room of floor n-1 (at the bottom of its hole); the Olm (progress 4) -> the last floor's end room.
     */
    fun respawnTile(raid: CoxRaid): Tile {
        val floors = raid.layout.floors
        val reachedFloor = when {
            raid.progress >= 4 -> floors.size
            raid.progress >= 2 -> raid.progress - 1
            else -> 0
        }
        if (reachedFloor <= 0) return spawnTile(raid)
        val endFloor = reachedFloor - 1
        val end = floors[endFloor].last()
        val source = CoxMap.FLOOR_END_HOLE[end.variant] ?: CoxMap.FLOOR_END_HOLE.filterNotNull().first()
        return nearestOpen(raid.world, CoxMap.roomTile(raid.map, endFloor, end, source))
    }

    /**
     * Raid-only items (Wiki: 'All raid-unique items (potions, planks, food) are dropped on death'; tsps: 'Raid-only items drop where they
     * fell'): the raid supplies of the 'cox-items' batch - wood, vials, bait, fish/bats, keystone, grubs, Vespula herb, herbs/seeds/
     * ingredients, every raid potion, plank, storage kits. Not the rewards (dark journal, relic, tablet, torn scroll, talisman, books).
     */
    val RAID_ONLY_OSRS: Set<Int> = (listOf(20799, 20800, 20801) + (20853..20885) + listOf(20892) + (20901..20996) + listOf(21036) +
        (21037..21042)).toSet()

    fun raidOnlyLocal(): Set<Int> = RAID_ONLY_OSRS.mapNotNull { CoxIds.item(it) }.toSet()

    /** 'm:ss' of the ticks since the raid began (tsps: durations are ticks since "The raid has begun!"). */
    fun duration(raid: CoxRaid): String {
        val seconds = if (raid.startCycle < 0) 0 else ((raid.world.currentCycle - raid.startCycle) * 6 / 10)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    fun spawnTile(raid: CoxRaid): Tile {
        val first = raid.layout.floors[0][0]
        return CoxMap.roomTile(raid.map, 0, first, CoxMap.RAID_START_SPAWN[first.variant])
    }

    /** Out of the chambers (exit steps / test command); the map goes once nobody of it is left inside. */
    fun leave(player: Player) {
        val raid = raidAt(player.tile)
        if (raid != null) dropRaidItems(player, player.tile)
        player.attr[gg.rsmod.game.model.attr.LOC_ALIAS_ATTR]?.clear()   // no 'Reload' steps alias outside (only CoX uses it)
        player.moveTo(OUTSIDE)
        CoxPartyTab.close(player)
        CoxPointsOverlay.close(player)
        CoxRewards.dropUnclaimed(player, player.world)
        CoxPotions.clear(player)
        if (raid != null) { releaseIfEmpty(raid); if (!raid.closed) syncReload(raid) }   // a leader who left hands 'Reload' on
    }

    /** Called every few ticks (cox_raid.plugin.kts): logouts and leavers free the instance. */
    fun releaseEmpty(world: World) {
        raids.values.filter { it.world === world }.forEach { releaseIfEmpty(it) }
    }

    private fun releaseIfEmpty(raid: CoxRaid) {
        if (raid.closed) return
        if (raid.world.players.any { it.isOnline && raid.map.area.contains(it.tile) }) return
        raid.closed = true
        raids.remove(raid.map)
        reloadSteps.remove(raid.id)
        CoxParties.all().filter { it.raidId == raid.id }.forEach { it.raidId = -1 }
        CoxChest.forget(raid)
        CoxStorage.forget(raid)
        gg.rsmod.plugins.content.activity.cox.rooms.CoxSupplies.forget(raid.id)
        gg.rsmod.plugins.content.activity.cox.rooms.CoxRooms.unload(raid)
        runCatching { raid.world.instanceAllocator.release(raid.world, raid.map) }.onFailure { logger.error(it) { "CoX release ${raid.id}" } }
    }
}

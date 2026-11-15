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

    fun raidAt(tile: Tile): CoxRaid? = raids.values.firstOrNull { !it.closed && it.map.area.contains(tile) }

    fun byId(id: Long): CoxRaid? = raids.values.firstOrNull { !it.closed && it.id == id }

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
        player.teleportTo(spawnTile(raid))
        CoxPartyTab.open(player, raid)
        return raid
    }

    /** The leader starts the raid ('Start raid' in the party tab; ::coxbegin on the test server). */
    fun begin(player: Player, raid: CoxRaid): Boolean {
        if (raid.started || player.username != raid.leader) return false
        raid.started = true
        raid.progress = 1
        raid.startCycle = raid.world.currentCycle
        // tsps r123 (rsprox): the raid's announcements are friendschatnotification messages (type 11).
        raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.forEach {
            it.message("The raid has begun!", gg.rsmod.plugins.api.ChatMessageType.FRIENDS_CHAT_NOTIFICATION)
            raid.points.putIfAbsent(it.username.lowercase(), 0)
            CoxPointsOverlay.open(it)
        }
        CoxPartyTab.updateAll(raid)
        return true
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
        }
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
        fun open(c: Tile) = gg.rsmod.game.model.Direction.NESW.any { d -> world.collision.canTraverse(tile = c, direction = d, projectile = false, water = false) }
        if (open(t)) return t
        for (d in 1..r) for (dx in -d..d) for (dz in -d..d) {
            if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != d) continue
            val c = t.transform(dx, dz)
            if (open(c)) return c
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
        player.moveTo(OUTSIDE)
        CoxPartyTab.close(player)
        CoxPointsOverlay.close(player)
        if (raid != null) releaseIfEmpty(raid)
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
        runCatching { raid.world.instanceAllocator.release(raid.world, raid.map) }.onFailure { logger.error(it) { "CoX release ${raid.id}" } }
    }
}

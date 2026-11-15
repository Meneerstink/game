package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** One running Chambers of Xeric instance (fase 5, first slice: solo entry into a generated layout; party/rooms follow). */
class CoxRaid(val id: Long, val layout: CoxLayout, val map: InstancedMap, val world: World, val leader: String) {
    @Volatile var closed = false
    /** tsps r118: the first room's passage stays shut until the leader starts the raid (party tab; ::coxbegin on the test server). */
    @Volatile var started = false
    var startCycle = -1
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
        return raid
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

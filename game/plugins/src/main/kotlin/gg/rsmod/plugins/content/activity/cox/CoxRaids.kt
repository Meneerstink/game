package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** One running Chambers of Xeric instance (fase 5, first slice: solo entry into a generated layout; party/rooms follow). */
class CoxRaid(val id: Long, val layout: CoxLayout, val map: InstancedMap, val world: World) {
    @Volatile var closed = false
}

/**
 * CoX raids (dossier COX.txt fase 5; same lifetime pattern as ToaLobby: the map is MANAGED_LIFETIME and released here once nobody of the
 * raid is inside, so [isRaidTile] never answers for a stale map).
 * OSRS Wiki Chambers of Xeric: 'the leader should enter the chambers, where a starting room is located'; the entrance is the steps
 * RAIDS_ENTRANCE_STEPS 29777 on Mount Quidamortem (op 'Enter', cache 2686), placed at 1232,3573 (imported in place, testserver 15-11).
 * The starting room's spawn per version (west / straight / east) = CoxMap.RAID_START_SPAWN (tsps BOUND.RAID_START).
 */
object CoxRaids : KLogging() {
    /** The tile in front of the entrance steps (walked to from fairy ring BLS on the test server 15-11: 1233,3571 is reachable). */
    val OUTSIDE = Tile(1233, 3571, 0)

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
        val raid = CoxRaid(nextId++, layout, map, world)
        raids[map] = raid
        logger.info { "[cox] raid ${raid.id} ${player.username} base=${map.area.bottomLeftX},${map.area.bottomLeftZ} pattern=${layout.pattern} floors=${layout.floors.map { f -> f.map { "${it.room}@${it.gridX},${it.gridZ}/${it.entry}/v${it.variant}" } }}" }
        player.teleportTo(spawnTile(raid))
        return raid
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

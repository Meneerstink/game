package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap

/** One world-thread raid controller per party. Room implementations receive this instance, never a global room. */
class ToaRaid(val id: Long, val roster: List<PlayerUID>, val settings: ToaRaidSettings, val map: InstancedMap, val world: World) {
    val remaining = roster.toMutableSet()
    val progress = ToaRaidProgress(settings, ::onRoomEvent)
    private val rooms = hashMapOf<Int, Pair<ToaRoom, ToaRoomScope>>()

    fun contains(player: Player): Boolean = player.uid in remaining && map.area.contains(player.tile)
    fun players(): List<Player> = world.players.entries.filterNotNull().filter { it.uid in remaining && contains(it) }

    fun enterChallenge(player: Player, path: ToaPath): Boolean {
        if (!contains(player) || player.isDead()) return false
        progress.challenge(path, world.currentCycle) ?: return false
        player.teleportTo(ToaMap.instance(map, path.challengeEntry))
        return true
    }

    fun enterBoss(player: Player, path: ToaPath): Boolean {
        if (!contains(player) || player.isDead()) return false
        progress.boss(path) ?: return false
        player.teleportTo(ToaMap.instance(map, path.bossEntry))
        return true
    }

    fun enterWardens(player: Player): Boolean {
        if (!contains(player) || player.isDead()) return false
        progress.wardens() ?: return false
        player.teleportTo(ToaMap.instance(map, WARDENS_ENTRY))
        return true
    }

    fun crystal(player: Player, path: ToaPath?): Boolean {
        if (!contains(player) || player.isDead()) return false
        if (path == null) {
            if (!progress.finished) return false
            player.teleportTo(ToaMap.instance(map, REWARD_ENTRY))
        } else {
            if (path !in progress.completedPaths) return false
            player.teleportTo(ToaMap.instance(map, ToaMap.NEXUS))
        }
        return true
    }

    fun scope(run: ToaRoomRun): ToaRoomScope? = rooms[run.generation]?.second

    private fun onRoomEvent(event: ToaRoomEvent, run: ToaRoomRun) {
        if (event == ToaRoomEvent.START) {
            val scope = ToaRoomScope(this, run)
            val room = ToaRooms.create(this, run.key)
            rooms[run.generation] = room to scope
            room.start(run)
            return
        }
        val (room, scope) = rooms[run.generation] ?: return
        when (event) {
            ToaRoomEvent.CLEAR -> { room.clear(run); scope.close(); rooms.remove(run.generation) }
            ToaRoomEvent.WIPE -> room.wipe(run)
            ToaRoomEvent.RESET -> { room.reset(run); scope.close(); rooms.remove(run.generation) }
            else -> Unit
        }
    }

    fun close() {
        rooms.values.toList().forEach { it.second.close() }
        rooms.clear()
    }

    companion object {
        // 40-loc-placements: Wardens lower Exit3806,5178,p1; vault Entry3679,5171,p0.
        val WARDENS_ENTRY = Tile(3808, 5176, 1)
        val REWARD_ENTRY = Tile(3679, 5169)
    }
}

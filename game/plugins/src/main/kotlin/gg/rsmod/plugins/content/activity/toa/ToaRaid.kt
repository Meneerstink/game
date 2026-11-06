package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.content.combat.removeCombatTarget
import java.util.UUID

/** One world-thread raid controller per party. Room implementations receive this instance, never a global room. */
class ToaRaid(val id: Long, val roster: List<PlayerUID>, val settings: ToaRaidSettings, val map: InstancedMap, val world: World) {
    val rewardToken: String = UUID.randomUUID().toString()
    var closed = false
        private set
    val memberNames = hashMapOf<PlayerUID, String>()
    val remaining = roster.toMutableSet()
    val offline = mutableSetOf<PlayerUID>()
    val participants = ToaParticipants()
    val checkpoints = ToaCheckpoints()
    val supplies = ToaSupplies(this)
    val progress = ToaRaidProgress(settings, ::onRoomEvent)
    private val rooms = hashMapOf<Int, Pair<ToaRoom, ToaRoomScope>>()
    private val started = mutableSetOf<Int>()
    private val dying = mutableSetOf<PlayerUID>()
    private var retry: ToaRoomKey? = null
    private var scene: ToaScene? = null
    private val lastBarrier = hashMapOf<PlayerUID, Int>()
    var completion: ToaCompletion? = null
        private set

    fun prepare(members: List<Player> = players()) {
        check(scene == null && !closed)
        members.forEach { memberNames[it.uid] = it.username }
        scene = ToaScene(this).also { it.refresh() }
    }
    fun openChest(player: Player, obj: gg.rsmod.game.model.entity.GameObject): Boolean = scene?.openChest(player, obj) == true

    fun contains(player: Player): Boolean = !closed && player.uid in remaining && map.area.contains(player.tile)
    fun players(): List<Player> = world.players.entries.filterNotNull().filter { it.uid in remaining && contains(it) }
    /** Boss and puzzle cards must target this live view, excluding spectators and disconnected entrants. */
    fun activePlayers(): List<Player> = progress.current?.let { run ->
        players().filter { !it.isDead() && participants.alive(run, it.uid) && inRoom(it, run.key) }
    } ?: emptyList()

    fun canFight(player: Player): Boolean = player in activePlayers()

    private fun inRoom(player: Player, key: ToaRoomKey): Boolean {
        val source = ToaMap.source(map, player.tile) ?: return false
        return if (key.wardens) source.regionId in WARDENS_REGIONS
        else source.regionId == (if (key.boss) key.path!!.bossEntry else key.path!!.challengeEntry).regionId
    }

    private fun enter(player: Player, run: ToaRoomRun, tile: Tile): Boolean {
        if (!participants.enter(run, player.uid)) return false
        checkpoints.forfeit(player.uid)
        scene?.refresh()
        player.teleportTo(ToaMap.instance(map, tile))
        if (run.key.boss) ToaRecovery.bossEntry(player)
        // Combat attachment sees the player in the correct instance/plane, never the previous room.
        if (started.add(run.generation)) rooms.getValue(run.generation).first.start(run)
        return true
    }

    fun enterChallenge(player: Player, path: ToaPath): Boolean {
        if (!contains(player) || player.isDead()) return false
        if (progress.failed || progress.finished || path in progress.completedPaths ||
            progress.activePath != null && progress.activePath != path) return false
        if (progress.challengeCleared(path)) {
            player.teleportTo(ToaMap.instance(map, path.challengeEntry))
            return true
        }
        val run = progress.challenge(path, world.currentCycle) ?: return false
        return enter(player, run, path.challengeEntry)
    }

    fun enterBoss(player: Player, path: ToaPath): Boolean {
        if (!contains(player) || player.isDead()) return false
        val run = progress.boss(path) ?: return false
        return enter(player, run, path.bossEntry)
    }

    fun enterWardens(player: Player): Boolean {
        if (!contains(player) || player.isDead()) return false
        val run = progress.wardens() ?: return false
        return enter(player, run, WARDENS_ENTRY)
    }

    /** 50-loc-defs45135 + 40-loc-placements: path barriers face east/west at rotations1/3. */
    fun passBarrier(player: Player, obj: gg.rsmod.game.model.entity.GameObject): Boolean {
        if (!contains(player) || player.isDead() || lastBarrier[player.uid] == world.currentCycle) return false
        val source = ToaMap.source(map, obj.tile) ?: return false
        val path = ToaPath.values().firstOrNull { it.challengeEntry.regionId == source.regionId } ?: return false
        val current = progress.current
        if (!progress.challengeCleared(path) && (current == null || current.key != ToaRoomKey(path, false) || !participants.alive(current, player.uid))) return false
        lastBarrier[player.uid] = world.currentCycle
        val across = if (obj.rot % 2 == 1) Tile(obj.tile.x + if (player.tile.x < obj.tile.x) 1 else -1, player.tile.z, player.tile.height)
            else Tile(player.tile.x, obj.tile.z + if (player.tile.z < obj.tile.z) 1 else -1, player.tile.height)
        player.teleportTo(across)
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

    fun deathStart(player: Player) {
        if (!dying.add(player.uid)) return
        lose(player.uid)
        ToaRaidEvents.playerDeath(this, player.uid)
    }

    private fun lose(uid: PlayerUID) {
        val run = progress.current ?: return
        if (run.status == ToaRoomStatus.RUNNING && participants.lose(run, uid) && participants.wiped(run))
            run.wipe(world.currentCycle)
    }

    fun disconnect(player: Player) { offline += player.uid; lose(player.uid) }
    fun remove(uid: PlayerUID) { lose(uid); remaining -= uid; offline -= uid; dying -= uid }

    fun reconnect(player: Player) {
        offline -= player.uid
        // 21-supplies-dood: returning to a new active encounter also counts as a death/spectator.
        progress.current?.takeIf { it.status == ToaRoomStatus.RUNNING }?.let { run ->
            if (!participants.participating(run, player.uid)) {
                participants.enter(run, player.uid)
                lose(player.uid)
            }
        }
        respawn(player)
    }

    fun respawn(player: Player) {
        dying -= player.uid
        ToaRecovery.full(player)
        val run = progress.current
        val destination = when {
            progress.finished -> REWARD_ENTRY
            run != null && run.status == ToaRoomStatus.RUNNING -> checkpoint(run.key)
            retry != null -> checkpoint(retry!!)
            else -> ToaMap.NEXUS
        }
        player.teleportTo(ToaMap.instance(map, destination))
    }

    /** Called once per world cycle, after death/clear callbacks for the preceding cycle have settled. */
    fun cycle() {
        if (closed) return
        players().forEach { ToaUi.refresh(it, this) }
        progress.current?.takeIf { it.status == ToaRoomStatus.RUNNING }?.let { run ->
            players().filter { participants.alive(run, it.uid) && (it.isDead() || !inRoom(it, run.key)) }
                .forEach { lose(it.uid) }
        }
        progress.cycle(world.currentCycle)
        val key = retry ?: return
        if (progress.failed || dying.isNotEmpty()) return
        val present = players().filter { it.uid !in offline && !it.isDead() }
        if (present.isEmpty()) return
        retry = null
        // Everybody is restored before the retry starts; the generation discards all old room callbacks.
        present.forEach { ToaRecovery.full(it) }
        val run = when {
            key.wardens -> progress.wardens()
            key.boss -> progress.boss(key.path!!)
            else -> progress.challenge(key.path!!, world.currentCycle)
        } ?: return
        val tile = if (key.wardens) WARDENS_ENTRY else if (key.boss) key.path!!.bossEntry else key.path!!.challengeEntry
        present.forEach { participants.enter(run, it.uid); it.teleportTo(ToaMap.instance(map, tile)) }
        if (started.add(run.generation)) rooms.getValue(run.generation).first.start(run)
    }

    private fun checkpoint(key: ToaRoomKey): Tile = key.path?.challengeEntry ?: ToaMap.NEXUS

    private fun onRoomEvent(event: ToaRoomEvent, run: ToaRoomRun) {
        if (event == ToaRoomEvent.START) {
            val scope = ToaRoomScope(this, run)
            val room = ToaRooms.create(this, run.key)
            rooms[run.generation] = room to scope
            participants.begin(run)
            scene?.refresh()
            return
        }
        val (room, scope) = rooms[run.generation] ?: return
        when (event) {
            ToaRoomEvent.CLEAR -> {
                room.clear(run); scope.close(); rooms.remove(run.generation)
                started -= run.generation
                participants.clear()
                stopTransientCombat()
                players().filter { !it.isDead() }.forEach {
                    if (run.key.boss) ToaRecovery.full(it) else ToaRecovery.challenge(it)
                }
                if (run.key.path != null && run.key.boss) checkpoints.pathsCleared(progress.completedPaths.size)
                scene?.refresh()
                ToaRaidEvents.roomCleared(this, run)
                if (run.key.wardens) {
                    val record = ToaCompletion(rewardToken, roster.toList(), settings,
                        progress.elapsedCycles(world.currentCycle), progress.failedAttempts)
                    completion = record
                    ToaRaidEvents.completed(this, record)
                }
            }
            ToaRoomEvent.WIPE -> room.wipe(run)
            ToaRoomEvent.RESET -> {
                room.reset(run); scope.close(); rooms.remove(run.generation); participants.clear()
                started -= run.generation
                stopTransientCombat()
                retry = run.key
            }
            else -> Unit
        }
    }

    fun close() {
        if (closed) return
        stopTransientCombat()
        closed = true
        rooms.values.toList().forEach { (room, scope) -> room.reset(scope.run); scope.close() }
        rooms.clear()
        started.clear()
        progress.close()
        participants.clear()
        retry = null
        scene?.close(); scene = null
    }

    private fun stopTransientCombat() {
        players().forEach { it.clearHits(); it.removeCombatTarget(); it.damageMap.reset() }
    }

    companion object {
        // 40-loc-placements: Wardens lower Exit3806,5178,p1; vault Entry3679,5171,p0.
        val WARDENS_ENTRY = Tile(3808, 5176, 1)
        val REWARD_ENTRY = Tile(3679, 5169)
        // 40-kaart-bron.tsv: Wardens lower/upper arena source squares.
        private val WARDENS_REGIONS = setOf(15184, 15696)
    }
}

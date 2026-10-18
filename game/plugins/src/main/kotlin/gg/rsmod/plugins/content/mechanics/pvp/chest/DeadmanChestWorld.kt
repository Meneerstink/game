package gg.rsmod.plugins.content.mechanics.pvp.chest

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.BossAreas
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachIds
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import java.io.File
import java.io.FileReader

/**
 * Local ids of the cache data the chest needs (dossier N101 gap 7). 0 = the cache step (OsrsLocImportTool batch for OSRS locs
 * 60246 / 60250 and OsrsNpcImportTool for the chest-version Zemouregal 15564, BOTH caches, servers stopped) is not applied yet:
 * [DeadmanChestWorld.enabled] is then false, nothing spawns and one PROBLEM line says why, so a half-applied cache never shows an
 * invisible chest. Tests set the ids to stand-in objects.
 */
object DeadmanChestIds {
    /** Closed chest, option 1 "Loot", examine "Takes a while to open." (OSRS 60246 / 60248). */
    @Volatile var closedLoc = 0

    /** Open chest, no options, examine "Took a while to open." (OSRS 60250). */
    @Volatile var openLoc = 0

    /** Zemouregal, Deadman's chest version (OSRS 15564, combat 322, 500 HP = 5000 lifepoints). */
    @Volatile var guardianNpc = 0
}

/**
 * The live chests of the world: timed spawn every 30 minutes (owner: like Annihilation), kill chests, the channel (opening) of every
 * player, the removal rules. All rules are in [DeadmanChest]; this object only moves them into the world. Nothing is saved: a restart
 * removes every chest (dossier gap 2).
 */
object DeadmanChestWorld {
    const val SPOTS_PATH = "./data/cfg/deadman/chest_spots.json"

    /** OSRS broadcast colour of "A Deadman's Chest has spawned at ...!" (team\prep\chest_spots.txt). */
    const val BROADCAST_COLOUR = "a53fff"

    class Spot(
        val name: String,
        val tile: Tile,
    )

    class Chest(
        val tile: Tile,
        val timed: Boolean,
        val name: String,
        val state: DeadmanChest.State,
        var obj: DynamicObject,
        val killer: String? = null,
    ) {
        var guardian: Npc? = null
        val summons = ArrayList<Npc>()
        var removeAt = -1
        var limiterKeys: List<String> = emptyList()
    }

    private class Channelling(
        val chest: Chest,
        val channel: DeadmanChest.Channel,
        var snapshot: Int,
        var combat: Int,
    )

    val chests: MutableList<Chest> = java.util.concurrent.CopyOnWriteArrayList()
    val limiter = DeadmanChest.KillLimiter()
    // Insertion order = the order the players clicked: with two completions on one tick the one who started first wins.
    private val channels = LinkedHashMap<Player, Channelling>()
    private var spotList: List<Spot>? = null
    private var warned = false

    /** Test hook: where the clock of the chest rules stands. */
    var cycleOverride: Int? = null

    private fun now(world: World): Int = cycleOverride ?: world.currentCycle

    fun enabled(world: World): Boolean {
        val id = DeadmanChestIds.closedLoc
        val ok = id > 0 && world.definitions.getNullable(ObjectDef::class.java, id) != null
        if (!ok && !warned) {
            warned = true
            Server.logger.warn("PROBLEM chest.off: Deadman's chest loc def missing (closedLoc={}) - the cache step (N101 gap 7) is not applied; no chest will spawn.", id)
        }
        return ok
    }

    fun reset() {
        chests.clear()
        limiter.clear()
        channels.clear()
        spotList = null
        warned = false
    }

    // ------------------------------------------------------------------------------------------------------------------ spots

    /** The wiki spots (`chest_spots.json`) that the rev-667 map can hold: usable tile and dangerous (outside every safe zone). */
    fun spots(world: World): List<Spot> =
        spotList ?: loadSpots(world).also { spotList = it }

    private fun loadSpots(
        world: World,
        file: File = File(SPOTS_PATH),
    ): List<Spot> {
        if (!file.exists()) return emptyList()
        val root = FileReader(file).use { Gson().fromJson(it, JsonObject::class.java) }
        val all =
            root["spots"].asJsonArray.map { e ->
                val o = e.asJsonObject
                val t = o["tile"].asJsonArray
                Spot(o["name"].asString, Tile(t[0].asInt, t[1].asInt, t[2].asInt))
            }
        val kept = all.filter { fits(world, it.tile) }
        Server.logger.info(
            "Deadman chest spots: {} usable and dangerous, {} dropped (safe zone or no usable 667 tile): {}",
            kept.size, all.size - kept.size, all.filter { it !in kept }.map { it.name },
        )
        return kept
    }

    /** A tile a chest may stand on: the map holds it, it is walkable, not in a safe zone, no loc of type 9..21, dangerous. */
    fun fits(
        world: World,
        tile: Tile,
    ): Boolean = DeadmanBreach.usable(world, tile) && AreaState.isDangerous(tile)

    // ------------------------------------------------------------------------------------------------------------------ spawn

    /** Spawns the timed chest at [at] (admin command) or a random spot; the previous unlooted timed chest is removed first. */
    fun spawnTimed(
        world: World,
        at: Spot? = null,
    ): Chest? {
        if (!enabled(world)) return null
        val spot = at ?: spots(world).randomOrNull()
        if (spot == null) {
            Server.logger.warn("PROBLEM chest.timed.nospot: no usable dangerous chest spot")
            return null
        }
        chests.filter { it.timed }.forEach { remove(world, it, "replaced by the next timed chest") }
        val chest = place(world, spot.tile, timed = true, name = spot.name, ticks = DeadmanChestConfig.TIMED_INTERVAL_TICKS, killer = null) ?: return null
        world.players.forEach { it.message("<col=$BROADCAST_COLOUR>A Deadman's Chest has spawned at ${spot.name}!</col>") }
        Server.logger.info("[chest] spawn timed at {} ({}) ", spot.tile, spot.name)
        return chest
    }

    /**
     * The npc-kill chest: chance, exclusions and caps (dossier gap 1). [random] returns a 0-based roll in `0 until bound`
     * (tests pass a forced one). Silent (no broadcast); a refused roll writes a trace line only.
     */
    fun onNpcKilled(
        world: World,
        killer: Player,
        npc: Npc,
        roll: (Int) -> Int = { world.random(it) },
    ): Chest? {
        if (!DeadmanChest.killCounts(DeadmanChest.killFactsOf(killer, npc))) return null
        if (roll(DeadmanChestConfig.KILL_CHANCE) != 0) return null
        if (!enabled(world)) return null
        val keys = limiterKeys(killer)
        val tile = killSpot(world, killer, npc.tile)
        if (tile == null) {
            Server.logger.info("[chest] kill chest refused: no spot near {} (chest.kill.nospot)", npc.tile)
            return null
        }
        if (!limiter.allowed(keys, killer.username.lowercase(), tile, now(world))) {
            Server.logger.info("[chest] kill chest refused for {}: cooldown / cap / distance (npc {})", killer.username, npc.id)
            return null
        }
        val chest = place(world, tile, timed = false, name = "a monster kill", ticks = DeadmanChestConfig.KILL_CHEST_TICKS, killer = killer.username.lowercase()) ?: return null
        chest.limiterKeys = keys
        limiter.spawned(keys, killer.username.lowercase(), tile, now(world))
        Server.logger.info("[chest] spawn kill at {} by {} (npc {}#{})", tile, killer.username, npc.id, npc.index)
        return chest
    }

    /** Account + address key + machine id: alts on one address share one cooldown (dossier gap 1, AC link; same sources as ValidPkKill). */
    fun limiterKeys(player: Player): List<String> {
        val keys = ArrayList<String>()
        keys += "acct:" + player.username.lowercase()
        val client = player as? gg.rsmod.game.model.entity.Client
        val uuid = client?.let { runCatching { it.uuid }.getOrNull() }?.takeIf { it.isNotBlank() }
        if (uuid != null) keys += "pc:$uuid"
        val address = (client?.channel?.remoteAddress() as? java.net.InetSocketAddress)?.address
        if (address != null && !address.isLoopbackAddress) keys += "ip:" + address.hostAddress
        return keys
    }

    /**
     * Where a kill chest stands: the npc's death tile when usable; outside a boss area; else (unusable tile or boss lair) the nearest
     * fitting tile within 8 tiles the KILLER can walk to (4-direction walk from the death tile). The boss cave-entrance spots of the
     * wiki are used when the data file has them near the lair (spots list); otherwise null = no chest.
     */
    fun killSpot(
        world: World,
        killer: Player,
        death: Tile,
    ): Tile? {
        val bossArea = BossAreas.isBossArea(world, death)
        if (!bossArea && fits(world, death)) return death
        val entrance = spots(world).filter { it.tile.height == death.height && it.tile.getDistance(death) <= 40 }.minByOrNull { it.tile.getDistance(death) }
        if (bossArea && entrance != null) return entrance.tile
        return reachable(world, death, 8).firstOrNull { fits(world, it) && !BossAreas.isBossArea(world, it) }
    }

    /** Tiles reachable from [start] by 4-direction walking within [radius], nearest first (start excluded). */
    fun reachable(
        world: World,
        start: Tile,
        radius: Int,
    ): List<Tile> {
        val seen = HashSet<Tile>()
        val out = ArrayList<Tile>()
        var frontier = listOf(Tile(start))
        seen += Tile(start)
        repeat(radius) {
            val next = ArrayList<Tile>()
            for (cur in frontier) {
                for (d in listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                    val t = Tile(cur.x + d.getDeltaX(), cur.z + d.getDeltaZ(), cur.height)
                    if (t in seen || !world.collision.canTraverse(cur, d, projectile = false, water = false)) continue
                    seen += t
                    next += t
                    out += t
                }
            }
            frontier = next
        }
        return out
    }

    private fun place(
        world: World,
        tile: Tile,
        timed: Boolean,
        name: String,
        ticks: Int,
        killer: String?,
    ): Chest? {
        val loc = DeadmanChestIds.closedLoc
        val obj = DynamicObject(loc, 10, 0, Tile(tile))
        moveBodiesAside(world, tile)
        world.spawn(obj)
        val chest = Chest(Tile(tile), timed, name, DeadmanChest.State(Tile(tile), timed, now(world) + ticks), obj, killer)
        chests += chest
        spawnGuardian(world, chest)
        return chest
    }

    /** A player standing on the chest footprint is moved to the nearest free walkable tile beside it - never pushed into a wall. */
    private fun moveBodiesAside(
        world: World,
        tile: Tile,
    ) {
        val onTile = playersWhere(world) { it.tile.x == tile.x && it.tile.z == tile.z && it.tile.height == tile.height }
        if (onTile.isEmpty()) return
        val free = reachable(world, tile, 4).filter { !world.collision.isClipped(it) && DeadmanBreach.freeOfBodies(world, it, 1) }
        onTile.forEachIndexed { i, p ->
            val dest = free.getOrNull(i) ?: return@forEachIndexed
            p.moveTo(dest)
        }
    }

    /** Zemouregal spawns with the chest (OSRS), once the npc is in the cache; he is NOT a breach npc (dossier gap 2). */
    private fun spawnGuardian(
        world: World,
        chest: Chest,
    ) {
        val id = DeadmanChestIds.guardianNpc
        if (id <= 0) return
        val around = reachable(world, chest.tile, 3).filter { fits(world, it) && DeadmanBreach.freeOfBodies(world, it, 1) }.firstOrNull() ?: return
        val npc = Npc(id, Tile(around), world)
        npc.respawnOverride = false
        npc.walkRadius = DeadmanBreach.ROAM_RADIUS
        npc.smartPathfinding = true
        npc.naivePathfinding = true
        npc.attr[DeadmanChest.GUARDIAN_ATTR] = true
        world.spawn(npc)
        chest.guardian = npc
    }

    // ------------------------------------------------------------------------------------------------------------------ removal

    fun remove(
        world: World,
        chest: Chest,
        reason: String,
    ) {
        if (!chests.remove(chest)) return
        world.remove(chest.obj)
        chest.guardian?.let { if (world.npcs.contains(it) && !it.isDead()) world.remove(it) }
        chest.summons.forEach { if (world.npcs.contains(it) && !it.isDead()) world.remove(it) }
        if (!chest.timed) limiter.removed(chest.tile)
        channels.entries.removeAll { it.value.chest === chest }
        Server.logger.info("[chest] removed at {}: {}", chest.tile, reason)
    }

    // ------------------------------------------------------------------------------------------------------------------ opening

    /** The "Loot" option: starts the channel when the requirements pass. */
    fun beginOpen(
        player: Player,
        at: Tile,
    ) {
        val world = player.world
        val chest = chests.firstOrNull { it.tile == at && !it.state.looted }
        if (chest == null) {
            player.message(DeadmanChest.MSG_LOOTED)
            return
        }
        val refusal = DeadmanChest.refusal(DeadmanChest.factsOf(player, chest.tile))
        if (refusal != null) {
            player.message(refusal)
            return
        }
        channels[player]?.let { if (it.chest === chest) return }
        channels[player] = Channelling(chest, DeadmanChest.Channel(DeadmanChest.channelTicks(world, chest.tile)), snapshot(player), combatValue(player))
        player.message("You start to open the chest. Stay still and do not fight or use your inventory.")
    }

    private fun combatValue(player: Player): Int = if (player.timers.has(ACTIVE_COMBAT_TIMER)) player.timers[ACTIVE_COMBAT_TIMER] else 0

    /** Every inventory / equipment action changes this (eat, drink, equip, drop, use). */
    private fun snapshot(player: Player): Int {
        var h = 1
        for (c in listOf(player.inventory, player.equipment)) {
            for (i in 0 until c.capacity) {
                val it = c[i]
                h = 31 * h + (it?.id ?: -1) * 7 + (it?.amount ?: 0)
            }
        }
        return h
    }

    /** One game tick of every channel + the chest lifetimes. */
    fun tick(world: World) {
        val cycle = now(world)
        for (chest in chests.toList()) {
            if (chest.state.looted) {
                if (chest.removeAt in 0..cycle) remove(world, chest, "looted, open state over")
            } else if (cycle >= chest.state.expiresAt) {
                remove(world, chest, "expired unlooted")
            } else {
                trackSummons(world, chest)
            }
        }
        for ((player, ch) in channels.entries.toList()) {
            val chest = ch.chest
            if (!chests.contains(chest)) {
                channels.remove(player)
                continue
            }
            if (chest.state.looted) {
                channels.remove(player)
                if (chest.state.opener != player.username.lowercase()) player.message(DeadmanChest.MSG_LOOTED)
                continue
            }
            val gone = !world.players.contains(player) || player.isDead()
            val snap = snapshot(player)
            val combatNow = combatValue(player)
            // A fight resets the channel: a hit taken or dealt re-arms the combat timer (value goes UP); an attack in progress.
            val fought = combatNow > ch.combat || player.getCombatTarget() != null
            val interrupted = gone || snap != ch.snapshot || fought
            ch.snapshot = snap
            ch.combat = combatNow
            val facts = if (gone) DeadmanChest.Facts(false, 0, false, false) else DeadmanChest.factsOf(player, chest.tile)
            val result = ch.channel.tick(interrupted, facts)
            when (result) {
                DeadmanChest.Channel.Result.RUNNING -> Unit
                DeadmanChest.Channel.Result.STOPPED -> {
                    channels.remove(player)
                    if (!gone) player.message(DeadmanChest.refusal(facts) ?: DeadmanChest.MSG_INTERRUPTED)
                }
                DeadmanChest.Channel.Result.DONE -> {
                    channels.remove(player)
                    complete(world, player, chest)
                }
            }
        }
    }

    private fun complete(
        world: World,
        player: Player,
        chest: Chest,
    ) {
        // First completer wins; the flag is set BEFORE the reward is made (dupe guard).
        if (!chest.state.tryLoot(player.username.lowercase())) {
            player.message(DeadmanChest.MSG_LOOTED)
            return
        }
        val reward = DeadmanChest.giveReward(player, world.random.let { java.util.Random(it.nextLong()) })
        Server.logger.info(
            "[chest] reward {} to {} at {} ({}): {}", if (chest.timed) "timed" else "kill", player.username, chest.tile, chest.name,
            when (reward) {
                is DeadmanChestLoot.Reward.Item -> "item ${reward.item} x${reward.amount}"
                is DeadmanChestLoot.Reward.Pet -> "pet ${reward.pet} (item ${reward.item})"
            },
        )
        player.message("You open the chest and find something inside.")
        playersWhere(world) { p -> p !== player && channels[p]?.chest === chest }.forEach {
            channels.remove(it)
            it.message(DeadmanChest.MSG_LOOTED)
        }
        // Open state, then removal; the guardian and his summons go with the chest.
        val open = DeadmanChestIds.openLoc
        if (open > 0) {
            world.remove(chest.obj)
            chest.obj = DynamicObject(open, 10, 0, Tile(chest.tile))
            world.spawn(chest.obj)
        }
        chest.removeAt = now(world) + DeadmanChestConfig.OPEN_STATE_TICKS
    }

    /** Zemouregal's summons next to a living guardian belong to his chest (removed with it). */
    private fun trackSummons(
        world: World,
        chest: Chest,
    ) {
        val g = chest.guardian ?: return
        if (!world.npcs.contains(g) || g.isDead()) return
        DeadmanBreach.live.forEach { n ->
            if (BreachMonsters.BY_ID[n.id]?.summon == true && n !in chest.summons && n.tile.getDistance(g.tile) <= 24) chest.summons += n
        }
    }

    /** The guardian dies: a tier 5 Archaic emblem for the top damage dealer only; no BreachLoot, no points, no chest roll. */
    fun onGuardianDeath(
        world: World,
        npc: Npc,
    ): GroundItem? {
        if (npc.attr[DeadmanChest.GUARDIAN_ATTR] != true) return null
        val top = npc.damageMap.getMostDamage() as? Player ?: return null  // players are Clients (EntityType.CLIENT), so no type filter
        if (BreachIds.ARCHAIC_EMBLEM_TIER_5 <= 0) return null
        val emblem = GroundItem(BreachIds.ARCHAIC_EMBLEM_TIER_5, 1, Tile(npc.tile), top)
        world.spawn(emblem)
        Server.logger.info("[chest] guardian killed by {}: tier 5 Archaic emblem", top.username)
        return emblem
    }

    /** True when a player is protected from fighting the chest guardian (same rule as breach monsters, owner 09-23). */
    fun isGuardian(npc: Npc): Boolean = npc.attr[DeadmanChest.GUARDIAN_ATTR] == true

    private fun playersWhere(
        world: World,
        test: (Player) -> Boolean,
    ): List<Player> = ArrayList<Player>().also { out -> world.players.forEach { if (test(it)) out += it } }

    fun channelCount(): Int = channels.size

    fun channelElapsed(player: Player): Int? = channels[player]?.channel?.elapsed

    fun start(world: World) {
        spotList = null
        enabled(world)
        world.queue {
            var sinceTimed = 0
            while (true) {
                wait(1)
                sinceTimed++
                if (sinceTimed >= DeadmanChestConfig.TIMED_INTERVAL_TICKS) {
                    sinceTimed = 0
                    spawnTimed(world)
                }
                tick(world)
            }
        }
    }
}

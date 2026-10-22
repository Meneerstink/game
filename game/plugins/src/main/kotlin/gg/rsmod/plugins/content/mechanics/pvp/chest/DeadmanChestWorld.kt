package gg.rsmod.plugins.content.mechanics.pvp.chest

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.NpcDef
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
 * Local ids written by DeadmanChestImportTool (N101 gap 7). 0 = the transaction for OSRS locs
 * 60246 (appearance) / 60248 (Loot) / 60250 (open) and guardian 15564 has not been applied to both caches:
 * [DeadmanChestWorld.enabled] is then false, nothing spawns and one PROBLEM line says why, so a half-applied cache never shows an
 * invisible chest. Tests set the ids to stand-in objects.
 */
object DeadmanChestIds {
    const val ASSETS_PATH = "./data/cfg/deadman/chest-assets.json"

    /** Written only after the dual-cache transaction verifies; ids are allocated by the importer, never guessed. */
    fun load() {
        val file = File(ASSETS_PATH)
        if (!file.exists()) return
        runCatching {
            val root = file.reader().use { Gson().fromJson(it, JsonObject::class.java) }
            val closed = root["closedLoc"].asInt
            val open = root["openLoc"].asInt
            val guardian = root["guardianNpc"].asInt
            val spawning = root["spawnLoc"].asInt
            val delay = root["spawnTicks"].asInt
            require(closed > 0 && open > 0 && guardian > 0 && spawning > 0 && delay > 0)
            closedLoc = closed
            openLoc = open
            guardianNpc = guardian
            spawnLoc = spawning
            spawnTicks = delay
        }.onFailure {
            closedLoc = 0
            openLoc = 0
            guardianNpc = 0
            spawnLoc = 0
            spawnTicks = 0
            Server.logger.warn("PROBLEM chest.assets: invalid chest asset configuration", it)
        }
    }
    /** Closed 4x4 chest with Loot (OSRS 60248); 60246 is the non-clickable appearance variant. */
    @Volatile var closedLoc = 0

    /** Open chest, no options, examine "Took a while to open." (OSRS 60250). */
    @Volatile var openLoc = 0

    /** Zemouregal, Deadman's chest version (OSRS 15564, combat 322, 500 HP = 5000 lifepoints). */
    @Volatile var guardianNpc = 0

    /** Source 60246 appearance animation (classic sequence 7823); closed clickable variant is 60248. */
    @Volatile var spawnLoc = 0
    @Volatile var spawnTicks = 0
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
        var removeAt = -1
        var closedAt = -1
        var limiterKeys: List<String> = emptyList()
    }

    private class Channelling(
        val chest: Chest,
        val channel: DeadmanChest.Channel,
        var snapshot: Int,
        var combat: Int,
        val actionRevision: Long,
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
        val closed = DeadmanChestIds.closedLoc
        val open = DeadmanChestIds.openLoc
        val guardian = DeadmanChestIds.guardianNpc
        val ok = closed > 0 && open > 0 && guardian > 0 &&
            world.definitions.getNullable(ObjectDef::class.java, closed) != null &&
            world.definitions.getNullable(ObjectDef::class.java, open) != null &&
            world.definitions.getNullable(NpcDef::class.java, guardian) != null &&
            world.plugins.allNpcCombatDefs().containsKey(guardian) &&
            (DeadmanChestIds.spawnLoc == 0 || world.definitions.getNullable(ObjectDef::class.java, DeadmanChestIds.spawnLoc) != null)
        if (!ok && !warned) {
            warned = true
            Server.logger.warn("PROBLEM chest.off: incomplete chest cache/combat configuration (closedLoc={}, openLoc={}, guardianNpc={}); no chest will spawn.", closed, open, guardian)
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
    ): Boolean = footprint(world, tile).all { DeadmanBreach.usable(world, it) && AreaState.isDangerous(it) }

    private fun dimensions(world: World): Pair<Int, Int> {
        val def = world.definitions.getNullable(ObjectDef::class.java, DeadmanChestIds.closedLoc)
        return (def?.width ?: 1).coerceAtLeast(1) to (def?.length ?: 1).coerceAtLeast(1)
    }

    fun footprint(world: World, anchor: Tile): List<Tile> {
        val (width, length) = dimensions(world)
        return (0 until width).flatMap { dx -> (0 until length).map { dz -> Tile(anchor.x + dx, anchor.z + dz, anchor.height) } }
    }

    fun inside(world: World, anchor: Tile, tile: Tile): Boolean {
        val (width, length) = dimensions(world)
        return tile.height == anchor.height && tile.x in anchor.x until anchor.x + width && tile.z in anchor.z until anchor.z + length
    }

    fun adjacent(world: World, anchor: Tile, tile: Tile): Boolean {
        val (width, length) = dimensions(world)
        return tile.height == anchor.height && tile.x in anchor.x - 1..anchor.x + width && tile.z in anchor.z - 1..anchor.z + length
    }

    private fun perimeter(world: World, anchor: Tile): List<Tile> {
        val (width, length) = dimensions(world)
        return (-1..width).flatMap { dx -> (-1..length).map { dz -> Tile(anchor.x + dx, anchor.z + dz, anchor.height) } }
            .filterNot { inside(world, anchor, it) }
    }

    // ------------------------------------------------------------------------------------------------------------------ spawn

    /** Spawns the timed chest at [at] (admin command) or a random spot; the previous unlooted timed chest is removed first. */
    fun spawnTimed(
        world: World,
        at: Spot? = null,
    ): Chest? {
        if (!enabled(world)) return null
        fun available(tile: Tile): Boolean =
            (chests.any { it.timed && it.tile == tile } || fits(world, tile)) && spaced(tile, replacingTimed = true)
        val spot = at?.takeIf { available(it.tile) }
            ?: if (at == null) spots(world).filter { available(it.tile) }.randomOrNull() else null
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
        val tile = killSpot(world, killer, npc.tile, breachKill = DeadmanBreach.isBreachNpc(npc))
        if (tile == null) {
            Server.logger.info("[chest] kill chest refused: no spot near {} (chest.kill.nospot)", npc.tile)
            return null
        }
        if (!spaced(tile) || !limiter.allowed(keys, killer.username.lowercase(), tile, now(world))) {
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
     * fitting tile within 8 tiles which the KILLER can walk to. Fixed lairs use explicit donor exit mappings, including remote
     * ladders/other planes. A breach boss in an ordinary field must not turn that field into a boss cave for chest placement.
     */
    fun killSpot(
        world: World,
        killer: Player,
        death: Tile,
        breachKill: Boolean = false,
    ): Tile? {
        val bossArea = BossAreas.isBossArea(world, death)
        if (bossArea) {
            bossEntrance(world, death)?.let { return it }
        }
        // N130 (2026-10-22): only a real lair (a region with a boss_exits mapping) keeps the chest out. A boss that roams an open
        // Wilderness region (Chaos Elemental, Bork's surface, Barrelchest, Giant Mole's field) made the WHOLE 64x64 region refuse
        // every chest; OSRS only moves it out of the boss room itself.
        val avoidBossArea = (!breachKill || BossAreas.wasBossAreaAtBoot(death)) && death.regionId in lairRegions()
        if (killer.tile.height != death.height || killer.tile.getDistance(death) > 32) return null
        // Start at the KILLER, not at the victim: walkable islands separated by walls are not reachable.
        val reachable = (listOf(Tile(killer.tile)) + reachable(world, killer.tile, killer.tile.getDistance(death) * 2 + 16)).toSet()
        return (listOf(death) + reachable(world, death, 8)).firstOrNull {
            it in reachable && fits(world, it) && (!avoidBossArea || !BossAreas.isBossArea(world, it))
        }
    }

    /** Spacing is against ALL active chests, including the short open state. Replacing the timed chest may ignore that one. */
    private fun spaced(tile: Tile, replacingTimed: Boolean = false): Boolean = chests.none {
        !(replacingTimed && it.timed) && it.tile.height == tile.height &&
            it.tile.getDistance(tile) < DeadmanChestConfig.MIN_CHEST_DISTANCE
    }

    /** Explicit lair-to-exit mappings, including stairs to another map/plane; never a nearby unrelated timed spot. */
    /** N130: the regions [bossEntrance] maps to a lair exit (boss_exits in the spot file) - the only true boss rooms. */
    @Volatile
    private var lairRegionCache: Set<Int>? = null

    fun lairRegions(): Set<Int> =
        lairRegionCache ?: run {
            val root = File(SPOTS_PATH).takeIf { it.exists() }?.reader()?.use { Gson().fromJson(it, JsonObject::class.java) }
            val regions = root?.getAsJsonArray("boss_exits")?.flatMap { e -> e.asJsonObject["regions"].asJsonArray.map { it.asInt } }?.toSet() ?: emptySet()
            regions.also { lairRegionCache = it }
        }

    private fun bossEntrance(world: World, death: Tile): Tile? {
        val root = File(SPOTS_PATH).takeIf { it.exists() }?.reader()?.use { Gson().fromJson(it, JsonObject::class.java) } ?: return null
        val exits = root.getAsJsonArray("boss_exits") ?: return null
        for (entry in exits) {
            val obj = entry.asJsonObject
            if (obj["regions"].asJsonArray.none { it.asInt == death.regionId }) continue
            val a = obj["tile"].asJsonArray
            val exit = Tile(a[0].asInt, a[1].asInt, a[2].asInt)
            // The donor exit may be the ladder tile itself: choose free ground accessible FROM that exit.
            val (width, length) = dimensions(world)
            val radius = 8 + 2 * maxOf(width, length)
            return (listOf(exit) + reachable(world, exit, radius)).firstOrNull { fits(world, it) }
        }
        return null
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
        if (!fits(world, tile) || !spaced(tile)) return null
        val loc = DeadmanChestIds.spawnLoc.takeIf { it > 0 } ?: DeadmanChestIds.closedLoc
        val obj = DynamicObject(loc, 10, 0, Tile(tile))
        if (!moveBodiesAside(world, tile)) return null
        world.spawn(obj)
        val chest = Chest(Tile(tile), timed, name, DeadmanChest.State(Tile(tile), timed, now(world) + ticks), obj, killer)
        if (DeadmanChestIds.spawnLoc > 0) chest.closedAt = now(world) + DeadmanChestIds.spawnTicks
        chests += chest
        if (!spawnGuardian(world, chest)) {
            remove(world, chest, "no usable guardian position")
            return null
        }
        return chest
    }

    /** A player standing on the chest footprint is moved to the nearest free walkable tile beside it - never pushed into a wall. */
    private fun moveBodiesAside(
        world: World,
        tile: Tile,
    ): Boolean {
        val onTile = playersWhere(world) { inside(world, tile, it.tile) }
        val chosen = HashSet<Tile>()
        val destinations = onTile.map { p ->
            reachable(world, p.tile, 8).firstOrNull {
                !inside(world, tile, it) && it !in chosen && !world.collision.isClipped(it) && DeadmanBreach.freeOfBodies(world, it, 1)
            }?.also { chosen += it } ?: return false
        }
        onTile.zip(destinations).forEach { (p, dest) -> p.moveTo(dest) }
        return true
    }

    /** Zemouregal spawns with the chest (OSRS), once the npc is in the cache; he is NOT a breach npc (dossier gap 2). */
    private fun spawnGuardian(
        world: World,
        chest: Chest,
    ): Boolean {
        val id = DeadmanChestIds.guardianNpc
        if (id <= 0) return false
        val size = world.definitions.get(NpcDef::class.java, id).size.coerceAtLeast(1)
        val edge = perimeter(world, chest.tile)
        val candidates = (edge + edge.flatMap { reachable(world, it, size + 2) }).distinct()
        val around = candidates.firstOrNull { t ->
            (0 until size).all { dx -> (0 until size).all { dz ->
                val at = Tile(t.x + dx, t.z + dz, t.height)
                !inside(world, chest.tile, at) && DeadmanBreach.usable(world, at) && AreaState.isDangerous(at)
            } } && DeadmanBreach.freeOfBodies(world, t, size)
        } ?: return false
        val npc = Npc(id, Tile(around), world)
        npc.respawnOverride = false
        npc.walkRadius = DeadmanBreach.ROAM_RADIUS
        npc.smartPathfinding = true
        npc.naivePathfinding = true
        npc.attr[DeadmanChest.GUARDIAN_ATTR] = true
        if (!world.spawn(npc)) return false
        chest.guardian = npc
        return true
    }

    // ------------------------------------------------------------------------------------------------------------------ removal

    fun remove(
        world: World,
        chest: Chest,
        reason: String,
    ) {
        if (!chests.remove(chest)) return
        world.remove(chest.obj)
        chest.guardian?.let { guardian ->
            // The reference survives death and looting. Read at removal too, so late summons cannot escape cleanup.
            guardian.attr[DeadmanBreach.SUMMONS_OF]?.toList()?.forEach { summon ->
                if (world.npcs.contains(summon)) world.remove(summon)
                DeadmanBreach.live.remove(summon)
            }
            guardian.attr.remove(DeadmanBreach.SUMMONS_OF)
            if (world.npcs.contains(guardian)) world.remove(guardian)
        }
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
        if (chest.closedAt >= 0) {
            player.message("The chest is still appearing.")
            return
        }
        val refusal = DeadmanChest.refusal(DeadmanChest.factsOf(player, chest.tile))
        if (refusal != null) {
            player.message(refusal)
            return
        }
        channels[player]?.let { if (it.chest === chest && it.actionRevision == player.actionRevision) return }
        channels[player] = Channelling(chest, DeadmanChest.Channel(DeadmanChest.channelTicks(world, chest.tile)), snapshot(player), combatValue(player), player.actionRevision)
        player.message("You start to open the chest. Stay still and do not fight or use your inventory.")
    }

    private fun combatValue(player: Player): Int = if (player.timers.has(ACTIVE_COMBAT_TIMER)) player.timers[ACTIVE_COMBAT_TIMER] else 0

    /** Backup guard for system-driven container mutations (charges/degradation); actions use actionRevision. */
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
            if (chest.closedAt in 0..cycle) {
                world.remove(chest.obj)
                chest.obj = DynamicObject(DeadmanChestIds.closedLoc, 10, 0, Tile(chest.tile))
                world.spawn(chest.obj)
                chest.closedAt = -1
            }
            if (chest.state.looted) {
                if (chest.removeAt in 0..cycle) remove(world, chest, "looted, open state over")
            } else if (cycle >= chest.state.expiresAt) {
                remove(world, chest, "expired unlooted")
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
            val interrupted = gone || snap != ch.snapshot || fought || player.actionRevision != ch.actionRevision
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

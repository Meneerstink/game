package gg.rsmod.plugins.content.mechanics.pvp.breach

import com.google.gson.Gson
import com.google.gson.JsonObject
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import net.runelite.cache.IndexType
import java.io.File
import java.io.FileReader
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * OSRS permanent Deadman Mode breaches (OSRS Wiki "Deadman Mode" #Breaches, "Breach (scenery)", "Boss Spawn"):
 *
 * - Schedule: "every weekend in four-hour intervals, starting on Saturday at 02:00 GMT and ending on Sunday at 22:00 GMT" ([nextStart]).
 * - Types: "Localised breach in two locations, of which one is in single-way combat and other in multicombat" and "a region breach in
 *   which the breach monsters spawn widely across given region" ([Kind]). Locations: the wiki's Deadman: Annihilation tables
 *   (`data/cfg/deadman/breach-locations.json`); single-way locations have three spawners, multicombat ones one ("either one or three").
 * - Spawning: a Breach spawner shoots projectiles; a Boss Spawn appears where one lands and spawns a breach monster on top of it.
 *   Localised monsters land at most [LOCAL_SPAWN_RADIUS] tiles from the spawner. "Up to 25 NPCs spawn in area breaches, up to 6 in
 *   specific location ones" (runescapeguides.com "Deadman - World 345 - Boss Breaches").
 * - "The breach will keep spawning bosses for around 15 minutes, and the ones left unkilled will despawn around 30 minutes later."
 * - "Players with PvP protection active cannot attack breach monsters" ([canFight]); ruby bolts (e) cap at [RUBY_BOLT_CAP] (EnchantedBolts).
 * - Location via Perdu (grand_exchange_hub) and the broadcast at opening.
 *
 * ADAPTED where the sources are silent (listed for the owner): the projectile cadence spreads a breach's monsters evenly over the
 * 15 minutes; which of the two types opens is a fair coin; the regional spawner stands on the region's spawn point nearest its centre;
 * the broadcast and Perdu wording. The Breach and Boss Spawn keep their OSRS models but not their animations or spotanims (skeletal).
 */
object DeadmanBreach {
    const val RUBY_BOLT_CAP = 30
    const val LOCAL_SPAWN_RADIUS = 9
    const val LOCALISED_MAX_NPCS = 6
    const val REGIONAL_MAX_NPCS = 25

    /** How far a regional monster wanders around its landing tile. */
    const val REGIONAL_WANDER_RADIUS = 8

    /**
     * How far from its landing tile a breach monster keeps chasing (the leash, `NpcLeash.mayPursue`). The generic npc leash is 7
     * tiles (Void `NPC.maxRange()` default), so a breach boss gave up on anyone who stepped a few tiles away (owner 2026-09-23
     * "not chasing correctly"). OSRS gives no number for breach monsters; the Zemouregal Summon page confirms they "have a
     * limited wander radius, so they can only chase players so far before they lose aggro". ADAPTED: 16 tiles, the whole
     * localised landing area (radius 9) plus room to follow a player running out of it.
     */
    const val CHASE_RANGE = 16

    /** 15 minutes / 30 minutes in 600 ms game ticks. */
    const val SPAWN_WINDOW_TICKS = 1500
    const val LINGER_TICKS = 3000

    /** Local loc ids of OSRS "Breach" 49561 and "Boss Spawn" 49563 (OsrsLocImportTool deadman-breach). */
    const val BREACH_LOC = BreachIds.BREACH_LOC
    const val BOSS_SPAWN_LOC = BreachIds.BOSS_SPAWN_LOC

    /**
     * How often an open breach pulses its graphic, in game cycles (3 seconds).
     *
     * Owner 2026-09-21: "whenever i spawn a breach through the breach command tele there i see always the same thing
     * on the ground there is no breach". The breach loc IS there - 62747, the OSRS "Breach" scenery imported as
     * tx-20260919-164612 - but it stands dead still, so it reads as a painted ring rather than a rift. Its OSRS
     * animation is sequence 10418, which carries opcode 13, animaya (skeletal) data: `OsrsFxImportTool.decodeOsrsSeq`
     * refuses exactly that opcode because revision 667 has no way to represent a skeletal sequence, so
     * `OsrsLocImportTool` dropped the animation and imported the model alone. The loc therefore cannot be made to
     * animate itself in this cache. Pulsing the breach's own imported graphic over each spawner instead gives the
     * movement back using an asset 667 CAN represent, and is one constant to change if a better graphic turns up.
     */
    private const val BREACH_PULSE_TICKS = 5

    /** How long a Boss Spawn stands before its monster appears on top of it. */
    private const val BOSS_SPAWN_TICKS = 3

    const val LOCATIONS_PATH = "./data/cfg/deadman/breach-locations.json"

    private val BREACH_NPC_ATTR = AttributeKey<Boolean>()
    private val CONTRIBUTION_ATTR = AttributeKey<BreachContribution>()
    private val REWARDED_ATTR = AttributeKey<Boolean>()
    private val DOT_SOURCE_ATTR = AttributeKey<String>()

    enum class Kind { LOCALISED, REGIONAL }

    class Localised(
        val name: String,
        val multi: Boolean,
        val spawners: List<Tile>,
    )

    class Regional(
        val name: String,
        val combat: String,
        val points: List<Tile>,
    )

    class Locations(
        val localised: List<Localised>,
        val regional: List<Regional>,
    )

    /** One open breach site: its spawners, where its monsters may land and everything it spawned. */
    class Site(
        val kind: Kind,
        val name: String,
        val multi: Boolean,
        val spawners: List<Tile>,
        val landing: List<Tile>,
        val maxNpcs: Int,
    ) {
        val objects = ArrayList<DynamicObject>()
        val npcs = ArrayList<Npc>()
        var spawned = 0

        /** Footprints of monsters whose projectile is still in flight. */
        val pending: MutableList<Footprint> = java.util.concurrent.CopyOnWriteArrayList()
    }

    class Active(
        val sites: List<Site>,
        val openedAtCycle: Int,
    )

    @Volatile
    var active: Active? = null
        private set

    @Volatile
    var nextOpening: ZonedDateTime = nextStart(ZonedDateTime.now(ZoneOffset.UTC))
        private set

    private var locations: Locations? = null

    fun isBreachNpc(npc: Npc): Boolean = npc.attr[BREACH_NPC_ATTR] == true

    // ---- schedule ---------------------------------------------------------------------------------------------------

    /** Saturday 02:00 .. Sunday 22:00 GMT every four hours: the first opening strictly after [now]. */
    fun nextStart(now: ZonedDateTime): ZonedDateTime {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        var day = utc.toLocalDate()
        repeat(8) {
            if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) {
                for (hour in 2..22 step 4) {
                    val slot = day.atTime(hour, 0).atZone(ZoneOffset.UTC)
                    if (slot.isAfter(utc)) return slot
                }
            }
            day = day.plusDays(1)
        }
        error("no breach slot within a week of $now")
    }

    fun start(world: World) {
        locations = loadLocations(world)
        world.queue {
            while (true) {
                wait(100)
                val now = ZonedDateTime.now(ZoneOffset.UTC)
                if (!now.isBefore(nextOpening)) {
                    nextOpening = nextStart(now)
                    if (active == null) open(world, null)
                }
            }
        }
    }

    // ---- locations --------------------------------------------------------------------------------------------------

    fun loadLocations(file: File = File(LOCATIONS_PATH)): Locations {
        val root = FileReader(file).use { Gson().fromJson(it, JsonObject::class.java) }
        val localised =
            root["localised"].asJsonArray.map { e ->
                val o = e.asJsonObject
                Localised(
                    o["name"].asString,
                    o["multi"].asBoolean,
                    o["spawners"].asJsonArray.map { p -> p.asJsonArray.let { Tile(it[0].asInt, it[1].asInt, it[2].asInt) } },
                )
            }
        val regional =
            root["regional"].asJsonArray.map { e ->
                val o = e.asJsonObject
                val plane = o["plane"].asInt
                Regional(o["name"].asString, o["combat"].asString, o["points"].asJsonArray.map { p -> p.asJsonArray.let { Tile(it[0].asInt, it[1].asInt, plane) } })
            }
        return Locations(localised, regional)
    }

    private fun loadLocations(world: World): Locations = loadLocations().let { all -> Locations(
        all.localised.map { Localised(it.name, it.multi, it.spawners.filter { t -> usable(world, t) }) }.filter { it.spawners.isNotEmpty() },
        all.regional.map { Regional(it.name, it.combat, it.points.filter { t -> usable(world, t) }) }.filter { it.points.size >= REGIONAL_MAX_NPCS },
    ) }

    /**
     * A tile the rev-667 map has (its map square exists), that is walkable, lies outside every Deadman safe zone and holds no
     * object in the slot a type-10 loc uses - a spawned Breach or Boss Spawn must never replace (and on removal delete) a map object.
     */
    fun usable(
        world: World,
        tile: Tile,
    ): Boolean =
        mapExists(world, tile) && !world.collision.isClipped(tile) && !GuardedZones.contains(tile) &&
            world.chunks.get(tile, createIfNeeded = true)!!
                .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .none { it.type in 9..21 }

    fun mapExists(
        world: World,
        tile: Tile,
    ): Boolean = world.filestore.data(IndexType.MAPS.number, "m${tile.x shr 6}_${tile.z shr 6}") != null

    // ---- opening ----------------------------------------------------------------------------------------------------

    /** Every usable location (localised first, then regional) with a tile at its spawner, numbered from 1 by the admin commands. */
    fun allLocations(world: World): List<Pair<String, Tile>> {
        val locs = locations ?: loadLocations(world).also { locations = it }
        return locs.localised.map { "${it.name} (local, ${if (it.multi) "multi" else "single"})" to it.spawners.first() } +
            locs.regional.map { r -> "${r.name} (region)" to r.points.first() }
    }

    /** Closes the open breach at once: its spawners and every monster it spawned are removed. */
    fun close(world: World) {
        val open = active ?: return
        active = null
        open.sites.forEach { site ->
            site.objects.forEach { world.remove(it) }
            site.npcs.forEach { if (world.npcs.contains(it) && !it.isDead()) world.remove(it) }
            site.npcs.clear()
        }
        pruneLive(world)
        gg.rsmod.game.Server.logger.info("Breach closed by command: spawners and monsters removed.")
    }

    /**
     * Opens a breach now; [kind] null picks one of the two types at random, [only] (admin command) opens the location whose
     * name contains it. Returns the sites, or null when nothing is usable.
     */
    fun open(
        world: World,
        kind: Kind?,
        only: String? = null,
    ): Active? {
        if (active != null) return active
        val locs = locations ?: loadLocations(world).also { locations = it }
        val wantedLocal = only?.let { o -> locs.localised.firstOrNull { it.name.contains(o, ignoreCase = true) } }
        val wantedRegion = only?.let { o -> locs.regional.firstOrNull { it.name.contains(o, ignoreCase = true) } }
        if (only != null && wantedLocal == null && wantedRegion == null) return null
        val chosen =
            when {
                wantedLocal != null -> Kind.LOCALISED
                wantedRegion != null -> Kind.REGIONAL
                else -> kind ?: if (Random.nextBoolean()) Kind.LOCALISED else Kind.REGIONAL
            }
        val sites =
            when (chosen) {
                Kind.LOCALISED -> {
                    val single = if (wantedLocal != null) wantedLocal.takeIf { !it.multi } else locs.localised.filter { !it.multi }.randomOrNull()
                    val multi = if (wantedLocal != null) wantedLocal.takeIf { it.multi } else locs.localised.filter { it.multi }.randomOrNull()
                    listOfNotNull(single, multi).map { l ->
                        val landing = l.spawners.flatMap { s -> around(world, s, LOCAL_SPAWN_RADIUS) }.distinct()
                        Site(Kind.LOCALISED, l.name, l.multi, l.spawners, landing, LOCALISED_MAX_NPCS)
                    }
                }
                Kind.REGIONAL -> {
                    val r = wantedRegion ?: locs.regional.randomOrNull() ?: return null
                    val cx = r.points.sumOf { it.x } / r.points.size
                    val cz = r.points.sumOf { it.z } / r.points.size
                    val spawner = r.points.minByOrNull { (it.x - cx) * (it.x - cx) + (it.z - cz) * (it.z - cz) }!!
                    listOf(Site(Kind.REGIONAL, r.name, r.combat != "Singles", listOf(spawner), r.points, REGIONAL_MAX_NPCS))
                }
            }.filter { it.landing.isNotEmpty() }
        if (sites.isEmpty()) return null
        val opened = Active(sites, world.currentCycle)
        active = opened
        sites.forEach { site ->
            site.spawners.forEach { t ->
                val obj = DynamicObject(BREACH_LOC, 10, 0, t)
                world.spawn(obj)
                site.objects += obj
            }
        }
        val names = sites.joinToString(" and ") { "${it.name.replaceFirstChar { c -> c.lowercase() }} (${if (it.multi) "multi-way" else "single-way"} combat)" }
        world.players.forEach { it.filterableMessage("<col=ff0000>Breaches have opened: $names!</col>") }
        gg.rsmod.game.Server.logger.info("Breach opened: {}.", sites.joinToString("; ") { "${it.name} ${it.kind} ${if (it.multi) "multi" else "single"} spawners=${it.spawners} landing=${it.landing.size}" })
        run(world, opened)
        return opened
    }

    /**
     * Landing tiles of a localised spawner: usable tiles within [radius] (Chebyshev) that can be WALKED to from the spawner.
     *
     * The square alone let monsters land behind walls, inside neighbouring buildings or on the far side of a river - unreachable
     * for players (a free safespot for them, a trapped monster for the breach). A flood fill over the real collision map keeps
     * every landing on the spawner's side. The spawner tile itself is excluded (the Breach loc stands there).
     */
    fun around(
        world: World,
        centre: Tile,
        radius: Int,
    ): List<Tile> {
        val seen = HashSet<Tile>()
        val queue = ArrayDeque<Tile>()
        seen += centre
        queue += centre
        val out = ArrayList<Tile>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current != centre && usable(world, current)) out += current
            for (direction in gg.rsmod.game.model.Direction.NESW) {
                val next = current.step(direction)
                if (next in seen) continue
                if (kotlin.math.abs(next.x - centre.x) > radius || kotlin.math.abs(next.z - centre.z) > radius) continue
                if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) continue
                seen += next
                queue += next
            }
        }
        return out
    }

    private fun run(
        world: World,
        opened: Active,
    ) {
        world.queue {
            var elapsed = 0
            while (elapsed < SPAWN_WINDOW_TICKS && active === opened) {
                opened.sites.forEach { site ->
                    val interval = SPAWN_WINDOW_TICKS / site.maxNpcs
                    if (site.spawned < site.maxNpcs && elapsed >= site.spawned * interval) fire(world, site)
                }
                if (elapsed % BREACH_PULSE_TICKS == 0) {
                    opened.sites.forEach { site -> site.spawners.forEach { world.spawn(TileGraphic(it, BreachMonsters.GFX_BREACH_PROJECTILE, height = 0)) } }
                }
                wait(1)
                elapsed++
            }
            // The spawners close; unkilled monsters stay for another 30 minutes.
            opened.sites.forEach { site -> site.objects.forEach { world.remove(it) } }
            if (active === opened) active = null
            gg.rsmod.game.Server.logger.info("Breach spawning stopped: {} monsters spawned, survivors linger {} ticks.", opened.sites.sumOf { it.spawned }, LINGER_TICKS)
            wait(LINGER_TICKS)
            var removed = 0
            opened.sites.forEach { site ->
                site.npcs.forEach { if (world.npcs.contains(it) && !it.isDead()) { world.remove(it); removed++ } }
                site.npcs.clear()
            }
            pruneLive(world)
            gg.rsmod.game.Server.logger.info("Breach cleanup: {} unkilled monsters removed.", removed)
        }
    }

    /**
     * One projectile from a spawner to a landing tile; a Boss Spawn appears there, the monster spawns on top of it and the Boss
     * Spawn then disappears (OSRS Wiki "Boss Spawn": "These boss spawns then spawn a breach monster on top of them before
     * disappearing").
     *
     * Owner 2026-09-23 ("some breachmonsters are spawning in each other", "not moving they just stand still", "no clipp"): the
     * landing tile used to be any single usable tile, but a breach monster is 1x1 to 5x5, so its other tiles landed in walls,
     * rocks and other monsters - stuck there it could not step anywhere and stood inside the scenery. The monster is now chosen
     * first and lands only where its whole footprint is usable and free of every other npc and every landing still in flight.
     */
    private fun fire(
        world: World,
        site: Site,
    ) {
        val monster = BreachMonsters.SPAWNABLE.random()
        val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, monster.id)?.size ?: 1
        val landing = site.landing.shuffled().firstOrNull { fits(world, it, size) && isFree(world, site, it, size) }
        if (landing == null) {
            // Nowhere free for this one right now; the next pulse tries again (spawned is not counted).
            return
        }
        site.spawned++
        val reserved = Footprint(landing, size)
        site.pending += reserved
        val centre = Tile(landing.x + (size - 1) / 2, landing.z + (size - 1) / 2, landing.height)
        val from = site.spawners.minByOrNull { it.getDistance(centre) }!!
        val flight = 30 + from.getDistance(centre) * 5
        world.spawn(
            Projectile.Builder().setTiles(start = from, target = centre).setGfx(BreachMonsters.GFX_BREACH_PROJECTILE)
                .setHeights(startHeight = 100, endHeight = 0).setSlope(angle = 16, steepness = 64).setTimes(delay = 30, lifespan = flight).build(),
        )
        val landTicks = flight / 30 + 1
        world.queue {
            wait(landTicks)
            val bossSpawn = DynamicObject(BOSS_SPAWN_LOC, 10, 0, centre)
            world.spawn(bossSpawn)
            wait(BOSS_SPAWN_TICKS)
            site.npcs += spawnMonster(world, monster.id, landing, if (site.kind == Kind.LOCALISED) LOCAL_SPAWN_RADIUS else REGIONAL_WANDER_RADIUS)
            site.pending -= reserved
            wait(1)
            world.remove(bossSpawn)
        }
    }

    /** A square of tiles an npc will occupy (south-west corner + size). */
    data class Footprint(
        val corner: Tile,
        val size: Int,
    ) {
        fun overlaps(
            other: Tile,
            otherSize: Int,
        ): Boolean =
            corner.height == other.height &&
                corner.x < other.x + otherSize && other.x < corner.x + size &&
                corner.z < other.z + otherSize && other.z < corner.z + size
    }

    /** Every tile of a [size] x [size] footprint at [corner] is usable ([usable]) and can be walked between. */
    fun fits(
        world: World,
        corner: Tile,
        size: Int,
    ): Boolean {
        for (dx in 0 until size) for (dz in 0 until size) {
            if (!usable(world, corner.transform(dx, dz))) return false
        }
        return true
    }

    /** No live npc and no landing still in flight overlaps the footprint. */
    private fun isFree(
        world: World,
        site: Site,
        corner: Tile,
        size: Int,
    ): Boolean {
        val fp = Footprint(corner, size)
        if ((active?.sites.orEmpty() + site).any { s -> s.pending.any { it.overlaps(corner, size) } }) return false
        val reach = gg.rsmod.game.model.MovementQueue.MAX_NPC_SIZE - 1
        for (x in corner.x - reach until corner.x + size) for (z in corner.z - reach until corner.z + size) {
            val t = Tile(x, z, corner.height)
            val chunk = world.chunks.get(t, createIfNeeded = false) ?: continue
            if (chunk.getEntities<Npc>(t, EntityType.NPC).any { it.tile == t && fp.overlaps(t, it.getSize().coerceAtLeast(1)) }) return false
        }
        return true
    }

    /** Every breach npc currently in the world (breach monsters and Zemouregal's summons), for the per-tick mechanics. */
    val live: MutableSet<Npc> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Spawns a breach npc; with [lifetimeTicks] it is removed after that long if still alive (Zemouregal's summons). */
    fun spawnMonster(
        world: World,
        id: Int,
        tile: Tile,
        walkRadius: Int,
        lifetimeTicks: Int = -1,
    ): Npc {
        val npc = Npc(id, Tile(tile), world)
        npc.respawnOverride = false
        npc.walkRadius = walkRadius
        npc.attr[BREACH_NPC_ATTR] = true
        // OSRS Wiki: Durial321 "is also capable of running after his target, unlike most NPCs"; I DSCIM YOU "runs at its targets".
        if (id in BreachMonsters.RUNNERS) npc.attr[gg.rsmod.game.model.attr.NPC_RUNS_ATTR] = true
        world.spawn(npc)
        live += npc
        gg.rsmod.game.Server.logger.info("Breach monster {} spawned at {}.", id, tile)
        if (lifetimeTicks > 0) {
            world.queue {
                wait(lifetimeTicks)
                if (world.npcs.contains(npc) && !npc.isDead()) world.remove(npc)
            }
        }
        return npc
    }

    /** Drops npcs that died or were removed from [live]. */
    fun pruneLive(world: World) {
        live.removeIf { !world.npcs.contains(it) || it.isDead() }
    }

    // ---- combat rules -----------------------------------------------------------------------------------------------

    /**
     * "Players with PvP protection active cannot attack breach monsters at either breach location" (OSRS Wiki "Deadman Mode";
     * owner 2026-09-23: blocked in single-way and multi-way alike, either direction, spells included).
     */
    fun canFight(player: Player): Boolean = !BeginnerProtection.isProtected(player)

    /**
     * The player a pawn fights for: the player itself, or the owner of a familiar/pet that deals damage on a player's behalf
     * ([gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR], the same credit the damage map uses). Null for ordinary npcs.
     */
    fun controllingPlayer(pawn: Pawn): Player? =
        pawn as? Player ?: pawn.attr[gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR]?.get() as? Player

    /** Local players within [radius] tiles of [centre] on its plane - chunk lookups, never a scan of every player online. */
    fun playersNear(
        world: World,
        centre: Tile,
        radius: Int,
    ): List<Player> {
        val out = ArrayList<Player>()
        for (cx in ((centre.x - radius) shr 3)..((centre.x + radius) shr 3)) {
            for (cz in ((centre.z - radius) shr 3)..((centre.z + radius) shr 3)) {
                val chunk = world.chunks.get(Tile(cx shl 3, cz shl 3, centre.height), createIfNeeded = false) ?: continue
                chunk.getEntities<Player>(EntityType.CLIENT, EntityType.PLAYER).forEach { p ->
                    if (p.tile.height == centre.height && p.tile.isWithinRadius(centre, radius) && p !in out) out += p
                }
            }
        }
        return out
    }

    /** Porazdir, Justiciar Zachariah and Derwen are "completely immune to melee and ranged attacks". */
    fun modifyIncomingDamage(
        npc: Npc,
        hitType: HitType,
        damage: Int,
    ): Int {
        if (!isBreachNpc(npc)) return damage
        val m = BreachMonsters.BY_ID[npc.id] ?: return damage
        return when {
            hitType == HitType.MELEE && m.immuneMelee -> 0
            hitType == HitType.RANGE && m.immuneRanged -> 0
            else -> damage
        }
    }

    /**
     * Adds [damage] (already credited to the familiar's owner by the caller) to the monster's contribution ledger.
     *
     * The ledger is keyed by the lower-case account name, not the [Player] object: a player who relogs gets a new object, and
     * keying by object used to drop his first-damage slot (offline) while handing the new object a second slot. Insertion order
     * is first-damage order ([BreachContribution.lootEligible]); the value is total damage ([BreachContribution.pointEarners]).
     * Only real damage counts ("the first 16 players to deal damage"), so a 0 hitsplat claims nothing.
     */
    fun recordDamage(
        target: Pawn,
        source: Pawn,
        damage: Int,
    ) {
        if (damage <= 0 || target !is Npc || source !is Player || !isBreachNpc(target)) return
        if (target.attr[REWARDED_ATTR] == true) return
        val ledger = target.attr[CONTRIBUTION_ATTR] ?: BreachContribution().also { target.attr[CONTRIBUTION_ATTR] = it }
        ledger.add(source.username, damage)
    }

    fun contribution(npc: Npc): BreachContribution = npc.attr[CONTRIBUTION_ATTR] ?: BreachContribution()

    /**
     * Poison and venom tick without a source, so the player who applied them is remembered when they take hold: the last pawn
     * that attacked the monster ([gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR], set as the attack lands), credited to a familiar's
     * owner. Breach monsters only - ordinary npcs keep their unchanged drop rules.
     */
    fun notePoisoner(target: Pawn) {
        if (target !is Npc || !isBreachNpc(target)) return
        val source = target.attr[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR]?.get() ?: return
        target.attr[DOT_SOURCE_ATTR] = controllingPlayer(source)?.username ?: return
    }

    /** A poison/venom tick on a breach monster counts for the player who applied it ([notePoisoner]). */
    fun recordDotDamage(
        target: Pawn,
        damage: Int,
    ) {
        if (damage <= 0 || target !is Npc || !isBreachNpc(target) || target.attr[REWARDED_ATTR] == true) return
        val name = target.attr[DOT_SOURCE_ATTR] ?: return
        val ledger = target.attr[CONTRIBUTION_ATTR] ?: BreachContribution().also { target.attr[CONTRIBUTION_ATTR] = it }
        ledger.add(name, damage)
    }

    // ---- loot -------------------------------------------------------------------------------------------------------

    /**
     * Commits one breach monster's rewards exactly once: loot for the first [BreachLoot.ELIGIBLE] damage dealers (each with an
     * independent roll, dropped under the monster and visible only to its owner), the Archaic emblem (tier 5) for eligible
     * players with [BreachLoot.EMBLEM_DAMAGE]+ damage, and Breach Points for the top [BreachPoints.EARNERS]. The monster is
     * marked rewarded first and its ledger cleared afterwards, so a second death callback, a late hit or a double click can
     * never pay twice. Players who are offline when it dies receive nothing (their slot is not handed to anyone else).
     */
    fun dropLoot(npc: Npc) {
        if (npc.attr[REWARDED_ATTR] == true) return
        npc.attr[REWARDED_ATTR] = true
        val world = npc.world
        val ledger = contribution(npc)
        val tile = Tile(npc.tile)
        var looted = 0
        ledger.lootEligible().forEach { name ->
            val player = world.getPlayerForName(name) ?: return@forEach
            looted++
            BreachLoot.roll(world.random).forEach drops@{ drop ->
                val def = world.definitions.getNullable(ItemDef::class.java, drop.item) ?: return@drops
                val item = if (drop.noted && def.noteLinkId > 0) def.noteLinkId else drop.item
                world.spawn(GroundItem(item, drop.amount, tile, player))
            }
            if (ledger.damageOf(name) >= BreachLoot.EMBLEM_DAMAGE && BreachIds.ARCHAIC_EMBLEM_TIER_5 > 0 &&
                world.definitions.getNullable(ItemDef::class.java, BreachIds.ARCHAIC_EMBLEM_TIER_5) != null
            ) {
                world.spawn(GroundItem(BreachIds.ARCHAIC_EMBLEM_TIER_5, 1, tile, player))
            }
        }
        var paid = 0
        ledger.pointEarners().forEach { (name, damage) ->
            val player = world.getPlayerForName(name) ?: return@forEach
            if (BreachPoints.award(player, damage) > 0) paid++
        }
        gg.rsmod.game.Server.logger.info(
            "Breach reward: npc {} at {} - {} contributors, loot to {}, points to {}.",
            npc.id, tile, ledger.size, looted, paid,
        )
        npc.attr.remove(CONTRIBUTION_ATTR)
    }

    // ---- status -----------------------------------------------------------------------------------------------------

    /** Perdu / home-board line: where the open breach is, or when the next one opens. */
    fun statusLine(now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)): String {
        val open = active
        if (open != null) {
            return "The breach is open " + open.sites.joinToString(" and ") { it.name.replaceFirstChar { c -> c.lowercase() } } + "."
        }
        val left = Duration.between(now, nextOpening)
        return "The next breach opens in ${left.toHours()} hour(s) and ${left.toMinutesPart()} minute(s)."
    }

}

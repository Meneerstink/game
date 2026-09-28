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
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
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
 * the broadcast and Perdu wording. The Breach and Boss Spawn animations and the breach spawn/despawn graphics are OSRS skeletal\n * animations baked into classic frames (SkeletalBakeTool, 2026-09-27).
 */
object DeadmanBreach {
    const val RUBY_BOLT_CAP = 30
    const val LOCAL_SPAWN_RADIUS = 9
    const val LOCALISED_MAX_NPCS = 6
    const val REGIONAL_MAX_NPCS = 25

    /** How far a regional monster wanders around its landing tile. */
    const val REGIONAL_WANDER_RADIUS = 8

    /** Owner decision 2026-09-28 ("laat alle breachmonsters ook rondlopen, max 10 tiles"): the roam radius of every breach npc. */
    const val ROAM_RADIUS = 10

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

    /**
     * Breach audit 2026-09-27, phase 4: the OSRS skeletal animations of the breach are now baked into classic 667 frames by
     * `SkeletalBakeTool` (tx-20260927-062328 / -062541): the Breach loc plays its own spiral (OSRS seq 10418 -> 15828), the Boss
     * Spawn its own (10423 -> 15829), and the spawn / despawn graphics exist: VFX_DM_BREACH_SPAWN 2519 -> 3173 and
     * VFX_DM_BREACH_DESPAWN 2553 -> 3174. The pulse of the projectile graphic above ([BREACH_PULSE_TICKS]) is therefore no longer
     * played - OSRS shows only the animated spiral.
     */
    const val GFX_BREACH_SPAWN = 3173
    const val GFX_BREACH_DESPAWN = 3174

    /** RuneLite gameval SpotanimID VFX_DM_BOSS_SPAWN 2554 / VFX_DM_BOSS_DESPAWN 2556, baked (tx-20260927-073656). */
    const val GFX_BOSS_SPAWN = 3175
    const val GFX_BOSS_DESPAWN = 3176

    /**
     * Boss Spawn sounds (owner 2026-09-27: it must "spawn, make a sound and disappear"). The OSRS breach and Boss Spawn sequences
     * carry no frame sounds (SkeletalBakeTool probe) and no breach sound name exists in the OSRS Wiki sound list, so the owner
     * chose OSRS 4274 noa_rift_open_1 / 4322 noa_rift_end_screech_1 (ADAPTED), imported as 10339 / 10340 (tx-20260927-145454).
     */
    const val SFX_BOSS_SPAWN = 10339
    const val SFX_BOSS_DESPAWN = 10340

    /** How long a Boss Spawn stands before its monster appears on top of it. */
    private const val BOSS_SPAWN_TICKS = 3

    const val LOCATIONS_PATH = "./data/cfg/deadman/breach-locations.json"

    private val BREACH_NPC_ATTR = AttributeKey<Boolean>()
    private val CONTRIBUTION_ATTR = AttributeKey<BreachContribution>()
    private val REWARDED_ATTR = AttributeKey<Boolean>()
    private val DOT_SOURCE_ATTR = AttributeKey<String>()
    private val TEST_NPC_ATTR = AttributeKey<Boolean>()

    /** Spawned by `breachtest`: gives no rewards ([dropLoot]). */
    fun isTestNpc(npc: Npc): Boolean = npc.attr[TEST_NPC_ATTR] == true

    fun markTest(npc: Npc) {
        npc.attr[TEST_NPC_ATTR] = true
    }

    /** Audit D-14: the breach event ([Active.eventId]) a breach npc was spawned for. */
    private val BREACH_EVENT_ATTR = AttributeKey<Int>()

    /** Audit D-14: id of the most recently opened breach event; npcs spawned while none is open belong to it. */
    @Volatile
    private var lastEventId = 0

    /** Audit D-14: breach event id -> lower-case account names that already received an Archaic emblem in it. */
    private val emblemClaims = java.util.concurrent.ConcurrentHashMap<Int, MutableSet<String>>()

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
        val objects: MutableList<DynamicObject> = java.util.concurrent.CopyOnWriteArrayList()
        val npcs: MutableList<Npc> = java.util.concurrent.CopyOnWriteArrayList()
        var spawned = 0

        /** Footprints of monsters whose projectile is still in flight. */
        val pending: MutableList<Footprint> = java.util.concurrent.CopyOnWriteArrayList()

        /** Boss Spawns standing on a landing tile right now (removed when their monster appears or the breach closes). */
        val bossSpawns: MutableList<DynamicObject> = java.util.concurrent.CopyOnWriteArrayList()

        /** Set by [close]: projectiles still in flight land on nothing and spawn nothing. */
        @Volatile
        var closed = false
    }

    class Active(
        val sites: List<Site>,
        val openedAtCycle: Int,
        /** Audit D-14: identifies this breach event (one emblem per account per event). */
        val eventId: Int = 0,
    )

    /**
     * Every site whose monsters may still be alive: the open breach and the previous ones in their linger time. [close] clears
     * all of them (breach audit 2026-09-27, F2: a breach whose spawning had ended was no longer [active], so `breachclose`
     * left its survivors standing).
     */
    private val lingering: MutableList<Site> = java.util.concurrent.CopyOnWriteArrayList()

    @Volatile
    var active: Active? = null
        private set

    /** Declared before [nextOpening], whose initializer reads it. */
    @Volatile
    var schedule: Schedule = Schedule()

    @Volatile
    var nextOpening: ZonedDateTime = nextStart(ZonedDateTime.now(ZoneOffset.UTC))
        private set

    private var locations: Locations? = null

    fun isBreachNpc(npc: Npc): Boolean = npc.attr[BREACH_NPC_ATTR] == true

    // ---- schedule ---------------------------------------------------------------------------------------------------

    /**
     * When breaches open, always in UTC so daylight saving never moves them. Default: the OSRS permanent-world schedule,
     * Saturday 02:00 .. Sunday 22:00 every four hours. `data/cfg/deadman/breach-schedule.json` overrides it
     * (`{"days": ["SATURDAY", "SUNDAY"], "hours": [2, 6, 10, 14, 18, 22]}`), read at boot.
     */
    class Schedule(
        val days: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        val hours: List<Int> = (2..22 step 4).toList(),
    )

    const val SCHEDULE_PATH = "./data/cfg/deadman/breach-schedule.json"

    fun loadSchedule(file: File = File(SCHEDULE_PATH)): Schedule {
        if (!file.exists()) return Schedule()
        val root = FileReader(file).use { Gson().fromJson(it, JsonObject::class.java) }
        val days = root["days"].asJsonArray.map { DayOfWeek.valueOf(it.asString.uppercase()) }.toSet()
        val hours = root["hours"].asJsonArray.map { it.asInt }.filter { it in 0..23 }.sorted()
        require(days.isNotEmpty() && hours.isNotEmpty()) { "$file needs at least one day and one hour" }
        return Schedule(days, hours)
    }

    /** The first opening of [schedule] strictly after [now] (UTC). */
    fun nextStart(now: ZonedDateTime): ZonedDateTime {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        var day = utc.toLocalDate()
        val plan = schedule
        repeat(8) {
            if (day.dayOfWeek in plan.days) {
                for (hour in plan.hours) {
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
        schedule = try { loadSchedule() } catch (e: Exception) { gg.rsmod.game.Server.logger.error("Breach schedule not loaded, using the default: {}", e.toString()); Schedule() }
        nextOpening = nextStart(ZonedDateTime.now(ZoneOffset.UTC))
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

    private fun loadLocations(world: World): Locations =
        loadLocations().let { all ->
            val locs =
                Locations(
                    all.localised.map { Localised(it.name, it.multi, it.spawners.filter { t -> usable(world, t) }) }.filter { it.spawners.isNotEmpty() },
                    all.regional.map { Regional(it.name, it.combat, it.points.filter { t -> usable(world, t) }) }.filter { it.points.size >= REGIONAL_MAX_NPCS },
                )
            // Breach audit 2026-09-27 (F8): name every location the rev-667 map cannot hold instead of dropping it silently.
            val keptLocal = locs.localised.map { it.name }.toSet()
            val keptRegion = locs.regional.map { it.name }.toSet()
            val droppedLocal = all.localised.map { it.name }.filter { it !in keptLocal }
            val droppedRegion = all.regional.map { it.name }.filter { it !in keptRegion }
            gg.rsmod.game.Server.logger.info(
                "Breach locations: {} localised and {} regional usable; {} dropped (no usable 667 map tile): {}",
                locs.localised.size, locs.regional.size, droppedLocal.size + droppedRegion.size, droppedLocal + droppedRegion,
            )
            locs
        }

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

    /** Map square id -> whether the rev-667 cache has it; F9: one filestore lookup per 64x64 square instead of one per tile. */
    private val mapSquares = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    fun mapExists(
        world: World,
        tile: Tile,
    ): Boolean =
        mapSquares.getOrPut(((tile.x shr 6) shl 8) or (tile.z shr 6)) {
            world.filestore.data(IndexType.MAPS.number, "m${tile.x shr 6}_${tile.z shr 6}") != null
        }

    // ---- opening ----------------------------------------------------------------------------------------------------

    /** Every usable location (localised first, then regional) with a tile at its spawner, numbered from 1 by the admin commands. */
    fun allLocations(world: World): List<Pair<String, Tile>> {
        val locs = locations ?: loadLocations(world).also { locations = it }
        return locs.localised.map { "${it.name} (local, ${if (it.multi) "multi" else "single"})" to it.spawners.first() } +
            locs.regional.map { r -> "${r.name} (region)" to r.points.first() }
    }

    /**
     * Closes every breach at once (breach audit 2026-09-27, F2): the open one and the ones still lingering, their spawners and
     * Boss Spawns, every breach npc in the world (monsters, Zemouregal's summons, TzTok-Jad-Rek, `breachtest` monsters), and
     * every projectile still in flight lands on nothing. Returns how many npcs were removed.
     */
    fun close(world: World): Int {
        val open = active
        active = null
        val sites = (open?.sites.orEmpty() + lingering).distinct()
        lingering.clear()
        sites.forEach { site ->
            site.closed = true
            if (site.objects.isNotEmpty()) site.spawners.forEach { world.spawn(TileGraphic(it, GFX_BREACH_DESPAWN, height = 0)) }
            // F1: the objects list is emptied here, so the old run task can never remove a newer breach's objects on the same tile.
            site.objects.forEach { world.remove(it) }
            site.objects.clear()
            site.bossSpawns.forEach { world.remove(it) }
            site.bossSpawns.clear()
            site.pending.clear()
            site.npcs.clear()
        }
        var removed = 0
        live.toList().forEach { npc ->
            if (world.npcs.contains(npc) && !npc.isDead()) {
                world.remove(npc)
                removed++
            }
        }
        live.clear()
        gg.rsmod.game.Server.logger.info("Breach closed by command: {} site(s) and {} breach npc(s) removed.", sites.size, removed)
        return removed
    }

    /**
     * Opens a breach now; [kind] null picks one of the two types at random, [only] (admin command) opens the location whose
     * name contains it. Returns the sites, or null when nothing is usable.
     */
    /** The localised [Site] whose name contains [name], without opening anything (breach conformance harness). */
    internal fun localisedSite(
        world: World,
        name: String,
    ): Site? {
        val locs = locations ?: loadLocations(world).also { locations = it }
        val l = locs.localised.firstOrNull { it.name.contains(name, ignoreCase = true) } ?: return null
        val landing = l.spawners.flatMap { s -> around(world, s, LOCAL_SPAWN_RADIUS) }.distinct()
        return Site(Kind.LOCALISED, l.name, l.multi, l.spawners, landing, LOCALISED_MAX_NPCS)
    }

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
        lastEventId++
        // Only the current and the previous event can still have live monsters (survivors linger after spawning stops).
        emblemClaims.keys.removeIf { it < lastEventId - 1 }
        val opened = Active(sites, world.currentCycle, lastEventId)
        active = opened
        lingering += sites
        try {
            sites.forEach { site ->
                site.spawners.forEach { t ->
                    val obj = DynamicObject(BREACH_LOC, 10, 0, t)
                    world.spawn(obj)
                    site.objects += obj
                    world.spawn(TileGraphic(t, GFX_BREACH_SPAWN, height = 0))
                }
            }
            val names = sites.joinToString(" and ") { "${it.name.replaceFirstChar { c -> c.lowercase() }} (${if (it.multi) "multi-way" else "single-way"} combat)" }
            // F5: a world event, never hidden by the game-message filter.
            world.players.forEach { it.message("<col=ff0000>Breaches have opened: $names!</col>") }
            gg.rsmod.game.Server.logger.info("Breach opened: {}.", sites.joinToString("; ") { "${it.name} ${it.kind} ${if (it.multi) "multi" else "single"} spawners=${it.spawners} landing=${it.landing.size}" })
            run(world, opened)
        } catch (e: Exception) {
            // F7: a failed opening must not leave `active` set (no breach could open again until a restart).
            gg.rsmod.game.Server.logger.error("Breach opening failed, cleaning up: {}", e.toString(), e)
            close(world)
            return null
        }
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
                if (!world.collision.canStep(current, direction)) continue
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
            val closed = { opened.sites.all { it.closed } }
            while (elapsed < SPAWN_WINDOW_TICKS && active === opened && !closed()) {
                // F7: one bad tick (a failed spawn) is logged and skipped; it never ends the breach or leaves `active` set.
                try {
                    opened.sites.forEach { site ->
                        // Owner decision 2026-09-27: half of a site's monsters come out at once when the breach opens, the rest
                        // spread over the 15 minutes (OSRS gives no cadence; one every 2.5 minutes looked like an empty breach).
                        val burst = site.maxNpcs / 2
                        val interval = SPAWN_WINDOW_TICKS / (site.maxNpcs - burst).coerceAtLeast(1)
                        if (site.spawned < site.maxNpcs && (site.spawned < burst || elapsed >= (site.spawned - burst + 1) * interval)) fire(world, site)
                    }

                } catch (e: Exception) {
                    gg.rsmod.game.Server.logger.error("Breach tick {} failed: {}", elapsed, e.toString(), e)
                }
                wait(1)
                elapsed++
            }
            if (active === opened) active = null
            // F1: a breach closed by command was cleaned up by close(); touching its objects again could remove a newer
            // breach's spiral standing on the same tile.
            if (closed()) return@queue
            // The spawners close; unkilled monsters stay for another 30 minutes.
            opened.sites.forEach { site ->
                site.spawners.forEach { world.spawn(TileGraphic(it, GFX_BREACH_DESPAWN, height = 0)) }
                site.objects.forEach { world.remove(it) }
                site.objects.clear()
            }
            gg.rsmod.game.Server.logger.info("Breach spawning stopped: {} monsters spawned, survivors linger {} ticks.", opened.sites.sumOf { it.spawned }, LINGER_TICKS)
            wait(LINGER_TICKS)
            if (closed()) return@queue
            var removed = 0
            opened.sites.forEach { site ->
                site.closed = true
                site.npcs.forEach { if (world.npcs.contains(it) && !it.isDead()) { world.remove(it); removed++ } }
                site.npcs.clear()
                lingering -= site
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
    internal fun fire(
        world: World,
        site: Site,
    ) {
        val monster = BreachMonsters.SPAWNABLE.random()
        val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, monster.id)?.size ?: 1
        val landing =
            site.landing.shuffled().firstOrNull {
                fits(world, it, size) && canLeave(world, it, size) && isFree(world, site, it, size) &&
                    (site.kind != Kind.LOCALISED || reachesSpawner(world, it, size, site.spawners))
            }
        if (gg.rsmod.game.model.BreachTrace.active()) {
            // Breach conformance trace: how many landing tiles each rule rejected for this monster.
            var noFit = 0
            var boxed = 0
            var taken = 0
            var noRoute = 0
            site.landing.forEach {
                when {
                    !fits(world, it, size) -> noFit++
                    !canLeave(world, it, size) -> boxed++
                    !isFree(world, site, it, size) -> taken++
                    site.kind == Kind.LOCALISED && !reachesSpawner(world, it, size, site.spawners) -> noRoute++
                }
            }
            gg.rsmod.game.model.BreachTrace.log("breach.fire") {
                "site='${site.name}' monster=${monster.id} size=$size landing=${landing?.let { "${it.x},${it.z}" } ?: "NONE"} tiles=${site.landing.size} " +
                    "rejected[fit=$noFit boxed=$boxed taken=$taken noRoute=$noRoute] spawned=${site.spawned}/${site.maxNpcs}"
            }
        }
        if (landing == null) {
            // Nowhere free for this one right now; the next pulse tries again (spawned is not counted).
            return
        }
        site.spawned++
        val reserved = Footprint(landing, size)
        site.pending += reserved
        val centre = Tile(landing.x + (size - 1) / 2, landing.z + (size - 1) / 2, landing.height)
        val from = projectileStart(site.spawners.minByOrNull { it.getDistance(centre) }!!, centre)
        val flight = flightCycles(from, centre)
        world.spawn(
            Projectile.Builder().setTiles(start = from, target = centre).setGfx(BreachMonsters.GFX_BREACH_PROJECTILE)
                .setHeights(startHeight = 100, endHeight = 0).setSlope(angle = 16, steepness = 64).setTimes(delay = 30, lifespan = flight).build(),
        )
        val landTicks = flight / 30 + 1
        world.queue {
            var bossSpawn: DynamicObject? = null
            try {
                wait(landTicks)
                // F2: a breach closed while the projectile flew lands on nothing.
                if (site.closed) return@queue
                bossSpawn = DynamicObject(BOSS_SPAWN_LOC, 10, 0, centre).also { site.bossSpawns += it }
                world.spawn(bossSpawn)
                world.spawn(TileGraphic(centre, GFX_BOSS_SPAWN, height = 0))
                world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = centre, id = SFX_BOSS_SPAWN, radius = 15, volume = 1))
                wait(BOSS_SPAWN_TICKS)
                if (site.closed) return@queue
                site.npcs += spawnMonster(world, monster.id, landing, if (site.kind == Kind.LOCALISED) LOCAL_SPAWN_RADIUS else REGIONAL_WANDER_RADIUS)
                wait(1)
            } catch (e: Exception) {
                gg.rsmod.game.Server.logger.error("Breach landing at {} failed: {}", landing, e.toString(), e)
            } finally {
                // F7: whatever happens, the footprint is released and the Boss Spawn disappears.
                site.pending -= reserved
                bossSpawn?.let {
                    if (site.bossSpawns.remove(it)) {
                        world.remove(it)
                        world.spawn(TileGraphic(it.tile, GFX_BOSS_DESPAWN, height = 0))
                        world.spawn(gg.rsmod.game.model.entity.AreaSound(tile = it.tile, id = SFX_BOSS_DESPAWN, radius = 15, volume = 1))
                    }
                }
            }
        }
    }

    /**
     * Client cycles (20 ms) a spawner projectile flies: 30 + 5 per tile, as before, but at least long enough to be seen and never
     * longer than the rev-667 projectile packet can hold. Regional breaches fire over up to ~40 tiles.
     */
    fun flightCycles(
        from: Tile,
        to: Tile,
    ): Int = (30 + from.getDistance(to) * 5).coerceAtMost(1000)

    /** Longest projectile flight in tiles; see [projectileStart]. */
    const val MAX_PROJECTILE_TILES = 30

    /**
     * Where a spawner projectile starts (breach audit 2026-09-27, D1). A rev-667 projectile packet reaches only the players whose
     * loaded 104x104 map holds its START tile (Chunk.sendUpdate), so a regional projectile fired 40+ tiles across the region was
     * never seen by the players standing at the landing tile. Beyond [MAX_PROJECTILE_TILES] it starts on the straight line from
     * the spawner, [MAX_PROJECTILE_TILES] tiles before the landing: it still comes from the spawner's direction and is visible
     * where it lands. ADAPTED (packet limit), localised breaches (radius 9) are unchanged.
     */
    fun projectileStart(
        spawner: Tile,
        landing: Tile,
    ): Tile {
        val dx = spawner.x - landing.x
        val dz = spawner.z - landing.z
        val far = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz))
        if (far <= MAX_PROJECTILE_TILES) return spawner
        return Tile(landing.x + dx * MAX_PROJECTILE_TILES / far, landing.z + dz * MAX_PROJECTILE_TILES / far, landing.height)
    }

    /**
     * In-engine test run 2026-09-27: the landing flood fill ([around]) walks with a 1x1 body, so a 2x2-5x5 monster could land in
     * a pocket only a 1x1 walker leaves (Cerberus stood 4 tiles from its target at a Varrock site). A large monster lands only
     * where a route for its whole body reaches next to a spawner.
     */
    fun reachesSpawner(
        world: World,
        corner: Tile,
        size: Int,
        spawners: List<Tile>,
    ): Boolean {
        if (size <= 1) return true
        val spawner = spawners.minByOrNull { it.getDistance(corner) } ?: return true
        val route =
            gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy(world.collision).calculateRoute(
                gg.rsmod.game.model.path.PathRequest.Builder().setPoints(corner, spawner).setSourceSize(size, size).setTargetSize(1, 1)
                    .setProjectilePath(false).setTouchRadius(1).clipPathNodes(node = true, link = true).clipOverlapTiles().build(),
            )
        return route.success
    }

    /**
     * A footprint that can step at least one way (breach audit 2026-09-27, B3): [fits] proves the tiles under the monster are
     * usable, not that it can move off them - a large monster could land boxed in by walls on every side.
     */
    fun canLeave(
        world: World,
        corner: Tile,
        size: Int,
    ): Boolean =
        gg.rsmod.game.model.Direction.NESW.any { dir ->
            (0 until size).all { i ->
                val edge =
                    when (dir) {
                        gg.rsmod.game.model.Direction.NORTH -> corner.transform(i, size - 1)
                        gg.rsmod.game.model.Direction.SOUTH -> corner.transform(i, 0)
                        gg.rsmod.game.model.Direction.EAST -> corner.transform(size - 1, i)
                        else -> corner.transform(0, i)
                    }
                world.collision.canStep(edge, dir)
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
        val sites = (active?.sites.orEmpty() + lingering + site).distinct()
        if (sites.any { s -> s.pending.any { it.overlaps(corner, size) } }) return false
        // F6: never on a spawner (a regional spawner stands on one of the region's own landing points).
        if (sites.any { s -> s.spawners.any { fp.overlaps(it, 1) } }) return false
        // 2026-09-28: never on a player either (a monster standing on its target cannot attack it and the player is then boxed in).
        if (playersNear(world, corner, size + 1).any { fp.overlaps(it.tile, 1) }) return false
        return noNpcOn(world, corner, size)
    }

    /** No live npc's footprint overlaps the [size] x [size] square at [corner]. */
    private fun noNpcOn(
        world: World,
        corner: Tile,
        size: Int,
    ): Boolean {
        val fp = Footprint(corner, size)
        val reach = gg.rsmod.game.model.MovementQueue.MAX_NPC_SIZE - 1
        for (x in corner.x - reach until corner.x + size) for (z in corner.z - reach until corner.z + size) {
            val t = Tile(x, z, corner.height)
            val chunk = world.chunks.get(t, createIfNeeded = false) ?: continue
            if (chunk.getEntities<Npc>(t, EntityType.NPC).any { it.tile == t && !it.isDead() && fp.overlaps(t, it.getSize().coerceAtLeast(1)) }) return false
        }
        return true
    }

    /**
     * Where a summon (Zemouregal's summons, TzTok-Jad-Rek) of [size] appears next to [target] (breach audit 2026-09-28): a usable
     * footprint touching the target, never on the target nor on another npc. It used to be any unclipped tile within one of the
     * target, so a summon could appear on the player's own tile or inside another monster and stand stuck there. Falls back to
     * the old rule when nothing around the target is free.
     */
    fun summonTile(
        world: World,
        target: Pawn,
        size: Int,
        nearestTo: Tile? = null,
    ): Tile {
        val t = target.tile
        val ts = target.getSize().coerceAtLeast(1)
        val candidates = ArrayList<Tile>()
        for (x in (t.x - size)..(t.x + ts)) for (z in (t.z - size)..(t.z + ts)) candidates += Tile(x, z, t.height)
        // A summon appears at a random free spot; a monster stepping off its target takes the nearest one (2026-09-28: a random pick
        // each tick sent Commander Zilyana in a new direction every step, so she never got off her target and never attacked).
        val ordered = if (nearestTo != null) candidates.sortedBy { maxOf(kotlin.math.abs(it.x - nearestTo.x), kotlin.math.abs(it.z - nearestTo.z)) } else candidates.shuffled()
        return ordered.firstOrNull { c ->
            !Footprint(c, size).overlaps(t, ts) && fits(world, c, size) && canLeave(world, c, size) && noNpcOn(world, c, size)
        } ?: Tile(t.x + world.random(-1..1), t.z + world.random(-1..1), t.height).takeIf { !world.collision.isClipped(it) } ?: Tile(t)
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
        test: Boolean = false,
    ): Npc {
        val npc = Npc(id, Tile(tile), world)
        npc.respawnOverride = false
        // Owner decision 2026-09-28: every breach monster roams at most ROAM_RADIUS tiles around where it appeared - breach spawns,
        // breachtest monsters (they had radius 0 and stood still) and summons alike. [walkRadius] is kept for the callers' logs only.
        npc.walkRadius = ROAM_RADIUS
        // Owner decision 2026-09-28 (live test: Derwen and "every breach monster" stood still behind a tree): EVERY breach monster
        // routes around obstacles - no safespots. ADAPTED on purpose: OSRS gives routefinding only to Zemouregal, his summons and
        // Durial321 (BreachMonsters.ROUTEFINDERS, kept as the OSRS reference); the 09-27 A2 rule made the others safespottable.
        npc.smartPathfinding = true
        // Owner 2026-09-28 (later the same day, "dat ik niet meer in breachmonsters kan staan kun je dat weghalen"): the earlier
        // blocksPlayers rule is withdrawn - players walk through breach monsters again, as in OSRS.
        npc.naivePathfinding = true
        if (id in BreachMonsters.COLLISION_EXCLUDED) npc.ignoresEntityCollision = true
        npc.attr[BREACH_NPC_ATTR] = true
        // F4: a `breachtest` monster fights like the real one but pays nothing (no loot, emblem or points).
        if (test) markTest(npc)
        npc.attr[BREACH_EVENT_ATTR] = active?.eventId ?: lastEventId
        // OSRS Wiki: Durial321 "is also capable of running after his target, unlike most NPCs"; I DSCIM YOU "runs at its targets".
        if (id in BreachMonsters.RUNNERS) npc.attr[gg.rsmod.game.model.attr.NPC_RUNS_ATTR] = true
        world.spawn(npc)
        BreachMonsters.BY_ID[id]?.let { m -> BreachMonsters.COMBAT_LEVEL_BY_OSRS[m.osrsId] }?.let { level -> if (level != npc.def.combatLevel) npc.setCombatLevel(level) }
        live += npc
        gg.rsmod.game.Server.logger.info("Breach monster {} spawned at {}.", id, tile)
        gg.rsmod.game.model.BreachTrace.log("breach.spawn") {
            "npc=$id#${npc.index} at=${tile.x},${tile.z},${tile.height} size=${npc.getSize()} walkRadius=${npc.walkRadius} pathing=${if (npc.smartPathfinding) "smart" else if (npc.naivePathfinding) "naive" else "simple"} " +
                "runs=${id in BreachMonsters.RUNNERS} test=$test hp=${npc.getCurrentLifepoints()} multi=${tile.isMulti(world)}"
        }
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
        val result =
            when {
                hitType == HitType.MELEE && m.immuneMelee -> 0
                hitType == HitType.RANGE && m.immuneRanged -> 0
                else -> damage
            }
        gg.rsmod.game.model.BreachTrace.log("damage.in") { "npc=${npc.id}#${npc.index} type=$hitType raw=$damage final=$result hp=${npc.getCurrentLifepoints()}" }
        return result
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
        gg.rsmod.game.model.BreachTrace.log("contribution") { "npc=${target.id}#${target.index} player=${source.username} +$damage total=${ledger.damageOf(source.username)} contributors=${ledger.size}" }
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
     * players with [BreachLoot.EMBLEM_DAMAGE]+ damage (Audit D-14: once per account per breach event, [claimArchaicEmblem]),
     * and Breach Points for the top [BreachPoints.EARNERS]. The monster is
     * marked rewarded first and its ledger cleared afterwards, so a second death callback, a late hit or a double click can
     * never pay twice. Players who are offline when it dies receive nothing (their slot is not handed to anyone else).
     */
    fun dropLoot(npc: Npc) {
        if (npc.attr[REWARDED_ATTR] == true) return
        npc.attr[REWARDED_ATTR] = true
        val world = npc.world
        val ledger = contribution(npc)
        val tile = Tile(npc.tile)
        if (isTestNpc(npc)) {
            gg.rsmod.game.Server.logger.info("Breach reward: test npc {} at {} - no rewards ({} contributors).", npc.id, tile, ledger.size)
            npc.attr.remove(CONTRIBUTION_ATTR)
            return
        }
        // F9: one pass over the players instead of a name lookup per contributor (up to 116 per kill).
        val online = HashMap<String, Player>()
        world.players.forEach { online[it.username.lowercase()] = it }
        var looted = 0
        ledger.lootEligible().forEach { name ->
            val player = online[name.lowercase()] ?: return@forEach
            looted++
            BreachLoot.roll(world.random).forEach drops@{ drop ->
                val def = world.definitions.getNullable(ItemDef::class.java, drop.item) ?: return@drops
                val item = if (drop.noted && def.noteLinkId > 0) def.noteLinkId else drop.item
                world.spawn(GroundItem(item, drop.amount, tile, player))
            }
            // Audit D-14: at most one Archaic emblem per account per breach event, however many monsters it helps kill.
            if (ledger.damageOf(name) >= BreachLoot.EMBLEM_DAMAGE && BreachIds.ARCHAIC_EMBLEM_TIER_5 > 0 &&
                world.definitions.getNullable(ItemDef::class.java, BreachIds.ARCHAIC_EMBLEM_TIER_5) != null &&
                claimArchaicEmblem(npc.attr[BREACH_EVENT_ATTR] ?: lastEventId, name)
            ) {
                world.spawn(GroundItem(BreachIds.ARCHAIC_EMBLEM_TIER_5, 1, tile, player))
            }
        }
        var paid = 0
        ledger.pointEarners().forEach { (name, damage) ->
            val player = online[name.lowercase()] ?: return@forEach
            if (BreachPoints.award(player, damage) > 0) paid++
        }
        gg.rsmod.game.Server.logger.info(
            "Breach reward: npc {} at {} - {} contributors, loot to {}, points to {}.",
            npc.id, tile, ledger.size, looted, paid,
        )
        npc.attr.remove(CONTRIBUTION_ATTR)
    }

    /**
     * Audit D-14: claims [account]'s Archaic emblem for breach event [eventId]. True only the first time per account and
     * event (case-insensitive, so a relog cannot claim twice); every later monster of the same event gives none.
     */
    fun claimArchaicEmblem(
        eventId: Int,
        account: String,
    ): Boolean = emblemClaims.getOrPut(eventId) { java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }.add(account.lowercase())

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

package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.mechanics.doors.DoorService
import gg.rsmod.plugins.content.mechanics.gates.GateService
import gg.rsmod.plugins.content.objs.bank_locs.BankObjects
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import kotlin.math.abs

/**
 * Bank safe zones (R03.1 "explicit bank boundaries; deposit boxes do not automatically make
 * safe bubbles").
 *
 * [MANUAL_BOUNDARIES] is the explicit, controlled source of truth - collision only SUPPORTS
 * deriving a boundary, it does not get to decide one on its own (an open door has no collision
 * at all, and a flood fill has no way to know a doorway is conceptually still the edge of the
 * room). Any bank id listed there uses exactly those tiles, nothing else. Empty by default: no
 * bank has been visually confirmed and hand-verified in this environment yet (no client), so
 * nothing is asserted as final here - this is the hook a verification pass fills in, not a
 * finished curated list.
 *
 * For every bank NOT in [MANUAL_BOUNDARIES], [floodFillRoom] derives a best-effort fallback
 * from real spawned objects - not invented coordinates - but is deliberately conservative and
 * door-aware: it stops at any tile occupied by a real door/gate object (checked against
 * [DoorService]/[GateService]'s own verified id lists, in EITHER open or closed state, since an
 * open door has no blocking collision at all and would otherwise leak the "safe" zone straight
 * into the street outside), on top of the collision-graph walk. This reduces, but does not
 * eliminate, the risk of an under- or over-sized fallback room for a bank that isn't in
 * [MANUAL_BOUNDARIES] yet - treat the fallback as provisional per-bank, not as proof.
 */
object BankZones {
    /**
     * Explicit, hand-verified bank safe-zone overrides, keyed by the exact bank object id the
     * boundary belongs to (see [BankObjects.ALL]). Populate this from an actual in-game/client
     * check, not a guess - an unverified entry here would be worse than the flood-fill
     * fallback, since it would be trusted completely instead of treated as provisional.
     */
    val MANUAL_BOUNDARIES: Map<Int, Set<Tile>> = emptyMap()

    /** Safety caps for the fallback flood fill - tightened from an earlier, looser pass;
     * real bank rooms are well under both, and a smaller cap bounds how far a door-detection
     * miss could still leak. */
    const val MAX_ROOM_TILES = 100
    const val MAX_RADIUS = 12

    private var safeTiles: HashSet<Tile> = HashSet()
    private var initialized = false

    /** Returns a short summary line for the caller to log (see [World.postLoad]). */
    fun init(world: World): String {
        val doorIds = collectDoorAndGateIds(world)
        val tiles = HashSet<Tile>()
        var bankObjectsFound = 0
        var cappedRooms = 0
        var manualCount = 0
        var fallbackCount = 0
        world.chunks.allChunks().forEach { chunk ->
            val statics = chunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            statics.forEach { obj ->
                if (obj.id in BankObjects.ALL) {
                    bankObjectsFound++
                    val manual = MANUAL_BOUNDARIES[obj.id]
                    if (manual != null) {
                        manualCount++
                        tiles.addAll(manual)
                    } else if (obj.tile !in tiles) {
                        fallbackCount++
                        val room = floodFillRoom(world, obj.tile, doorIds)
                        if (room.size >= MAX_ROOM_TILES) cappedRooms++
                        tiles.addAll(room)
                    }
                }
            }
        }
        safeTiles = tiles
        initialized = true
        // Self-check (ponytail: non-trivial logic needs one runnable check): a real bank room
        // is never 1-2 tiles. This is the exact assertion that caught this function's first,
        // broken version (seeded the flood fill on the object's own solid tile, so it could
        // never expand) automatically, instead of requiring a manual read of boot-log numbers.
        check(bankObjectsFound == 0 || tiles.size >= bankObjectsFound * 3) {
            "BankZones sanity check failed: $bankObjectsFound bank objects produced only " +
                "${tiles.size} safe tiles (<3/object average) - the flood fill is very likely " +
                "seeding on non-walkable tiles again, not a real room boundary."
        }
        return "BankZones: derived ${tiles.size} safe tiles ($manualCount from MANUAL_BOUNDARIES, " +
            "$fallbackCount from a door-aware collision flood fill, capped $cappedRooms/$fallbackCount " +
            "times) from $bankObjectsFound real bank booth/chest objects (R03.1)."
    }

    /** Every real door/gate object id, open or closed, from the already-loaded, verified
     * DoorService/GateService data files - used to stop the fallback flood fill at a doorway
     * even when that door currently has zero blocking collision (i.e. it's open). */
    private fun collectDoorAndGateIds(world: World): Set<Int> {
        val ids = HashSet<Int>()
        world.getService(DoorService::class.java)?.let { service ->
            service.doors.forEach {
                ids.add(it.closed)
                ids.add(it.opened)
            }
            service.doubleDoors.forEach {
                ids.add(it.closed.left)
                ids.add(it.closed.right)
                ids.add(it.opened.left)
                ids.add(it.opened.right)
            }
        }
        world.getService(GateService::class.java)?.let { service ->
            service.gates.forEach {
                ids.add(it.closed.hinge)
                ids.add(it.closed.extension)
                ids.add(it.opened.hinge)
                ids.add(it.opened.extension)
            }
        }
        return ids
    }

    /**
     * BFS over the actual traversable-tile graph ([World.collision]), so the resulting safe
     * zone approximates the real room a wall bounds - not a guessed radius, though still a
     * fallback, not a verified boundary (see the class doc). Stops at any [doorIds] tile
     * regardless of its current open/closed collision state, and is capped by [MAX_RADIUS]
     * (Chebyshev distance from the object) and [MAX_ROOM_TILES] so a bank chest standing in the
     * open (no walls) still gets a bounded, sane result instead of flooding indefinitely.
     *
     * A bank booth/chest is itself solid scenery - [objTile] is where the *object* stands, not
     * a tile a player can stand on, so [World.collision.isClipped] is true there and a BFS
     * seeded directly at it can never expand (verified live: the first version of this function
     * did exactly that and produced one single-tile "room" per bank, a silent near-total
     * failure a clean boot alone never surfaced). The real seed is whichever of the four
     * adjacent tiles a player can actually stand on.
     */
    private fun floodFillRoom(
        world: World,
        objTile: Tile,
        doorIds: Set<Int>,
    ): Set<Tile> {
        fun hasDoorAt(tile: Tile): Boolean =
            world.chunks
                .get(tile, createIfNeeded = false)
                ?.getEntities<StaticObject>(tile, EntityType.STATIC_OBJECT)
                ?.any { it.id in doorIds } == true

        val seeds =
            Direction.NESW
                .map { objTile.step(it) }
                .filter { !world.collision.isClipped(it) && !hasDoorAt(it) }
        if (seeds.isEmpty()) {
            return if (!world.collision.isClipped(objTile)) setOf(objTile) else emptySet()
        }
        val visited = LinkedHashSet<Tile>()
        val queue = ArrayDeque<Tile>()
        seeds.forEach {
            visited.add(it)
            queue.add(it)
        }
        while (queue.isNotEmpty() && visited.size < MAX_ROOM_TILES) {
            val current = queue.removeFirst()
            for (direction in Direction.NESW) {
                if (!world.collision.canTraverse(current, direction, projectile = false, water = false)) {
                    continue
                }
                val next = current.step(direction)
                if (next in visited) continue
                if (hasDoorAt(next)) continue
                if (abs(next.x - objTile.x) > MAX_RADIUS || abs(next.z - objTile.z) > MAX_RADIUS) continue
                visited.add(next)
                queue.add(next)
                if (visited.size >= MAX_ROOM_TILES) break
            }
        }
        visited.add(objTile)
        return visited
    }

    fun isSafe(tile: Tile): Boolean = initialized && tile in safeTiles
}

/**
 * The single authoritative area-state predicate for PvP permission (R03.3), replacing the
 * old Wilderness-only [BountyHunterHome.isDangerousWilderness] check as the gate for whether
 * two players are allowed to fight. Death/loot-risk classification ([gg.rsmod.plugins.content.mechanics.death.DeathResolver])
 * deliberately still keys off Wilderness specifically - R08.1 requires preserving established
 * Wilderness location-risk rules and treating non-Wilderness PvP death policy as provisional,
 * so widening combat permission here does NOT change item-risk on death.
 */
object AreaState {
    /** True if [tile] is inside any safe zone: the home enclave or a real bank building. */
    fun isSafe(
        tile: Tile,
        home: Tile,
    ): Boolean = BountyHunterHome.isSafe(tile, home) || BankZones.isSafe(tile)

    /** R03.1: PvP is allowed everywhere outside safe zones - not only in the Wilderness. */
    fun isPvpAllowed(
        tile: Tile,
        home: Tile,
    ): Boolean = !isSafe(tile, home)

    fun canPlayersFight(
        attacker: gg.rsmod.game.model.entity.Player,
        target: gg.rsmod.game.model.entity.Player,
    ): Boolean {
        // R14.23: covers targeting in BOTH directions - a protected player can neither attack
        // nor be attacked. The only way out is BeginnerProtection.forfeit (R14.25's explicit
        // consent flow in combat.plugin.kts), which runs before this is next evaluated.
        if (BeginnerProtection.isProtected(attacker) || BeginnerProtection.isProtected(target)) {
            return false
        }
        val world = attacker.world
        val home = world.gameContext.home
        return (isPvpAllowed(attacker.tile, home) && isPvpAllowed(target.tile, home)) ||
            PracticePvp.areMatched(attacker, target)
    }
}

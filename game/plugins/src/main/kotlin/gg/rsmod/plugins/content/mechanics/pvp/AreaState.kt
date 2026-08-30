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
 * from three INDEPENDENT real signals - not invented coordinates, and not collision alone:
 * 1. The actual traversable-tile graph ([World.collision]).
 * 2. Real door/gate objects ([DoorService]/[GateService]'s own verified id lists), checked in
 *    EITHER open or closed state, since an open door has zero blocking collision and would
 *    otherwise leak the "safe" zone straight into the street outside.
 * 3. The cache's own roofed/indoor tile flag (`CollisionManager.ROOF_TILE`, now read out into
 *    [gg.rsmod.game.model.region.Chunk.isRoofed] - a real map flag this codebase already
 *    declared but never used before this pass). When the bank object sits somewhere the cache
 *    marks as roofed, the fill is restricted to roofed tiles only - a real bank interior is
 *    roofed, a street is not, so this is a strong, independent signal against exactly the
 *    "street becomes safe" failure mode a wall/door check alone cannot fully rule out. Where no
 *    roof signal exists (an open-air bank stall, or a region with no roof data), this
 *    restriction does not apply and the fill falls back to door+collision only.
 *
 * Even with three signals, this remains a best-effort fallback for banks not in
 * [MANUAL_BOUNDARIES], not a verified boundary - [init] rejects (drops to empty, logs a
 * warning, never silently trusts) any fallback room whose own roofed-tile ratio looks
 * inconsistent, and reports capped/rejected counts honestly rather than asserting success.
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
        var roofRestrictedCount = 0
        var rejectedCount = 0
        world.chunks.allChunks().forEach chunkLoop@{ chunk ->
            val statics = chunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            statics.forEach objLoop@{ obj ->
                if (obj.id in BankObjects.ALL) {
                    bankObjectsFound++
                    val manual = MANUAL_BOUNDARIES[obj.id]
                    if (manual != null) {
                        manualCount++
                        tiles.addAll(manual)
                    } else if (obj.tile !in tiles) {
                        fallbackCount++
                        val result = floodFillRoom(world, obj.tile, doorIds)
                        if (result.room.size >= MAX_ROOM_TILES) cappedRooms++
                        if (result.roofRestricted) roofRestrictedCount++
                        // Purity self-check: a roof-restricted room should be ~entirely roofed
                        // by construction (every accepted tile passed the roofed check), so a
                        // low ratio here means the roof signal itself looked inconsistent for
                        // this bank (e.g. missing roof data mid-room) - reject rather than trust
                        // a room this check cannot vouch for, log it, and leave that bank
                        // uncovered (safe-by-omission) instead of risking a street tile.
                        if (result.roofRestricted && result.room.size >= 3) {
                            val roofedCount = result.room.count { isRoofedAt(world, it) }
                            if (roofedCount < result.room.size * 9 / 10) {
                                rejectedCount++
                                return@objLoop
                            }
                        }
                        tiles.addAll(result.room)
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
        check(bankObjectsFound == 0 || tiles.size >= (bankObjectsFound - rejectedCount) * 3) {
            "BankZones sanity check failed: $bankObjectsFound bank objects produced only " +
                "${tiles.size} safe tiles (<3/object average) - the flood fill is very likely " +
                "seeding on non-walkable tiles again, not a real room boundary."
        }
        return "BankZones: derived ${tiles.size} safe tiles ($manualCount from MANUAL_BOUNDARIES, " +
            "$fallbackCount from a door+roof-aware collision flood fill [$roofRestrictedCount " +
            "roof-restricted, $cappedRooms/$fallbackCount hit the size cap, $rejectedCount " +
            "rejected by the roof-purity check and left uncovered]) from $bankObjectsFound real " +
            "bank booth/chest objects (R03.1). Still a best-effort fallback where no " +
            "MANUAL_BOUNDARIES entry exists - not a verified boundary."
    }

    private fun isRoofedAt(
        world: World,
        tile: Tile,
    ): Boolean = world.chunks.get(tile, createIfNeeded = false)?.isRoofed(tile) == true

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

    private data class FloodResult(
        val room: Set<Tile>,
        val roofRestricted: Boolean,
    )

    /**
     * BFS over the actual traversable-tile graph ([World.collision]), so the resulting safe
     * zone approximates the real room a wall bounds - not a guessed radius, though still a
     * fallback, not a verified boundary (see the class doc). Stops at any [doorIds] tile
     * regardless of its current open/closed collision state, and is capped by [MAX_RADIUS]
     * (Chebyshev distance from the object) and [MAX_ROOM_TILES] so a bank chest standing in the
     * open (no walls) still gets a bounded, sane result instead of flooding indefinitely.
     *
     * If at least one of the object's real walkable neighbour tiles is roofed (per the cache's
     * own indoor flag - see the class doc), the whole fill is restricted to roofed tiles only:
     * a real bank interior is roofed, a street is not, so this stops the fill from crossing an
     * open doorway into the street even when the door object itself wasn't recognised. Where no
     * roofed neighbour exists, there is no roof signal to use (open-air bank, or the region
     * simply has no roof data) and the fill falls back to door+collision only.
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
    ): FloodResult {
        fun hasDoorAt(tile: Tile): Boolean =
            world.chunks
                .get(tile, createIfNeeded = false)
                ?.getEntities<StaticObject>(tile, EntityType.STATIC_OBJECT)
                ?.any { it.id in doorIds } == true

        val allSeeds =
            Direction.NESW
                .map { objTile.step(it) }
                .filter { !world.collision.isClipped(it) && !hasDoorAt(it) }
        if (allSeeds.isEmpty()) {
            val room = if (!world.collision.isClipped(objTile)) setOf(objTile) else emptySet()
            return FloodResult(room, roofRestricted = false)
        }

        val roofRestricted = allSeeds.any { isRoofedAt(world, it) }
        val seeds = if (roofRestricted) allSeeds.filter { isRoofedAt(world, it) } else allSeeds

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
                if (roofRestricted && !isRoofedAt(world, next)) continue
                if (abs(next.x - objTile.x) > MAX_RADIUS || abs(next.z - objTile.z) > MAX_RADIUS) continue
                visited.add(next)
                queue.add(next)
                if (visited.size >= MAX_ROOM_TILES) break
            }
        }
        visited.add(objTile)
        return FloodResult(visited, roofRestricted)
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

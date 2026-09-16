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
 * room). Any bank INSTANCE listed there uses exactly those tiles, nothing else. Empty by
 * default: no bank has been visually confirmed and hand-verified in this environment yet (no
 * client), so nothing is asserted as final here - this is the hook a verification pass fills
 * in, not a finished curated list.
 *
 * Keyed by the bank object's own tile (audit finding 12), not its object id: the same bank
 * booth/chest object id is reused at many physical banks across the map, so keying by id alone
 * would apply one verified boundary to every bank sharing that id - a real safety-zone bug the
 * moment this map is first populated, not a hypothetical. A tile already uniquely identifies a
 * specific instance (no two bank objects occupy the same tile), so no extra key component is
 * needed.
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
     * Explicit, hand-verified bank safe-zone overrides, keyed by the exact tile of the specific
     * bank object instance (see [BankObjects.ALL] for which ids count as a bank) the boundary
     * belongs to - never by object id alone (finding 12: the same id is reused at many banks).
     * Populate this from an actual in-game/client check, not a guess - an unverified entry here
     * would be worse than the flood-fill fallback, since it would be trusted completely instead
     * of treated as provisional.
     */
    val MANUAL_BOUNDARIES: Map<Tile, Set<Tile>> = emptyMap()

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
                    val manual = MANUAL_BOUNDARIES[obj.tile]
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
 * two players are allowed to fight. Death/loot-risk classification is deliberately separate:
 * [gg.rsmod.plugins.content.mechanics.death.DeathResolver] follows credited player cause, while
 * this area predicate remains responsible for safe-bank/home permission and location-only rules
 * such as loot-key destruction.
 */
object AreaState {
    /**
     * True if [tile] is inside a Deadman guarded city ([GuardedZones]). Owner instruction
     * 2026-09-16: safe/death zones follow the OSRS Deadman Mode map - whole guarded cities are
     * safe, every other place (Wilderness, Ferox, Edgeville/Draynor/Al Kharid banks, ...) is a
     * death zone. Supersedes the earlier bank-room-only model.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isSafe(
        tile: Tile,
        home: Tile,
    ): Boolean = GuardedZones.contains(tile)

    /** PvP is allowed everywhere outside the guarded cities - not only in the Wilderness. */
    fun isPvpAllowed(
        tile: Tile,
        home: Tile,
    ): Boolean = !isSafe(tile, home)

    /**
     * Deadman PvP guards plan (owner-approved 2026-09-16): a player may only attack another
     * player up to [MAX_COMBAT_LEVEL_DIFFERENCE] combat levels above or below them. This is a
     * flat, location-independent range - not the traditional OSRS Wilderness-level-scaled range
     * - because PvP in this build is allowed everywhere outside explicit bank safe zones (R03.1),
     * not only in the Wilderness, so a range that scales with Wilderness depth would give no
     * protection at all outside the Wilderness. Superseded here: the previous per-tile
     * Wilderness-level-scaled check in [gg.rsmod.plugins.content.combat.Combat] now delegates to
     * this single shared constant/comparison instead of running its own formula.
     */
    const val MAX_COMBAT_LEVEL_DIFFERENCE = 12

    fun isWithinCombatLevelRange(
        attacker: gg.rsmod.game.model.entity.Player,
        target: gg.rsmod.game.model.entity.Player,
    ): Boolean = abs(attacker.combatLevel - target.combatLevel) <= MAX_COMBAT_LEVEL_DIFFERENCE

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
        // Deadman PvP guards plan (2026-09-16): a player currently protected by the post-kill
        // grace period cannot be attacked at all, by anyone - checked unconditionally like
        // BeginnerProtection above, not folded into the practice-PvP-exempt clause below.
        if (KillGrace.isProtected(target)) {
            return false
        }
        val world = attacker.world
        val home = world.gameContext.home
        // Practice PvP matches are randomly queued (no level-matching) and are a consequence-free
        // sandbox, so a matched pair bypasses both the safe-zone gate and the level-range gate -
        // the same exemption the safe-zone gate already had.
        return (isPvpAllowed(attacker.tile, home) && isPvpAllowed(target.tile, home) && isWithinCombatLevelRange(attacker, target)) ||
            PracticePvp.areMatched(attacker, target)
    }
}

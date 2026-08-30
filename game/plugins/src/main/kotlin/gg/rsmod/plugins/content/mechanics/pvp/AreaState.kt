package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.objs.bank_locs.BankObjects
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import kotlin.math.abs

/**
 * Real, cache-verified bank safe zones (R03.1 "explicit bank boundaries; deposit boxes do
 * not automatically make safe bubbles"). Rather than hand-authoring coordinates for every
 * bank in the game - unverifiable without a full map decode - this scans every actually
 * spawned instance of a real bank booth/chest ([BankObjects.ALL], the same ids already bound
 * to the Bank interface) once at world-init and flood-fills the REAL walkable room around each
 * using the already-loaded collision data ([World.collision]), stopping at actual walls. A
 * fixed radius bubble proves nothing about where a bank's real walls are - it can leak past a
 * thin wall into the street, or fail to cover a large banking hall - so this walks the same
 * traversability graph the pathfinder itself uses, capped so an outdoor/wall-less bank chest
 * can't flood the whole map.
 */
object BankZones {
    /** Safety caps for the flood fill - real bank rooms are well under both of these. */
    const val MAX_ROOM_TILES = 200
    const val MAX_RADIUS = 20

    private var safeTiles: HashSet<Tile> = HashSet()
    private var initialized = false

    /** Returns a short summary line for the caller to log (see [World.postLoad]). */
    fun init(world: World): String {
        val tiles = HashSet<Tile>()
        var bankObjectsFound = 0
        var cappedRooms = 0
        world.chunks.allChunks().forEach { chunk ->
            val statics = chunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            statics.forEach { obj ->
                if (obj.id in BankObjects.ALL) {
                    bankObjectsFound++
                    if (obj.tile !in tiles) {
                        val room = floodFillRoom(world, obj.tile)
                        if (room.size >= MAX_ROOM_TILES) cappedRooms++
                        tiles.addAll(room)
                    }
                }
            }
        }
        safeTiles = tiles
        initialized = true
        // Self-check (ponytail: non-trivial logic needs one runnable check): a real bank room
        // is never 1-2 tiles. This is the exact assertion that would have caught this
        // function's first, broken version (seeded the flood fill on the object's own solid
        // tile, so it could never expand) automatically, instead of requiring a manual read of
        // the boot log's numbers to notice 86 objects producing only 86 tiles.
        check(bankObjectsFound == 0 || tiles.size >= bankObjectsFound * 3) {
            "BankZones sanity check failed: $bankObjectsFound bank objects produced only " +
                "${tiles.size} safe tiles (<3/object average) - the flood fill is very likely " +
                "seeding on non-walkable tiles again, not a real room boundary."
        }
        return "BankZones: derived ${tiles.size} safe tiles (real-collision flood fill, capped " +
            "$cappedRooms/${bankObjectsFound} times) from $bankObjectsFound real bank " +
            "booth/chest objects (R03.1)."
    }

    /**
     * BFS over the actual traversable-tile graph ([World.collision]), so the resulting safe
     * zone is the real room a wall genuinely bounds - not a guessed radius. Capped by
     * [MAX_RADIUS] (Chebyshev distance from the object) and [MAX_ROOM_TILES] so a bank chest
     * standing in the open (no walls) still gets a bounded, sane safe zone instead of flooding
     * indefinitely.
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
    ): Set<Tile> {
        val seeds = Direction.NESW.map { objTile.step(it) }.filter { !world.collision.isClipped(it) }
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

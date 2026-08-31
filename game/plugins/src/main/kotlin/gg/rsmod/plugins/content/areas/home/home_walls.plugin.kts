package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject

/**
 * R02.1/R14.1/R14.3/R14.4/HOME_DESIGN_2.png: the full visible wall perimeter connecting the 4
 * gates, shaped as the confirmed design's real OCTAGONAL ruin (not a plain square) via
 * [BountyHunterHome.octagonRing] - 4 shortened straight edges plus 4 diagonal corner-cut
 * staircases, all real integer tiles, no new client assets needed. [BountyHunterHome.safeArea]
 * traces the identical shape, so the safety classification and the physical wall can never
 * disagree about which tiles are inside.
 *
 * Every tile on the octagon ring (the 4 gate tiles included - already blocked, this is
 * idempotent there) is blocked in all 4 directions, so the 4 gates are the ONLY way across.
 *
 * Audit finding 1 fix: [Objs.WILDERNESS_WALL] (1440-1444) was the WRONG object for a per-tile
 * perimeter wall on two independent counts, both confirmed via a boot-time [ObjectDef] dump of
 * this codebase's own loaded cache data (not the earlier guess): (a) semantically it is the real
 * Wilderness DITCH-crossing prop ("Cross" option, already bound in `wilderness_wall.plugin.kts`
 * to force-move a player across the ditch bank) - not a connected boundary wall at all; (b) its
 * real footprint is 3x2 tiles, so placing one on every single perimeter tile massively
 * overlapped its neighbours. [Objs.CRUMBLING_WALL] (1948) is used instead: confirmed via the
 * same dump to be a real 1x1-footprint, solid, impenetrable object literally named "Crumbling
 * wall" - exactly the "vervallen ruïne" material R14.4 asks for, and small enough to place one
 * per perimeter tile with no overlap. Its own "Climb-over" option is deliberately left UNBOUND
 * here (only the 4 real gates are crossable) - see `objs/wilderness_wall.plugin.kts` for where
 * that option IS bound, on the unrelated ditch object family.
 *
 * Object placement type is set to 0 ([ObjectType.LENGTHWISE_WALL], matching [Objs.GATE]'s own
 * placement type in `home_gates.plugin.kts`), by analogy with the audit's confirmed finding that
 * the PvM arena entrance's correct cache placement type was 0, not 10.
 *
 * Per-edge rotation/type (R14.4 remainder): each of the 4 straight edges and 4 diagonal corner
 * tiles now gets the type/rot the engine's own wall renderer actually expects for that facing,
 * derived directly from this codebase's real collision code
 * ([gg.rsmod.game.model.collision.CollisionUpdate.Builder.putObject]/[Direction.WNES]/
 * [Direction.WNES_DIAGONAL]) rather than a uniform placeholder:
 * - Straight edges: type 0 ([ObjectType.LENGTHWISE_WALL]); rot is the [Direction.WNES] index of
 *   the direction the wall FACES (north edge -> NORTH -> rot 1, south -> SOUTH -> rot 3,
 *   east -> EAST -> rot 2, west -> WEST -> rot 0).
 * - The 4 corner-cut diagonal tiles: type 1 ([ObjectType.TRIANGULAR_CORNER]); rot is the
 *   [Direction.WNES_DIAGONAL] index of the corner's own diagonal direction (NE -> rot 1,
 *   SE -> rot 2, SW -> rot 3, NW -> rot 0).
 * Collision is unaffected either way (this file blocks NESW on every ring tile directly, not via
 * the object's own def) - this only fixes which real cache wall-type/facing gets rendered. Still
 * disclosed as visually unverified (no client capture available this session, see
 * OWNER_TASK_STATUS.md), but now grounded in the engine's real wall-type semantics instead of a
 * uniform guess.
 */
val wallHome = world.gameContext.home

on_world_init {
    // BATCH 1 terrain evidence check: before this file adds its OWN collision, sample the whole
    // candidate interior for PRE-EXISTING blocked terrain (rock/water/building already in the 667
    // cache at Tile(3140,3616,0), SAFE_RADIUS=24) - the owner's instruction was to only shift the
    // home centre if real collision/terrain evidence proved this preferred candidate unworkable.
    // A mostly-clear interior IS that evidence check passing; a heavily-blocked one would fail
    // loudly here instead of silently shipping a broken enclave.
    val interior = mutableListOf<Tile>()
    for (x in -BountyHunterHome.SAFE_RADIUS..BountyHunterHome.SAFE_RADIUS) {
        for (z in -BountyHunterHome.SAFE_RADIUS..BountyHunterHome.SAFE_RADIUS) {
            val tile = wallHome.transform(x, z)
            if (BountyHunterHome.isSafe(tile, wallHome)) interior.add(tile)
        }
    }
    val preBlocked = interior.count { world.collision.isClipped(it) }
    val preBlockedPct = preBlocked * 100.0 / interior.size
    println(
        "home_walls: terrain evidence at candidate centre $wallHome (SAFE_RADIUS=" +
            "${BountyHunterHome.SAFE_RADIUS}): $preBlocked/${interior.size} interior tiles " +
            "(%.1f%%) were already collision-blocked by real cache terrain/objects BEFORE this ".format(preBlockedPct) +
            "file's own walls were added.",
    )
    check(preBlockedPct < 10.0) {
        "home_walls: $preBlocked/${interior.size} (%.1f%%) of the candidate interior is already ".format(preBlockedPct) +
            "blocked by real terrain - this is evidence the owner's preferred centre/radius does " +
            "NOT fit here and the location must be shifted, per the explicit 'only shift when " +
            "evidence proves necessary' instruction."
    }

    val ring = BountyHunterHome.octagonRing(wallHome)

    val builder = CollisionUpdate.Builder()
    builder.setType(CollisionUpdate.Type.ADD)
    ring.forEach { tile -> builder.putTile(tile, false, *Direction.NESW) }
    world.collision.applyUpdate(builder.build())

    val blockedCount = ring.count { world.collision.isClipped(it) }
    check(blockedCount == ring.size) {
        "home_walls: only $blockedCount/${ring.size} perimeter tiles ended up blocked - the " +
            "collision update failed to apply, the enclave is NOT actually enclosed."
    }

    val gateTileSet = BountyHunterHome.gateTiles(wallHome).toHashSet()
    var wallObjectsPlaced = 0
    ring.forEach { tile ->
        if (tile !in gateTileSet) {
            // R02.1 spawn-phase bug fix: `spawn_obj()` only appends to the plugin
            // repository's `objSpawns` list, which is flushed to the live world exactly
            // once, during `PluginRepository.init()` - BEFORE `on_world_init` callbacks
            // ever run (`World.postLoad()` calls `executeWorldInit()` strictly after
            // `Server.kt` calls `plugins.init()`). Any `spawn_obj()` called from inside
            // `on_world_init` (as this block did) queues into a list nobody reads again -
            // the object is never passed to `world.spawn()`, so it never renders, even
            // though this block's own `CollisionUpdate` (applied directly, not queued)
            // took effect correctly. That mismatch - real collision, invisible wall - is
            // exactly what was reported. Fix: spawn the object directly via `world.spawn`,
            // which is the same live-application path `spawnTemporaryObject` already uses.
            val r = BountyHunterHome.SAFE_RADIUS
            val dx = tile.x - wallHome.x
            val dz = tile.z - wallHome.z
            // Straight edges first (also covers the vertex tiles the corner staircases share with
            // them); only the lone middle diagonal tile of each corner cut falls through to the
            // corner cases below.
            val (wallType, wallRot) =
                when {
                    dz == r -> ObjectType.LENGTHWISE_WALL.value to 1 // north edge, faces NORTH
                    dz == -r -> ObjectType.LENGTHWISE_WALL.value to 3 // south edge, faces SOUTH
                    dx == r -> ObjectType.LENGTHWISE_WALL.value to 2 // east edge, faces EAST
                    dx == -r -> ObjectType.LENGTHWISE_WALL.value to 0 // west edge, faces WEST
                    dx > 0 && dz > 0 -> ObjectType.TRIANGULAR_CORNER.value to 1 // NE corner
                    dx > 0 && dz < 0 -> ObjectType.TRIANGULAR_CORNER.value to 2 // SE corner
                    dx < 0 && dz < 0 -> ObjectType.TRIANGULAR_CORNER.value to 3 // SW corner
                    else -> ObjectType.TRIANGULAR_CORNER.value to 0 // NW corner
                }
            world.spawn(DynamicObject(Objs.CRUMBLING_WALL, wallType, wallRot, tile))
            wallObjectsPlaced++
        }
    }

    println(
        "home_walls: verified all ${ring.size} perimeter tiles are real collision barriers " +
            "(4 are the gates), placed $wallObjectsPlaced visible wall objects.",
    )
}

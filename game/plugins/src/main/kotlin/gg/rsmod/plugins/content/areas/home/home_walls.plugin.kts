package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionUpdate
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
 * Object placement type is set to 0 (matching [Objs.GATE]'s own placement type in
 * `home_gates.plugin.kts`), by analogy with the audit's confirmed finding that the PvM arena
 * entrance's correct cache placement type was 0, not 10 - this environment still has no visual
 * capture of the rendered result (see OWNER_TASK_STATUS.md), so this is disclosed as the most
 * plausible choice given the available evidence, not a visually confirmed one. Rotation is left
 * at 0 uniformly for the same disclosed reason as before: getting it wrong only affects
 * appearance, never collision.
 */
val wallHome = world.gameContext.home

on_world_init {
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
            world.spawn(DynamicObject(Objs.CRUMBLING_WALL, 0, 0, tile))
            wallObjectsPlaced++
        }
    }

    println(
        "home_walls: verified all ${ring.size} perimeter tiles are real collision barriers " +
            "(4 are the gates), placed $wallObjectsPlaced visible wall objects.",
    )
}

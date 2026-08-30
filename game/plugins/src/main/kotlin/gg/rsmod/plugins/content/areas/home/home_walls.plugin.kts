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
 * [Objs.WILDERNESS_WALL] is used for the visible wall model - a real, verified 2011 object (the
 * actual Wilderness boundary wall, already used elsewhere in this codebase for the ditch
 * crossing) with exactly the "ruined Wilderness aesthetic" R14.4 asks for. Rotation is left at 0
 * uniformly (most visibly approximate on the diagonal corner tiles) rather than computed per
 * edge: this environment cannot visually confirm which rotation value orients the model
 * correctly, and getting it wrong would only affect appearance, not collision - a known, honest
 * art simplification, not a guessed functional detail.
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
            world.spawn(DynamicObject(Objs.WILDERNESS_WALL, 0, 0, tile))
            wallObjectsPlaced++
        }
    }

    println(
        "home_walls: verified all ${ring.size} perimeter tiles are real collision barriers " +
            "(4 are the gates), placed $wallObjectsPlaced visible wall objects.",
    )
}

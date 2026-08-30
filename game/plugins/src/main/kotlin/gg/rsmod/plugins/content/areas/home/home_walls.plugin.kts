package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.CollisionUpdate

/**
 * R02.1/R14.1/R14.3/R14.4: the full visible wall perimeter connecting the 4 gates - the
 * "visible walls/barriers and safe collision boundary agree" requirement `home_gates.plugin.kts`
 * (the gates alone) explicitly left open, since blindly placing wall-segment objects without any
 * way to visually confirm rotation was flagged as a real risk. That risk was about relying on an
 * OBJECT's own collision (rotation-dependent, unverifiable here) - it does not apply to directly
 * applying [CollisionUpdate] the same way `home_gates.plugin.kts` already does and self-checking
 * with [world.collision.isClipped] afterwards, which needs no visual confirmation to be correct.
 *
 * Every tile on the boundary square (Chebyshev radius [BountyHunterHome.SAFE_RADIUS] from home,
 * the 4 gate tiles included - already blocked, this is idempotent there) is blocked in all 4
 * directions, so the 4 gates are the ONLY way across. [Objs.WILDERNESS_WALL] is used for the
 * visible wall model - a real, verified 2011 object (the actual Wilderness boundary wall,
 * already used elsewhere in this codebase for the ditch crossing) with exactly the "ruined
 * Wilderness aesthetic" R14.4 asks for. Rotation is left at 0 uniformly rather than computed
 * per edge: this environment cannot visually confirm which rotation value orients the model
 * correctly, and getting it wrong would only affect appearance, not collision - the collision
 * block is applied directly and does not depend on the object's own orientation at all, so an
 * uncertain visual rotation cannot silently break the actual barrier the way it would have
 * before this session's collision self-check discipline. A known, honest art simplification,
 * not a guessed functional detail.
 */
val wallHome = world.gameContext.home
val wallRadius = BountyHunterHome.SAFE_RADIUS

on_world_init {
    val ring = LinkedHashSet<Tile>()
    for (x in -wallRadius..wallRadius) {
        ring.add(wallHome.transform(x, wallRadius))
        ring.add(wallHome.transform(x, -wallRadius))
    }
    for (z in -wallRadius..wallRadius) {
        ring.add(wallHome.transform(wallRadius, z))
        ring.add(wallHome.transform(-wallRadius, z))
    }

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
            spawn_obj(obj = Objs.WILDERNESS_WALL, x = tile.x, z = tile.z, height = tile.height, type = 0, rot = 0)
            wallObjectsPlaced++
        }
    }

    println(
        "home_walls: verified all ${ring.size} perimeter tiles are real collision barriers " +
            "(4 are the gates), placed $wallObjectsPlaced visible wall objects.",
    )
}

package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.ForcedMovement
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.collision.CollisionUpdate

/**
 * R14.3/R02.1: four REAL, collision-verified barriers at the home enclave's safe-boundary
 * edges - not decorative-only, not a teleport, and not resting on an unverified assumption.
 *
 * Each gate tile is blocked in all 4 directions via the same low-level [CollisionUpdate] call
 * [gg.rsmod.game.fs.DefinitionSet.createRegion] itself uses to build real map walls from cache
 * tile data - not via [Objs.GATE]'s own object definition's `solid` flag, which this
 * environment has no way to visually confirm is even set. A boot that doesn't crash proves
 * nothing about whether the object actually blocks anything (a real, previously-shipped gap in
 * this exact file - see git history), so this self-checks with [world.collision.isClipped]
 * right after applying the block and fails loudly (not silently) if it didn't take.
 *
 * "Open" force-moves the player across the still-blocked tile - the identical mechanic already
 * proven live in `wilderness_wall.plugin.kts`'s ditch crossing (`Player.forceMove` only touches
 * the block buffer/tile position, it does not consult collision) - so ordinary walking still
 * cannot cross the barrier; only the explicit option can.
 */
val home = world.gameContext.home
val gateTiles = BountyHunterHome.gateTiles(home)

on_world_init {
    val builder = CollisionUpdate.Builder()
    builder.setType(CollisionUpdate.Type.ADD)
    gateTiles.forEach { gate -> builder.putTile(gate, false, *Direction.NESW) }
    world.collision.applyUpdate(builder.build())

    val blockedCount = gateTiles.count { world.collision.isClipped(it) }
    check(blockedCount == gateTiles.size) {
        "home_gates: only $blockedCount/${gateTiles.size} gate tiles ended up blocked - " +
            "the collision update failed to apply, this is NOT a real barrier."
    }
    println("home_gates: verified all ${gateTiles.size} gate tiles are real, collision-checked barriers.")
}

gateTiles.forEach { gate ->
    spawn_obj(obj = Objs.GATE, x = gate.x, z = gate.z, height = gate.height, type = 0, rot = 0)
}

on_obj_option(obj = Objs.GATE, option = "open") {
    val gate = player.getInteractingGameObj().tile
    val outward = player.tile.getDistance(home) <= BountyHunterHome.SAFE_RADIUS
    val direction =
        when {
            gate.z > home.z -> if (outward) Direction.NORTH else Direction.SOUTH
            gate.z < home.z -> if (outward) Direction.SOUTH else Direction.NORTH
            gate.x > home.x -> if (outward) Direction.EAST else Direction.WEST
            else -> if (outward) Direction.WEST else Direction.EAST
        }
    val endTile = player.tile.step(direction, 2)
    player.faceTile(endTile)
    player.queue {
        player.stopMovement()
        player.forceMove(
            this,
            ForcedMovement.of(
                player.tile,
                endTile,
                clientDuration1 = 33,
                clientDuration2 = 60,
                directionAngle = direction.ordinal,
                lockState = LockState.FULL,
            ),
        )
    }
}

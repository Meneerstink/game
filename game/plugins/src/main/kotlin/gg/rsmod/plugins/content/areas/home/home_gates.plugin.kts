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
 *
 * Audit finding 3 fix: [Objs.GATE] (id 37) is an ordinary, non-unique cache object id that could
 * in principle exist anywhere else on the map. The old handler bound "open" globally for that
 * id and derived BOTH the crossing direction AND the landing tile from the clicking player's
 * OWN position (`player.tile + 2`) - an uncontrolled destination, and one that would fire for
 * ANY Gate(37) a player might click, not just the 4 real home gates. The handler below looks up
 * the clicked object's tile against [BountyHunterHome.gates] and no-ops if it isn't one of the
 * 4 real home gates, then force-moves to that gate's own fixed, pre-validated landing tile.
 */
val home = world.gameContext.home
val gates = BountyHunterHome.gates(home)
val gateTiles = gates.map { it.tile }

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
    val clickedTile = player.getInteractingGameObj().tile
    val gate = gates.find { it.tile == clickedTile } ?: return@on_obj_option
    val goingOutward = player.tile.getDistance(home) <= BountyHunterHome.SAFE_RADIUS
    val endTile = if (goingOutward) gate.outerLanding else gate.innerLanding
    val direction = if (goingOutward) gate.direction else gate.direction.getOpposite()
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

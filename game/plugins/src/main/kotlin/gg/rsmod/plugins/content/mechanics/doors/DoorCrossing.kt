package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectGroup
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.api.ext.message

/**
 * Where a player crosses a door-like loc to, from where he stands - one shared rule for every "walk through the door" script
 * (generic walkthrough doors, Barrows tunnel doors, pickable doors).
 *
 * Owner priority #1 2026-10-03 (Barrows 00:26 "noclip again": stuck at 3562,9710, no step possible). The old per-script math
 * took the axis from the rotation the wrong way round and always stepped to a neighbour of the door tile: a wall door (shape 0)
 * with rotation 2 sits on the EAST face of its tile, so it is crossed along x between its own tile and the tile east of it - the
 * script walked along z instead, with collision off, into the walled pocket south of the door. The 667 client flags a straight
 * wall by rotation 0 west, 1 north, 2 east, 3 south (`CollisionMap.flagWall`), and the server the same ([Direction.WNES]).
 *
 * - Wall doors (shapes 0-3): the far side is the door's own tile when the player stands on the tile the face looks at, and that
 *   tile when he stands on the door tile. Any other position is not a crossing (null): the caller refuses, it never walks a
 *   collision-free route from somewhere else.
 * - Diagonal wall doors (shape 9) and free-standing doors / gates (shapes 10-11, 1x1): the tile mirrored through the door tile,
 *   from a cardinally adjacent tile; anything else is null.
 */
object DoorCrossing {
    fun faceDirection(rot: Int): Direction = Direction.WNES[rot and 0x3]

    /**
     * The whole bounded crossing: the one collision-free step across [door] from where [player] stands, returning only once he
     * really stands on the far side (true) or when there is no crossing from here / the step did not happen (false, "The door
     * is blocked."). A bare `wait(1)` after the walk is checked once more in the cycle it was created in (the first suspension
     * of a task resumes a cycle early, plan 8B), which judged the step before the movement queue had taken it - the owner's
     * client crossed and still read "The door is blocked." (2026-10-03 01:32, Barrows door 6721).
     */
    suspend fun cross(
        task: gg.rsmod.game.model.queue.QueueTask,
        player: gg.rsmod.game.model.entity.Player,
        door: GameObject,
    ): Boolean {
        val destination = destination(player.tile, door)
        if (destination == null) {
            player.message("The door is blocked.")
            return false
        }
        val route = player.walkTo(task, destination, detectCollision = false)
        var waited = 0
        while (!player.tile.sameAs(destination) && waited < 4) {
            task.wait(1)
            waited++
        }
        if (!route.success || !player.tile.sameAs(destination)) {
            player.stopMovement()
            player.message("The door is blocked.")
            return false
        }
        return true
    }

    /**
     * Void `enterDoor` for every hand-written door / gate script (owner hard rule 2026-10-03 E17, "a player may never noclip";
     * audit-2026-10-03-forcedmove-vs-donors.md: about twenty area scripts walked a collision-free line from wherever the click left
     * the player, some two tiles past the door): walk with collision to the tile in front of the door on the player's side and
     * arrive there, show the door open ([openedId], back after [openTicks]), then the one bounded step through ([cross]).
     * [farSide] (optional) is where the script's own rule says the player must end up; a crossing that does not end there is
     * refused. Returns false (nothing crossed, the player stands where he was) when no crossing is possible.
     */
    suspend fun enter(
        task: gg.rsmod.game.model.queue.QueueTask,
        player: gg.rsmod.game.model.entity.Player,
        door: GameObject,
        openedId: Int = -1,
        openTicks: Int = 2,
        farSide: Tile? = null,
        /** The script's own door-open visual (a hinged double door swinging open), run instead of the [openedId] swap. */
        open: (() -> Unit)? = null,
    ): Boolean {
        val near = nearSide(player.tile, door) ?: run {
            player.message("The door is blocked.")
            return false
        }
        if (!player.tile.sameAs(near)) {
            val route = player.walkTo(task, near)
            if (!route.success || !route.tail.sameAs(near)) {
                player.stopMovement()
                player.message("I can't reach that!")
                return false
            }
            var waited = 0
            while (!player.tile.sameAs(near) && waited < near.getDistance(player.tile) * 4 + 10) {
                if (!player.movementQueue.hasDestination()) break
                task.wait(1)
                waited++
            }
            if (!player.tile.sameAs(near)) {
                player.stopMovement()
                player.message("I can't reach that!")
                return false
            }
        }
        if (farSide != null && destination(player.tile, door)?.sameAs(farSide) != true) {
            player.message("The door is blocked.")
            return false
        }
        if (open != null) {
            open()
        } else if (openedId != -1) {
            val world = player.world
            world.spawn(gg.rsmod.game.model.entity.DynamicObject(door, openedId))
            world.queue {
                wait(openTicks + 1)
                world.spawn(gg.rsmod.game.model.entity.DynamicObject(door))
            }
        }
        player.faceTile(door.tile)
        return cross(task, player, door)
    }

    /**
     * The tile a crossing of [door] starts from on [playerTile]'s side: for a wall door the nearer of its own tile and the tile its
     * face looks at; for a 1x1 gate / free-standing door the nearest cardinal neighbour whose mirror through the door is a tile too.
     */
    fun nearSide(
        playerTile: Tile,
        door: GameObject,
    ): Tile? {
        val group = ObjectType.values().firstOrNull { it.value == door.type }?.group
        if (group == ObjectGroup.WALL && door.type != ObjectType.DIAGONAL_WALL.value) {
            val outside = door.tile.step(faceDirection(door.rot))
            return listOf(door.tile, outside).minByOrNull { (it.x - playerTile.x) * (it.x - playerTile.x) + (it.z - playerTile.z) * (it.z - playerTile.z) }
        }
        return Direction.NESW.map { door.tile.step(it) }.minByOrNull { (it.x - playerTile.x) * (it.x - playerTile.x) + (it.z - playerTile.z) * (it.z - playerTile.z) }
    }

    fun destination(
        playerTile: Tile,
        door: GameObject,
    ): Tile? = destination(playerTile, door.tile, door.type, door.rot)

    fun destination(
        playerTile: Tile,
        doorTile: Tile,
        type: Int,
        rot: Int,
    ): Tile? {
        if (playerTile.height != doorTile.height) return null
        val group = ObjectType.values().firstOrNull { it.value == type }?.group
        val straightWall = group == ObjectGroup.WALL && type != ObjectType.DIAGONAL_WALL.value
        if (straightWall) {
            val face = faceDirection(rot)
            val outside = doorTile.step(face)
            return when {
                playerTile.sameAs(doorTile) -> outside
                playerTile.sameAs(outside) -> doorTile
                else -> null
            }
        }
        val dx = doorTile.x - playerTile.x
        val dz = doorTile.z - playerTile.z
        if (Math.abs(dx) + Math.abs(dz) != 1) return null
        return doorTile.transform(dx, dz)
    }
}

package gg.rsmod.plugins.content.mechanics.traversal

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.CollisionUpdate
import gg.rsmod.game.model.entity.GameObject

/**
 * Owner priority #1 2026-10-03 (stuck / noclip, "in heel Gielinor"): the server's collision of a tile and its neighbours, written
 * so it can be laid next to the real client's `[walktrace]` lines tile by tile. For every tile: which of the four cardinal steps the
 * server refuses, and every loc on it with the fields the 667 client's own collision reads (`MapRegion.loadLocation`): shape,
 * rotation, size, blockwalk, breakroutefinding (opcode 74) - and whether the server flags it ([CollisionUpdate.blocksWalk]).
 */
object CollisionProbe {
    fun describe(
        world: World,
        centre: Tile,
    ): List<String> {
        val lines = ArrayList<String>()
        lines += "clip ${centre.x},${centre.z},${centre.height}: steps refused per tile (N E S W) and the locs on it"
        for (dz in 1 downTo -1) {
            for (dx in -1..1) {
                val tile = centre.transform(dx, dz)
                lines += describeTile(world, tile, dx == 0 && dz == 0)
            }
        }
        return lines
    }

    fun describeTile(
        world: World,
        tile: Tile,
        centre: Boolean = false,
    ): String {
        val refused =
            Direction.NESW.filter { dir -> world.collision.isBlocked(tile, dir, projectile = false) }
                .joinToString("") { it.name.first().toString() }
        val entering =
            Direction.NESW.filter { dir -> !world.collision.canStep(tile.step(dir), dir.getOpposite()) }
                .joinToString("") { it.name.first().toString() }
        val chunk = world.chunks.get(tile, createIfNeeded = true)
        val locs =
            chunk?.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)?.joinToString(" ") { obj ->
                val def = world.definitions.getNullable(ObjectDef::class.java, obj.id)
                if (def == null) {
                    "${obj.id}?"
                } else {
                    "${obj.id}${if (def.name.isNotBlank()) "'${def.name}'" else ""}(shape=${obj.type} rot=${obj.rot} " +
                        "${def.getRotatedWidth(obj)}x${def.getRotatedLength(obj)} blockwalk=${def.blockwalk}" +
                        "${if (def.breakroutefinding) " op74" else ""}${if (CollisionUpdate.blocksWalk(def, obj.type)) " FLAGGED" else " free"})"
                }
            } ?: ""
        return "${if (centre) ">" else " "}${tile.x},${tile.z},${tile.height} out=${refused.ifEmpty { "-" }} in=${entering.ifEmpty { "-" }} $locs"
    }
}

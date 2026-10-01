package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject

fun World.openDoor(
    obj: GameObject,
    opened: Int = obj.id + 1,
    invertRot: Boolean = false,
    invertTransform: Boolean = false,
): GameObject {
    val oldRot = obj.rot
    val newRot = Math.abs((oldRot + (if (invertRot) -1 else 1)) and 0x3)
    val diagonal = obj.type == ObjectType.DIAGONAL_WALL.value

    val newTile =
        when (oldRot) {
            0 -> if (diagonal) obj.tile.transform(0, 1) else obj.tile.transform(if (invertTransform) 1 else -1, 0)
            1 -> if (diagonal) obj.tile.transform(1, 0) else obj.tile.transform(0, if (invertTransform) -1 else 1)
            2 -> if (diagonal) obj.tile.transform(0, -1) else obj.tile.transform(if (invertTransform) -1 else 1, 0)
            3 -> if (diagonal) obj.tile.transform(-1, 0) else obj.tile.transform(0, if (invertTransform) 1 else -1)
            else -> throw IllegalStateException("Invalid door rotation: [currentRot=$oldRot, replaceRot=$newRot]")
        }

    val newDoor = DynamicObject(id = opened, type = obj.type, rot = newRot, tile = newTile)
    remove(obj)
    spawn(newDoor)
    return newDoor
}

fun World.closeDoor(
    obj: GameObject,
    closed: Int = obj.id - 1,
    invertRot: Boolean = false,
    invertTransform: Boolean = false,
): GameObject {
    val oldRot = obj.rot
    val newRot = Math.abs((oldRot + (if (invertRot) 1 else -1)) and 0x3)
    val diagonal = obj.type == ObjectType.DIAGONAL_WALL.value

    val newTile =
        when (oldRot) {
            0 -> if (diagonal) obj.tile.transform(1, 0) else obj.tile.transform(0, if (invertTransform) -1 else 1)
            1 -> if (diagonal) obj.tile.transform(0, -1) else obj.tile.transform(if (invertTransform) -1 else 1, 0)
            2 -> if (diagonal) obj.tile.transform(-1, 0) else obj.tile.transform(0, if (invertTransform) 1 else -1)
            3 -> if (diagonal) obj.tile.transform(0, 1) else obj.tile.transform(if (invertTransform) 1 else -1, 0)
            else -> throw IllegalStateException("Invalid door rotation: [currentRot=$oldRot, replaceRot=$newRot]")
        }

    val newDoor = DynamicObject(id = closed, type = obj.type, rot = newRot, tile = newTile)
    remove(obj)
    spawn(newDoor)
    return newDoor
}

/**
 * Online players other than [exclude] standing within [radius] tiles of [center] on its plane - the one area scan for effects that
 * reach players around a spot (Lunar group spells, Accept Aid sharing, Potion Share, hunter's mix and moths).
 */
fun World.playersAround(
    center: gg.rsmod.game.model.Tile,
    radius: Int,
    exclude: gg.rsmod.game.model.entity.Player? = null,
): List<gg.rsmod.game.model.entity.Player> {
    val result = mutableListOf<gg.rsmod.game.model.entity.Player>()
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val tile = center.transform(x, z)
            val chunk = chunks.get(tile, createIfNeeded = false) ?: continue
            chunk.getEntities<gg.rsmod.game.model.entity.Player>(tile, gg.rsmod.game.model.EntityType.PLAYER, gg.rsmod.game.model.EntityType.CLIENT)
                .forEach { other -> if (other !== exclude && other.isOnline && other !in result) result.add(other) }
        }
    }
    return result
}

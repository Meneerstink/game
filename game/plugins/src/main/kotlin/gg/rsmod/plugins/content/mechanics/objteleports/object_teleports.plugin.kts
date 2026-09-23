package gg.rsmod.plugins.content.mechanics.objteleports

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectType
import java.nio.file.Paths

/*
 * Registers the sourced object-teleport table (see ObjectTeleports) as the fallback for every object
 * option no plugin binds: Chaos Tunnels rifts and portals, cave and dungeon entrances, ladders and
 * staircases across the world.
 */
on_world_init {
    ObjectTeleports.load(Paths.get("./data/cfg/object-teleports/object-teleports.json"))
    val placementRejected = ObjectTeleports.retainPlaced { entry ->
        val point = entry.tile!!
        val tile = Tile(point.x, point.z, point.height)
        ObjectType.values.any { type ->
            world.getObject(tile, type)?.id == entry.id
        }
    }
    val rejected =
        ObjectTeleports.retainValid { id ->
            world.definitions.getNullable(ObjectDef::class.java, id)?.options?.toList()
        }
    placementRejected.forEach {
        logger.info("Object teleports: rejected {} ({}) {} at {} - not placed in the 667 cache.", it.name, it.id, it.option, it.tile)
    }
    rejected.forEach { logger.info("Object teleports: rejected {} ({}) {} at {} - not in the 667 cache.", it.name, it.id, it.option, it.tile) }
    world.plugins.bindObjectFallback { player, obj, opt -> ObjectTeleports.tryTeleport(player, obj, opt) }
    logger.info(
        "Object teleports: loaded {} sourced object teleport entries ({} placement-rejected, {} definition-rejected).",
        ObjectTeleports.size,
        placementRejected.size,
        rejected.size,
    )
}

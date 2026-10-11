package gg.rsmod.plugins.content.mechanics.traversal

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.impl.OpLoc1Message
import gg.rsmod.game.message.impl.OpLoc2Message
import gg.rsmod.game.message.impl.OpLoc3Message
import gg.rsmod.game.message.impl.OpLoc4Message
import gg.rsmod.game.message.impl.OpLoc5Message
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.priv.Privilege

/*
 * `oploc <id> [option 1-5] [x z]` - clicks the nearest loc [id] (within 20 tiles, or the one on x,z) for the player who types it,
 * exactly as the client's OPLOC packet would (same handler, gates and walk). Owner priority #1 (obstacles): an agent drives an
 * obstacle in its own client without having to hit the loc on screen, so every crossing can be watched and screenshotted.
 */
on_command("oploc", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val id = args.getOrNull(0)?.toIntOrNull()
    if (id == null) {
        player.message("Usage: oploc <id> [option 1-5] [x z]")
        return@on_command
    }
    val slot = args.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 1
    val at = if (args.size >= 4) Tile(args[2].toInt(), args[3].toInt(), player.tile.height) else null
    var best: GameObject? = null
    val radius = 20
    for (dx in -radius..radius) {
        for (dz in -radius..radius) {
            val tile = at?.takeIf { dx == 0 && dz == 0 } ?: if (at != null) continue else player.tile.transform(dx, dz)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
            chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .filter { it.id == id && it.tile.sameAs(tile) }
                .forEach { obj -> if (best == null || obj.tile.getDistance(player.tile) < best!!.tile.getDistance(player.tile)) best = obj }
        }
    }
    val obj = best
    if (obj == null) {
        player.message("No loc $id ${if (at != null) "on ${at.x},${at.z}" else "within $radius tiles"}.")
        return@on_command
    }
    val option = world.definitions.getNullable(ObjectDef::class.java, id)?.options?.getOrNull(slot - 1)
    player.message("oploc $id '$option' (option $slot) at ${obj.tile.x},${obj.tile.z} from ${player.tile.x},${player.tile.z}")
    val message =
        when (slot) {
            1 -> OpLoc1Message(0, obj.tile.x, id, obj.tile.z)
            2 -> OpLoc2Message(id, obj.tile.x, obj.tile.z, 0)
            3 -> OpLoc3Message(id, obj.tile.x, obj.tile.z, 0)
            4 -> OpLoc4Message(id, obj.tile.x, obj.tile.z, 0)
            else -> OpLoc5Message(id, obj.tile.x, obj.tile.z, 0)
        }
    (player as? Client)?.gameSystem?.inject(message)
}

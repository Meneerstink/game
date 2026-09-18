package gg.rsmod.game.message.handler

import gg.rsmod.game.action.ObjectPathAction
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpLocTMessage
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_COMPONENT_CHILD
import gg.rsmod.game.model.attr.INTERACTING_COMPONENT_PARENT
import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import java.lang.ref.WeakReference

/**
 * A spell or familiar special move used on an object (owner 2026-09-18 P0). The packet was never registered, so these
 * clicks - Charge orb spells on obelisks, the Compost mound / Beaver / Hydra specials - did nothing and, before the
 * decoder fallback, broke the packet stream. The player walks to the object, then the spell-on-object plugin runs.
 */
class OpLocTHandler : MessageHandler<OpLocTMessage> {
    override fun handle(client: Client, world: World, message: OpLocTMessage) {
        val tile = Tile(message.x, message.z, client.tile.height)
        if (!tile.viewableFrom(client.tile, Player.TILE_VIEW_DISTANCE) || !client.lock.canMove()) return
        val obj =
            world.chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .firstOrNull { it.id == message.id } ?: return
        val parent = message.componentHash shr 16
        val child = message.componentHash and 0xFFFF
        log(client, "Spell on object: id=%d, x=%d, z=%d, component=[%d:%d]", message.id, message.x, message.z, parent, child)
        client.closeInterfaceModal()
        client.fullInterruption(movement = true, animations = true, interactions = true, queue = true)
        client.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)
        client.attr[INTERACTING_COMPONENT_PARENT] = parent
        client.attr[INTERACTING_COMPONENT_CHILD] = child
        client.executePlugin(ObjectPathAction.spellOnObjectPlugin)
    }
}
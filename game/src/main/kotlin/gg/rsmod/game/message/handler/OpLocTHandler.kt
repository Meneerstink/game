package gg.rsmod.game.message.handler

import gg.rsmod.game.action.ObjectPathAction
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpLocTMessage
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_COMPONENT_CHILD
import gg.rsmod.game.model.attr.INTERACTING_COMPONENT_PARENT
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import java.lang.ref.WeakReference

/**
 * ClientProt OPLOCT (42) - the ONE packet the 667 client sends for an interface target used on an object (MiniMenu
 * TGT_LOC): an inventory item ("Use logs -> Fire") and a spell or familiar special move ("Charge Earth Orb -> Earth
 * obelisk", Compost mound / Beaver / Hydra specials) alike.
 *
 * Item PKT / N2: packets.yml registered this opcode twice (OpLocTMessage, then OpLocUMessage); the later put() won, so
 * every spell click went to the item-on-object handler, which dropped it because no inventory item matched. One handler
 * now splits by the source interface, the way Void's InterfaceOnObjectOptionHandler (item present -> interactItemOn,
 * otherwise interactOn) and our own [OpNpcTHandler] (parent 679) do.
 */
class OpLocTHandler : MessageHandler<OpLocTMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: OpLocTMessage,
    ) {
        val parent = message.componentHash shr 16
        val child = message.componentHash and 0xFFFF
        if (isItemUse(parent)) {
            useItemOnObject(client, world, message)
        } else {
            castOnObject(client, world, message, parent, child)
        }
    }

    /** "Use item -> object": the source is the inventory; the slot must still hold the item the client saw there. */
    private fun useItemOnObject(
        client: Client,
        world: World,
        message: OpLocTMessage,
    ) {
        val slot = message.componentSlot
        if (slot < 0 || slot >= client.inventory.capacity) return
        if (!client.lock.canItemInteract()) return
        val item = client.inventory[slot] ?: return
        if (item.id != message.item) return
        if (!client.lock.canMove()) return
        val obj = findObject(client, world, message) ?: return

        if (message.movementType == 1 && world.privileges.isEligible(client.privilege, Privilege.ADMIN_POWER)) {
            val def = obj.getDef(world.definitions)
            client.moveTo(
                world.findRandomTileAround(obj.tile, radius = 1, centreWidth = def.width, centreLength = def.length) ?: obj.tile,
            )
        }

        log(client, "Item on object: item=%d, slot=%d, obj=%d, x=%d, z=%d", message.item, slot, message.id, message.x, message.z)
        // Audit S-12: interaction traces are for staff only.
        if (client.seesDebugOutput()) {
            client.writeConsoleMessage("Item on object: item=${message.item}, slot=$slot, obj=${message.id}, x=${message.x}, z=${message.z}")
        }

        client.closeInterfaceModal()
        client.fullInterruption(movement = true, interactions = true, animations = true, queue = true)
        client.attr[INTERACTING_ITEM] = WeakReference(item)
        client.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)
        client.executePlugin(ObjectPathAction.itemOnObjectPlugin)
    }

    /** "Cast spell / familiar special -> object" (owner 2026-09-18 P0): walk to the object, then the spell-on-object plugin. */
    private fun castOnObject(
        client: Client,
        world: World,
        message: OpLocTMessage,
        parent: Int,
        child: Int,
    ) {
        if (!client.lock.canMove()) return
        val obj = findObject(client, world, message) ?: return
        log(client, "Spell on object: id=%d, x=%d, z=%d, component=[%d:%d]", message.id, message.x, message.z, parent, child)
        if (client.seesDebugOutput()) {
            client.writeConsoleMessage("Spell on object: obj=${message.id}, x=${message.x}, z=${message.z}, component=[$parent:$child]")
        }
        client.closeInterfaceModal()
        client.fullInterruption(movement = true, animations = true, interactions = true, queue = true)
        client.attr[INTERACTING_OBJ_ATTR] = WeakReference(obj)
        client.attr[INTERACTING_COMPONENT_PARENT] = parent
        client.attr[INTERACTING_COMPONENT_CHILD] = child
        client.executePlugin(ObjectPathAction.spellOnObjectPlugin)
    }

    private fun findObject(
        client: Client,
        world: World,
        message: OpLocTMessage,
    ): GameObject? {
        val tile = Tile(message.x, message.z, client.tile.height)
        if (!tile.viewableFrom(client.tile, Player.TILE_VIEW_DISTANCE)) return null
        return world.chunks.getOrCreate(tile)
            .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .firstOrNull { it.id == message.id }
    }

    companion object {
        /** The 667 inventory interface: the only source of an item used on an object. */
        const val INVENTORY_INTERFACE = 679

        /** Item use when the target came from the inventory; any other source (spellbook, Summoning orb/panel) is a cast. */
        fun isItemUse(parent: Int): Boolean = parent == INVENTORY_INTERFACE
    }
}

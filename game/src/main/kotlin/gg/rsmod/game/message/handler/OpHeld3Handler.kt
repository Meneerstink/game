package gg.rsmod.game.message.handler

import gg.rsmod.game.action.UnhandledInteractions
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpHeld3Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.entity.Client
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpHeld3Handler : MessageHandler<OpHeld3Message> {
    override fun handle(
        client: Client,
        world: World,
        message: OpHeld3Message,
    ) {
        @Suppress("unused")
        val interfaceId = message.componentHash shr 16

        @Suppress("unused")
        val component = message.componentHash and 0xFFFF

        if (message.slot < 0 || message.slot >= client.inventory.capacity) {
            return
        }

        if (!client.lock.canItemInteract()) {
            return
        }

        val item = client.inventory[message.slot] ?: return

        if (item.id != message.item) {
            return
        }

        log(
            client,
            "Item action 3: id=%d, slot=%d, component=(%d, %d), inventory=(%d, %d)",
            message.item,
            message.slot,
            interfaceId,
            component,
            item.id,
            item.amount,
        )
        if (client.attr[gg.rsmod.game.model.attr.ID_INSPECTOR_ATTR] == true) {
            val def = item.getDef(world.definitions)
            client.writeConsoleMessage("Item id=${item.id}, name=${def.name}, amount=${item.amount}, slot=${message.slot}")
        }

        client.attr[INTERACTING_ITEM] = WeakReference(item)
        client.attr[INTERACTING_ITEM_ID] = item.id
        client.attr[INTERACTING_ITEM_SLOT] = message.slot

        if (!world.plugins.executeItem(client, item.id, 3)) {
            UnhandledInteractions.recordInteraction("item", item.id, 3, "item", "slot=${message.slot}")
            if (world.devContext.debugItemActions && client.seesDebugOutput()) {
                client.writeMessage("Unhandled item action: [item=${item.id}, slot=${message.slot}, option=3]")
            }
        }
    }
}

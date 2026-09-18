package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpHeld5Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpHeld5Handler : MessageHandler<OpHeld5Message> {
    override fun handle(
        client: Client,
        world: World,
        message: OpHeld5Message,
    ) {
        if (!client.lock.canDropItems()) {
            gg.rsmod.game.model.AvTrace.log { "drop refused gate=lock lock=${client.lock} slot=${message.slot} (OpHeld5)" }
            return
        }
        val hash = message.hash
        val slot = message.slot

        val item = client.inventory[slot] ?: return

        log(
            client,
            "Drop item: item=[%d, %d], slot=%d, interfaceId=%d, component=%d",
            item.id,
            item.amount,
            slot,
            hash shr 16,
            hash and 0xFFFF,
        )

        client.attr[INTERACTING_ITEM] = WeakReference(item)
        client.attr[INTERACTING_ITEM_ID] = item.id
        client.attr[INTERACTING_ITEM_SLOT] = slot

        client.resetFacePawn()

        // The fifth inventory op is only "Drop" when the item says so. Imported OSRS items put real actions there
        // ("Revert", "Dismantle", ...): route every non-Drop op-5 to its item-option plugin instead of dropping the item
        // (owner 2026-09-18: Revert on the Granite maul (or) did nothing). Unbound ops fall through to the drop path.
        val op5 = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).inventoryMenu[4]
        if (op5 != null && !op5.equals("Drop", ignoreCase = true) && world.plugins.executeItem(client, item.id, 5)) {
            return
        }

        if (world.plugins.canDropItem(client, item.id)) {
            val remove = client.inventory.remove(item, assureFullRemoval = false, beginSlot = slot)
            if (remove.completed > 0) {
                val floor = GroundItem(item.id, remove.completed, client.tile, client)
                remove.firstOrNull()?.let { removed ->
                    floor.copyAttr(removed.item.attr)
                }
                world.spawn(floor)
                world
                    .getService(
                        LoggerService::class.java,
                        searchSubclasses = true,
                    )?.logItemDrop(client, Item(item.id, remove.completed), slot)
            }
        }
    }
}

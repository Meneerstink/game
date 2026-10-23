package gg.rsmod.game.message.handler

import gg.rsmod.game.action.UnhandledInteractions
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpHeld5Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_ITEM
import gg.rsmod.game.model.attr.INTERACTING_ITEM_ID
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpHeld5Handler : MessageHandler<OpHeld5Message> {
    companion object {
        /**
         * The id an item lands on the floor as when dropped (null = itself); plugins. OSRS Wiki "Barrows equipment": "When dropped,
         * any piece of Barrows equipment will automatically degrade to 0."
         */
        @Volatile
        var dropAs: ((Int) -> Int?)? = null

        /**
         * N215 (owner 2026-10-23: "een drop warning bij untradeables net als osrs"): asked before a plain floor drop of [item] from
         * [slot]; true = the plugin took over (it asks the player and calls [drop] on a yes), false = drop now.
         */
        @Volatile
        var confirm: ((Player, Item, Int) -> Boolean)? = null

        /** The floor drop itself: the item leaves [slot] (only while it is still there) and lands under the player. */
        fun drop(
            client: Player,
            world: World,
            item: Item,
            slot: Int,
        ) {
            if (client.inventory[slot]?.id != item.id) return
            val remove = client.inventory.remove(item, assureFullRemoval = false, beginSlot = slot)
            if (remove.completed > 0) {
                val droppedAs = dropAs?.invoke(item.id)
                val floor = GroundItem(droppedAs ?: item.id, remove.completed, client.tile, client)
                remove.firstOrNull()?.takeIf { droppedAs == null }?.let { removed ->
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
        if (client.attr[gg.rsmod.game.model.attr.ID_INSPECTOR_ATTR] == true) {
            val def = item.getDef(world.definitions)
            client.writeConsoleMessage("Item id=${item.id}, name=${def.name}, amount=${item.amount}, slot=$slot")
        }

        client.attr[INTERACTING_ITEM] = WeakReference(item)
        client.attr[INTERACTING_ITEM_ID] = item.id
        client.attr[INTERACTING_ITEM_SLOT] = slot

        client.resetFacePawn()

        // The fifth inventory op is only "Drop" when the item says so. Imported OSRS items put real actions there
        // ("Revert", "Dismantle", ...): route every non-Drop op-5 to its item-option plugin instead of dropping the item
        // (owner 2026-09-18: Revert on the Granite maul (or) did nothing). Unbound ops fall through to the drop path.
        val op5 = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).inventoryMenu[4]
        if (op5 != null && !op5.equals("Drop", ignoreCase = true)) {
            if (world.plugins.executeItem(client, item.id, 5)) {
                return
            }
            UnhandledInteractions.recordInteraction("item", item.id, 5, "item", "slot=$slot")
        }

        if (!world.plugins.hasDropHandler(item.id) && confirm?.invoke(client, item, slot) == true) return
        if (world.plugins.canDropItem(client, item.id)) {
            drop(client, world, item, slot)
        }
    }
}

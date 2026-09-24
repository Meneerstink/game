package gg.rsmod.game.message.handler

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.action.UnhandledInteractions
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.IfButtonMessage
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTaskSet
import gg.rsmod.game.model.queue.impl.PawnQueueTaskSet
import gg.rsmod.game.service.log.LoggerService
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class IfButton1Handler : MessageHandler<IfButtonMessage> {
    internal val queues: QueueTaskSet = PawnQueueTaskSet()

    /**
     * The opcodes for item actions on if3 inventory interfaces
     */
    val FIRST_OPTION = 61
    val SECOND_OPTION = 64
    val THIRD_OPTION = 4
    val FOURTH_OPTION = 18
    val FIFTH_OPTION = 10
    val EIGHT_OPTION = 25

    /** First item id appended by the OSRS import (Occult necklace, the pilot batch); the 667 cache ends at 22327. */
    val FIRST_IMPORTED_ITEM = 22328

    override fun handle(
        client: Client,
        world: World,
        message: IfButtonMessage,
    ) {
        val interfaceId = message.hash shr 16
        val component = message.hash and 0xFFFF
        val option = message.option + 1

        // Owner live report 2026-09-13: the Follower Details icon (548:99/107, 746:47/31) is not
        // clickable. Trace every button packet, including ones dropped below as "not visible",
        // so the client boundary shows whether the click arrives at all.
        gg.rsmod.game.model.AvTrace.log {
            "button recv component=$interfaceId:$component option=$option opcode=${message.opcode} " +
                "visible=${client.interfaces.isVisible(interfaceId)} displayMode=${client.interfaces.displayMode}"
        }

        if (!client.interfaces.isVisible(interfaceId)) {
            return
        }

        log(
            client,
            "Click button: component=[%d:%d], option=%d, slot=%d, item=%d",
            interfaceId,
            component,
            option,
            message.slot,
            message.item,
        )

        if (world.devContext.debugButtons) {
            client.writeConsoleMessage(
                "Button action: [component=[$interfaceId:$component], option=$option, slot=${message.slot}, item=${message.item}, opcode=${message.opcode}]",
            )
        }

        client.attr[INTERACTING_BUTTON_ID] = component
        client.attr[INTERACTING_OPT_ATTR] = option
        client.attr[INTERACTING_ITEM_ID] = message.item
        client.attr[INTERACTING_SLOT_ATTR] = message.slot
        client.attr[INTERACTING_OPCODE_ATTR] = message.opcode

        if (interfaceId == 679) {
            when (message.opcode) {
                FIRST_OPTION -> {
                    handleItemAction(client, world, message.item, message.slot, 1)
                }

                SECOND_OPTION -> {
                    handleItemAction(client, world, message.item, message.slot, 2)
                }

                THIRD_OPTION -> {
                    handleItemAction(client, world, message.item, message.slot, 3)
                }

                FOURTH_OPTION -> {
                    handleItemAction(client, world, message.item, message.slot, 4)
                }

                FIFTH_OPTION -> {
                    val def = world.definitions.get(ItemDef::class.java, message.item)
                    // The fifth slot is "Drop" by default, but imported OSRS items put their own option there ("Uncharge" on the
                    // Webweaver bow, "Empty" on Dizana's quiver, "Dismantle", ...). Only a real Drop may drop the item.
                    when (def.inventoryMenu.getOrNull(4)?.lowercase()) {
                        "destroy" -> handleItemAction(client, world, message.item, message.slot, 10)
                        null, "", "drop" -> handleDropItem(client, world, interfaceId, component, message.item, message.slot)
                        // A native 667 custom fifth option nobody handles ("Release" toads, puzzle "Move", ...) keeps the old
                        // behaviour; an imported item (ids from FIRST_IMPORTED_ITEM) never drops through a non-Drop option.
                        else ->
                            if (message.item >= FIRST_IMPORTED_ITEM || world.plugins.hasItemOption(message.item, 5)) {
                                handleItemAction(client, world, message.item, message.slot, 5)
                            } else {
                                handleDropItem(client, world, interfaceId, component, message.item, message.slot)
                            }
                    }
                }

                EIGHT_OPTION -> {
                    handleItemAction(client, world, message.item, message.slot, 8)
                }
            }
        }

        if (!world.plugins.executeButton(client, interfaceId, component)) {
            val isInventoryItemAction =
                interfaceId == 679 &&
                    message.opcode in setOf(FIRST_OPTION, SECOND_OPTION, THIRD_OPTION, FOURTH_OPTION, FIFTH_OPTION, EIGHT_OPTION)
            if (!isInventoryItemAction && !isClientSideButton(interfaceId, component)) {
                UnhandledInteractions.recordInteraction(
                    kind = "button",
                    id = component,
                    option = option,
                    name = "button",
                    context = "interface=$interfaceId opcode=${message.opcode}",
                )
            }
            return
        }
    }

    /**
     * Buttons the client acts on by itself (their click is only a notification): the gameframe tab stones (resizable 746, fixed 548
     * Options), the chat-filter "All/View" (751:34) and the chatbox "Click" line (137:56). They filled 170 of 196 rows of
     * logs/unhandled-object-actions.tsv (2026-09-24) and buried the real gaps, so they are not recorded as unhandled.
     */
    private fun isClientSideButton(interfaceId: Int, component: Int): Boolean =
        when (interfaceId) {
            746 -> component in 39..54 || component == 174 || component == 176
            548 -> component == 103
            751 -> component == 34
            137 -> component == 56
            else -> false
        }

    private fun handleItemAction(
        client: Client,
        world: World,
        itemId: Int,
        slot: Int,
        option: Int,
    ) {
        if (!client.lock.canItemInteract()) {
            // RCV-012 B11: every silent refusal is traced so a live retest names the gate that blocked the click.
            gg.rsmod.game.model.AvTrace.log { "item-action refused gate=lock lock=${client.lock} item=$itemId slot=$slot option=$option" }
            return
        }

        if (itemId > -1) {
            val item = client.inventory[slot]
            if (item == null || item.id != itemId) {
                gg.rsmod.game.model.AvTrace.log { "item-action refused gate=slot item=$itemId slot=$slot has=${item?.id}" }
                return
            }

            client.attr[INTERACTING_ITEM] = WeakReference(item)
            client.attr[INTERACTING_ITEM_ID] = item.id
            client.attr[INTERACTING_ITEM_SLOT] = slot

            if (option == 2) {
                val result = EquipAction.equip(client, item, slot)
                if (result == EquipAction.Result.UNHANDLED) {
                    UnhandledInteractions.recordInteraction("item", item.id, 2, "item", "slot=$slot")
                    if (world.devContext.debugItemActions) {
                        client.writeMessage("Unhandled equip action: [item=${item.id}, slot=$slot]")
                    }
                }
                return
            }

            if (option == 8) {
                world.sendExamine(client, item.id, ExamineEntityType.ITEM)
                return
            }

            if (option == 10) {
                val result = world.plugins.executeItem(client, item.id, option)
                if (!result) {
                    UnhandledInteractions.recordInteraction("item", item.id, option, "item", "slot=$slot")
                    if (world.devContext.debugItemActions) {
                        client.writeMessage("Unhandled destroy action: [item=${item.id}, slot=$slot]")
                    }
                }
                return
            }

            // OSRS (owner decision 2026-09-12): eating and drinking do not stop combat - food only
            // delays the next attack (OSRS Wiki Food; Void Eating.consume). Other inventory
            // options still end it.
            val menuOption = world.definitions.get(ItemDef::class.java, item.id).inventoryMenu.getOrNull(option - 1)?.lowercase()
            val consumes = menuOption == "eat" || menuOption == "drink"
            client.fullInterruption(movement = false, interactions = true, animations = false, queue = true, preserveCombat = consumes)

            val handled = world.plugins.executeItem(client, item.id, option)

            if (!handled) {
                UnhandledInteractions.recordInteraction(
                    kind = "item",
                    id = item.id,
                    option = option,
                    name = "item",
                    context = "slot=$slot",
                )
                client.writeFilterableMessage(Entity.NOTHING_INTERESTING_HAPPENS)
                if (world.devContext.debugItemActions) {
                    client.writeMessage("Unhandled item action: [item=${item.id}, slot=$slot, option=$option]")
                    return
                }
            }
        }
    }

    private fun handleDropItem(
        client: Client,
        world: World,
        interfaceId: Int,
        component: Int,
        itemId: Int,
        slot: Int,
    ) {
        if (!client.lock.canDropItems()) {
            gg.rsmod.game.model.AvTrace.log { "drop refused gate=lock lock=${client.lock} item=$itemId slot=$slot" }
            return
        }

        if (itemId > -1) {
            val item = client.inventory[slot]

            if (item == null || item.id != itemId) {
                gg.rsmod.game.model.AvTrace.log { "drop refused gate=slot item=$itemId slot=$slot has=${item?.id}" }
                return
            }

            log(
                client,
                "Drop item: item=[%d, %d], slot=%d, interfaceId=%d, component=%d",
                item.id,
                item.amount,
                slot,
                interfaceId,
                component,
            )

            client.attr[INTERACTING_ITEM] = WeakReference(item)
            client.attr[INTERACTING_ITEM_ID] = item.id
            client.attr[INTERACTING_ITEM_SLOT] = slot

            client.fullInterruption(interactions = true, queue = true)

            if (!world.plugins.canDropItem(client, item.id)) {
                gg.rsmod.game.model.AvTrace.log { "drop refused gate=can_drop_item plugin item=${item.id}" }
            } else {
                val remove = client.inventory.remove(item, assureFullRemoval = false, beginSlot = slot)
                gg.rsmod.game.model.AvTrace.log { "drop item=${item.id} removed=${remove.completed} combat=${client.attr.has(COMBAT_TARGET_FOCUS_ATTR)}" }
                if (remove.completed > 0) {
                    val floor = GroundItem(item.id, remove.completed, client.tile, client)
                    remove.firstOrNull()?.let { removed ->
                        floor.copyAttr(removed.item.attr)
                    }
                    client.write(SynthSoundMessage(2739, 1, 0))
                    world.spawn(floor)
                    world
                        .getService(LoggerService::class.java, searchSubclasses = true)
                        ?.logItemDrop(client, Item(item.id, remove.completed), slot)
                }
            }
        }
    }
}

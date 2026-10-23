package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.model.attr.CURRENT_SHOP_ATTR
import gg.rsmod.game.model.attr.LAST_VIEWED_SHOP_ITEM_FREE
import gg.rsmod.game.model.attr.LAST_VIEWED_SHOP_ITEM_SLOT
import gg.rsmod.game.model.shop.PurchasePolicy

val SHOP_INTERFACE_ID = 620
val INV_INTERFACE_ID = 621
val INFO_INTERFACE_ID = 449

val BUY_OPTS = arrayOf(1, 5, 10, 50, 500)

/**
 * Owner 2026-09-23: "buying items from every shop should give u sounds ... fix all sounds for every shop". Every shop routes its
 * buy, take-free and sell through [transaction]; the coin jingle plays once when the player's inventory actually changed, so a
 * refused purchase (no coins, no space, out of stock) stays silent.
 */
fun Player.transaction(action: () -> Unit) {
    val before = inventory.rawItems.map { it?.id to it?.amount }
    action()
    if (inventory.rawItems.map { it?.id to it?.amount } != before) {
        playSound(ShopSounds.TRANSACTION)
    }
}
val SELL_OPTS = arrayOf(1, 5, 10, 50)

/** Op 7 of a stock cell ("Buy X", ShopWindowScriptTool). */
val BUY_X_OPCODE = 18

/**
 * N62: asks how many, then buys exactly like Buy 1..500 (the currency caps it at the stock, the player's coins and inventory room).
 * Nothing happens when the window was closed or the row changed while the amount was typed.
 */
fun buyX(
    player: Player,
    shop: gg.rsmod.game.model.shop.Shop,
    slot: Int,
    item: Int,
) {
    player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
        val amount = inputInt("Enter amount:")
        if (amount <= 0) return@queue
        if (player.attr[CURRENT_SHOP_ATTR] !== shop || !player.isInterfaceVisible(SHOP_INTERFACE_ID) || shop.items.getOrNull(slot)?.item != item) {
            return@queue
        }
        player.transaction { shop.currency.sellToPlayer(player, shop, slot, amount) }
    }
}

on_interface_open(interfaceId = SHOP_INTERFACE_ID) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        shop.viewers.add(player.uid)
    }
}

on_interface_open(interfaceId = INV_INTERFACE_ID) {
    player.setEvents(interfaceId = 621, to = 27, setting = 1266)
    player.runClientScript(
        150,
        621 shl 16,
        93,
        4,
        7,
        0,
        -1,
        "Value",
        "",
        "",
        "Sell 1",
        "Sell 5",
        "Sell 10",
        "Sell 50",
        "",
    )
}

on_interface_close(interfaceId = SHOP_INTERFACE_ID) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        shop.viewers.remove(player.uid)
        player.closeInterface(interfaceId = INFO_INTERFACE_ID)
        player.closeInterface(interfaceId = INV_INTERFACE_ID)
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        player.inventory.dirty = true
    }
}

on_button(interfaceId = SHOP_INTERFACE_ID, component = 25) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        val opcode = player.getInteractingOpcode()
        // N221: every stock row is CHILDREN_PER_ROW children of 620:25 (the 7th draws the stock number); the first takes the ops.
        val slot = player.getInteractingSlot() / gg.rsmod.game.tools.importer.ShopWindowScriptTool.CHILDREN_PER_ROW
        val shopItem = shop.items.getOrNull(slot) ?: return@on_button

        when (opcode) {
            61 -> {
                shop.currency.onSellValueMessage(player, shopItem, false)
                player.attr[LAST_VIEWED_SHOP_ITEM_SLOT] = slot
            }
            25 -> world.sendExamine(player, shopItem.item, ExamineEntityType.ITEM)
            // N62 (owner 2026-10-23): "Buy X" (op 7) - the player types the amount.
            BUY_X_OPCODE -> buyX(player, shop, slot, shopItem.item)
            else -> {
                val amount =
                    when (opcode) {
                        64 -> BUY_OPTS[0]
                        4 -> BUY_OPTS[1]
                        52 -> BUY_OPTS[2]
                        81 -> BUY_OPTS[3]
                        91 -> BUY_OPTS[4]
                        else -> return@on_button
                    }
                player.transaction { shop.currency.sellToPlayer(player, shop, slot, amount) }
            }
        }
    }
}

// Owner 2026-10-06 (bug 204956): Prev page / Next page on a paged shop (ShopPageGroups, row 620:35..38 from ShopPageInterfaceImportTool).
on_button(interfaceId = SHOP_INTERFACE_ID, component = gg.rsmod.game.tools.importer.ShopPageInterfaceImportTool.PAGE_PREVIOUS) {
    ShopPageGroups.turnPage(player, -1)
}

on_button(interfaceId = SHOP_INTERFACE_ID, component = gg.rsmod.game.tools.importer.ShopPageInterfaceImportTool.PAGE_NEXT) {
    ShopPageGroups.turnPage(player, 1)
}

on_button(interfaceId = INFO_INTERFACE_ID, component = 21) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        val opcode = player.getInteractingOpcode()
        var slot = player.attr[LAST_VIEWED_SHOP_ITEM_SLOT]

        val amount =
            when (opcode) {
                61 -> BUY_OPTS[0]
                64 -> BUY_OPTS[1]
                4 -> BUY_OPTS[2]
                52 -> BUY_OPTS[3]
                else -> return@on_button
            }
        if (player.attr[LAST_VIEWED_SHOP_ITEM_FREE] == true) {
            player.transaction { shop.currency.giveToPlayer(player, shop, slot!!, amount) }
        } else {
            player.transaction { shop.currency.sellToPlayer(player, shop, slot!!, amount) }
        }
        player.closeInterface(interfaceId = INFO_INTERFACE_ID)
        player.openInterface(INV_INTERFACE_ID, dest = InterfaceDestination.TAB_AREA)
    }
}

on_button(interfaceId = INFO_INTERFACE_ID, component = 1) {
    player.closeInterface(interfaceId = INFO_INTERFACE_ID)
    player.openInterface(INV_INTERFACE_ID, dest = InterfaceDestination.TAB_AREA)
}

on_button(interfaceId = SHOP_INTERFACE_ID, component = 26) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        val opcode = player.getInteractingOpcode()
        var slot = player.getInteractingSlot()
        if (slot != 0) {
            slot = player.getInteractingSlot() / 4
        }
        val shopItem = shop.sampleItems.getOrNull(slot) ?: return@on_button

        when (opcode) {
            61 -> {
                shop.currency.onSellValueMessage(player, shopItem, true)
                player.attr[LAST_VIEWED_SHOP_ITEM_SLOT] = slot
            }
            25 -> world.sendExamine(player, shopItem.item, ExamineEntityType.ITEM)
            else -> {
                val amount =
                    when (opcode) {
                        64 -> BUY_OPTS[0]
                        4 -> BUY_OPTS[1]
                        52 -> BUY_OPTS[2]
                        81 -> BUY_OPTS[3]
                        91 -> BUY_OPTS[4]
                        else -> return@on_button
                    }
                player.transaction { shop.currency.giveToPlayer(player, shop, slot, amount) }
            }
        }
    }
}

on_button(interfaceId = INV_INTERFACE_ID, component = 0) {
    player.attr[CURRENT_SHOP_ATTR]?.let { shop ->
        val opcode = player.getInteractingOpcode()
        val slot = player.getInteractingSlot()
        val item = player.inventory[slot] ?: return@on_button

        when (opcode) {
            61 -> shop.currency.onBuyValueMessage(player, shop, item.id)
            25 -> world.sendExamine(player, item.id, ExamineEntityType.ITEM)
            else -> {
                val amount =
                    when (opcode) {
                        52 -> SELL_OPTS[0]
                        81 -> SELL_OPTS[1]
                        91 -> SELL_OPTS[2]
                        18 -> SELL_OPTS[3]
                        else -> return@on_button
                    }
                // Audit E-10: a shop that buys nothing refuses with a message before any currency code runs.
                if (shop.purchasePolicy == PurchasePolicy.BUY_NONE) {
                    player.message("You can't sell any items to this shop.")
                    return@on_button
                }
                player.transaction { shop.currency.buyFromPlayer(player, shop, slot, amount) }
            }
        }
    }
}

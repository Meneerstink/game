package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.model.attr.OBJ_DIALOG_ITEM_ATTR

/**
 * RCV-010 C3: bindings of the native 667 Grand Exchange screens; all logic lives in [GrandExchangeInterface].
 */
val GE = GrandExchangeInterface

on_login {
    GE.service(player)?.let { GE.refreshAll(player, it) }
}

GE.VIEW_OFFER.forEachIndexed { slot, component ->
    on_button(GE.MAIN, component) {
        if (player.getInteractingOpcode() == GE.OPCODE_OP2) GE.abort(player, slot) else GE.viewOffer(player, slot)
    }
}
GE.MAKE_BUY.forEachIndexed { slot, component -> on_button(GE.MAIN, component) { GE.beginBuy(player, slot) } }
GE.MAKE_SELL.forEachIndexed { slot, component -> on_button(GE.MAIN, component) { GE.beginSell(player, slot) } }

listOf(GE.DECREASE_QUANTITY, GE.INCREASE_QUANTITY, GE.ADD_1, GE.ADD_10, GE.ADD_100, GE.ADD_1000).forEach { component ->
    on_button(GE.MAIN, component) {
        val selection = player.attr[GE.SELECTION_ATTR]?.takeIf { it.itemId != -1 } ?: run {
            player.message(GE.MSG_CHOOSE_FIRST)
            return@on_button
        }
        selection.quantity = GE.adjustQuantity(selection, component, GE.ownedCount(player, selection.itemId))
        GE.sendSelection(player, selection)
    }
}

listOf(GE.DECREASE_PRICE, GE.INCREASE_PRICE, GE.PLUS_FIVE_PERCENT, GE.MINUS_FIVE_PERCENT, GE.OFFER_GUIDE_PRICE).forEach { component ->
    on_button(GE.MAIN, component) {
        val selection = player.attr[GE.SELECTION_ATTR]?.takeIf { it.itemId != -1 } ?: run {
            player.message(GE.MSG_CHOOSE_FIRST)
            return@on_button
        }
        selection.price = GE.adjustPrice(selection, component)
        GE.sendSelection(player, selection)
    }
}

on_button(GE.MAIN, GE.EDIT_QUANTITY) {
    val selection = player.attr[GE.SELECTION_ATTR]?.takeIf { it.itemId != -1 } ?: run {
        player.message(GE.MSG_CHOOSE_FIRST)
        return@on_button
    }
    player.queue(TaskPriority.WEAK) {
        val input = inputInt("Enter amount")
        val max = if (selection.type == OfferType.SELL) GE.ownedCount(player, selection.itemId) else Int.MAX_VALUE
        selection.quantity = input.coerceIn(0, max)
        GE.sendSelection(player, selection)
    }
}

on_button(GE.MAIN, GE.EDIT_PRICE) {
    val selection = player.attr[GE.SELECTION_ATTR]?.takeIf { it.itemId != -1 } ?: run {
        player.message(GE.MSG_CHOOSE_FIRST)
        return@on_button
    }
    player.queue(TaskPriority.WEAK) {
        selection.price = GE.clampPrice(selection, inputInt("Enter Price"))
        GE.sendSelection(player, selection)
    }
}

on_button(GE.MAIN, GE.CONFIRM) { GE.confirm(player) }
on_button(GE.MAIN, GE.CHOOSE_ITEM) {
    if (player.attr[GE.SELECTION_ATTR]?.type == OfferType.BUY) GE.openItemSearch(player)
}
on_button(GE.MAIN, GE.ABORT) {
    val slot = player.attr[GE.SELECTION_ATTR]?.slot ?: player.getVarp(GE.VARP_SLOT)
    if (slot in 0 until GrandExchangeService.SLOTS) GE.abort(player, slot)
}
on_button(GE.MAIN, GE.BACK) { GE.back(player) }

GE.COLLECT.forEachIndexed { index, component ->
    on_button(GE.MAIN, component) {
        val slot = player.getVarp(GE.VARP_SLOT)
        if (slot in 0 until GrandExchangeService.SLOTS) {
            GE.collect(player, slot, index, op1 = player.getInteractingOpcode() == GE.OPCODE_OP1)
        }
    }
}

GE.COLLECTION_BOX_OFFERS.forEachIndexed { slot, component ->
    on_button(GE.COLLECTION_BOX, component) {
        val index = if (player.getInteractingSlot() == 0) 0 else 1
        GE.collect(player, slot, index, op1 = player.getInteractingOpcode() == GE.OPCODE_OP1)
    }
}

on_button(GE.SELL_INVENTORY, GE.SELL_INVENTORY_ITEMS) {
    val selection = player.attr[GE.SELECTION_ATTR] ?: return@on_button
    if (selection.type != OfferType.SELL) return@on_button
    val item = player.inventory[player.getInteractingSlot()] ?: return@on_button
    GE.select(player, item.id)
}

on_obj_dialog {
    val selection = player.attr[GE.SELECTION_ATTR] ?: return@on_obj_dialog
    if (selection.type != OfferType.BUY) return@on_obj_dialog
    val item = player.attr[OBJ_DIALOG_ITEM_ATTR] ?: return@on_obj_dialog
    GE.select(player, item)
}

on_interface_close(GE.MAIN) {
    player.attr.remove(GE.SELECTION_ATTR)
    player.runClientScript(GE.SCRIPT_CLOSE_SEARCH)
    player.closeInterface(dest = InterfaceDestination.TAB_AREA)
}

package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT Dragonfire shield Inspect/Empty (rules and sources in [DragonfireShield]). Messages are
 * sourced from the OSRS Wiki verbatim where quoted, ADAPTED otherwise.
 */

on_item_option(item = Items.DRAGONFIRE_SHIELD, option = "Inspect") {
    val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    player.message("The shield has ${DragonfireShield.charges(item)} charges.")
}

on_item_option(item = Items.DRAGONFIRE_SHIELD, option = "Empty") {
    val slot = player.getInteractingItemSlot()
    val item = player.inventory[slot] ?: return@on_item_option
    if (DragonfireShield.charges(item) <= 0) {
        player.message("The shield has no charges.")
        return@on_item_option
    }
    player.inventory[slot] = DragonfireShield.withCharges(item, 0)
    player.message("You empty the shield of its remaining charges, releasing them in a harmless burst.")
}

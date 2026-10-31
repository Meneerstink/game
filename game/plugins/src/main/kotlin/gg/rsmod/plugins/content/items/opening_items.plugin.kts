package gg.rsmod.plugins.content.items

(OpeningItems.fixed.keys + Items.OYSTER).forEach { id ->
    on_item_option(id, "Open") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != id) return@on_item_option
        if (!OpeningItems.open(player.inventory, slot, id, world.random(0..31))) {
            player.message(ItemContents.NO_SPACE)
        }
    }
}

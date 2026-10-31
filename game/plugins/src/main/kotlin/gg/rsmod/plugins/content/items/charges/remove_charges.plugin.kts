package gg.rsmod.plugins.content.items.charges

// ItemActionGuard confirms the native Remove-charge spelling through the shared Uncharge GUI.
ItemCharges.removable.keys.forEach { id ->
    if (!world.plugins.hasItemOption(id, 4)) on_item_option(item = id, option = "Remove-charge") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id == id) ItemCharges.removeCharges(player, slot)
    }
}

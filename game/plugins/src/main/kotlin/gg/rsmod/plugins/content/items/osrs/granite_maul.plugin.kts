package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS-IMPORT Granite maul (rules and sources in [GraniteMaul]) and the Ornate maul handle (OSRS Wiki "Ornate maul handle":
 * "can be applied to the granite maul"; "This process can be reverted at any time, but the handle will be destroyed in doing
 * so"). The attach warning dialog is not ported (text not quoted; its 26 September 2019 toggle neither).
 */

// The special bar shows for both mauls; Quick Smash itself runs from the bar click and the combat cycle (GraniteMaul).
listOf(Items.GRANITE_MAUL, Items.GRANITE_MAUL_ORNATE_HANDLE).forEach { id ->
    SpecialAttacks.register(GraniteMaul.cost(id)!!, id) {
        GraniteMaul.smash(player, target, doubled = false, drain = false)
    }
}

on_item_on_item(item1 = Items.ORNATE_MAUL_HANDLE, item2 = Items.GRANITE_MAUL) {
    // Owner 2026-09-19: attaching an ornament asks "Are you sure" in the item GUI, like detaching (Revert) does.
    player.queue {
        if (!confirmItemAction(Items.GRANITE_MAUL, "Are you sure you want to attach the ornate handle to this item?", "The ornate handle will be attached to this item.")) {
            return@queue
        }
        if (!player.inventory.contains(Items.ORNATE_MAUL_HANDLE) || !player.inventory.contains(Items.GRANITE_MAUL)) return@queue
        if (player.inventory.remove(Items.ORNATE_MAUL_HANDLE, 1).hasSucceeded() && player.inventory.remove(Items.GRANITE_MAUL, 1).hasSucceeded()) {
            player.inventory.add(Items.GRANITE_MAUL_ORNATE_HANDLE, 1)
        }
    }
}

gg.rsmod.plugins.content.items.ItemActionGuard.selfConfirming("revert", Items.GRANITE_MAUL_ORNATE_HANDLE) // own warning below
on_item_option(item = Items.GRANITE_MAUL_ORNATE_HANDLE, option = "Revert") {
    val slot = player.getInteractingItemSlot()
    player.queue {
        // OSRS Wiki "Ornate maul handle": reverting destroys the handle, so it is confirmed first.
        if (!confirmWarning("Revert the maul? The ornate handle will be destroyed.")) return@queue
        if (player.inventory[slot]?.id != Items.GRANITE_MAUL_ORNATE_HANDLE) return@queue
        player.inventory[slot] = gg.rsmod.game.model.item.Item(Items.GRANITE_MAUL)    }
}

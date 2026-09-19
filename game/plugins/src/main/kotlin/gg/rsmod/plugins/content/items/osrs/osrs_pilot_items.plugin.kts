package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT item actions (`C:\RSPS\OSRS_IMPORT_MASTER.yml`). Combining is registered in
 * `CombinationData`; this file binds the cache "Dismantle" options of the imported definitions.
 *
 * - Ornamented items ([OsrsOrnamentKits]): Dismantle returns the base item and the ornament kit.
 * - Avernic defender: Dismantle returns the Dragon defender; the hilt is destroyed (OSRS Wiki), so
 *   the player confirms first.
 */

// Used-up kits (OsrsOrnamentKits.CONSUMED): a cleaning cloth on the frozen/volcanic whip, or the Revert option on the (or) staves,
// returns only the base item.
OsrsOrnamentKits.CONSUMED.forEach { kit ->
    fun revert(player: Player, slot: Int) {
        if (player.inventory[slot]?.id != kit.ornamented) return
        if (kit.returnsKit && player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return
        }
        player.inventory[slot] = gg.rsmod.game.model.item.Item(kit.base)
        if (kit.returnsKit) player.inventory.add(kit.kit, 1)
    }
    if (kit.cleaningCloth) {
        on_item_on_item(item1 = Items.CLEANING_CLOTH, item2 = kit.ornamented) {
            val first = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            revert(player, if (player.inventory[first]?.id == kit.ornamented) first else second)
        }
    } else {
        on_item_option(item = kit.ornamented, option = "Revert") { revert(player, player.getInteractingItemSlot()) }
    }
}

OsrsOrnamentKits.ALL.forEach { ornament ->
    on_item_option(item = ornament.ornamented, option = ornament.detachOption) {
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = ornament.ornamented, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) {
            return@on_item_option
        }
        player.inventory.add(item = ornament.base, assureFullInsertion = true)
        player.inventory.add(item = ornament.kit, assureFullInsertion = true)
        // Owner 2026-09-18: dismantling says the opposite of attaching, in the item GUI.
        player.queue { doubleItemMessageBox(OsrsOrnamentKits.DETACH_MESSAGE, item1 = ornament.base, item2 = ornament.kit) }
    }
}

on_item_option(item = Items.AVERNIC_DEFENDER, option = "Dismantle") {
    val slot = player.getInteractingItemSlot()
    player.queue {
        when (options("Dismantle it. The hilt will be destroyed.", "Cancel.")) {
            1 -> {
                if (player.inventory[slot]?.id != Items.AVERNIC_DEFENDER) return@queue
                if (!player.inventory.remove(item = Items.AVERNIC_DEFENDER, beginSlot = slot).hasSucceeded()) return@queue
                player.inventory.add(item = Items.DRAGON_DEFENDER, assureFullInsertion = true)
            }
        }
    }
}

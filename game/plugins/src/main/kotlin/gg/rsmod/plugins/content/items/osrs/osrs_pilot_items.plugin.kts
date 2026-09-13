package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT item actions (`C:\RSPS\OSRS_IMPORT_STATUS.md`). Combining is registered in
 * `CombinationData`; this file binds the cache "Dismantle" options of the imported definitions.
 *
 * - Ornamented items ([OsrsOrnamentKits]): Dismantle returns the base item and the ornament kit.
 * - Avernic defender: Dismantle returns the Dragon defender; the hilt is destroyed (OSRS Wiki), so
 *   the player confirms first.
 */

OsrsOrnamentKits.ALL.forEach { ornament ->
    on_item_option(item = ornament.ornamented, option = "Dismantle") {
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = ornament.ornamented, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) {
            return@on_item_option
        }
        player.inventory.add(item = ornament.base, assureFullInsertion = true)
        player.inventory.add(item = ornament.kit, assureFullInsertion = true)
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

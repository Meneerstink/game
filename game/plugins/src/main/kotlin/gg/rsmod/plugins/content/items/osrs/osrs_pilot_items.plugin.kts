package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT pilot item actions (`C:\RSPS\OSRS_IMPORT_STATUS.md`). Combining is registered in
 * `CombinationData` (AVERNIC_DEFENDER, OCCULT_NECKLACE_OR); this file binds the cache "Dismantle"
 * options of the imported definitions.
 *
 * - Occult necklace (or): Dismantle returns the necklace and the ornament kit (OSRS Wiki).
 * - Avernic defender: Dismantle returns the Dragon defender; the hilt is destroyed (OSRS Wiki), so
 *   the player confirms first.
 */

on_item_option(item = Items.OCCULT_NECKLACE_OR, option = "Dismantle") {
    if (player.inventory.freeSlotCount < 1) {
        player.message("You don't have enough inventory space to do that.")
        return@on_item_option
    }
    if (!player.inventory.remove(item = Items.OCCULT_NECKLACE_OR, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) {
        return@on_item_option
    }
    player.inventory.add(item = Items.OCCULT_NECKLACE, assureFullInsertion = true)
    player.inventory.add(item = Items.OCCULT_ORNAMENT_KIT, assureFullInsertion = true)
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

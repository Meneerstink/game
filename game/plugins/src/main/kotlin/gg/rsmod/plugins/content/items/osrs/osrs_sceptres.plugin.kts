package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT sceptres: quartz on the Ancient sceptre and Dismantle of the unlocked quartz sceptres ([AncientSceptres.QUARTZ_UPGRADES]).
 * ADAPTED: no message (none sourced). NOT ENFORCED (owner question 5): Desert Treasure II completion. BLOCKED: the Ancient sceptre itself
 * is made by Eblis (NPC absent in 667).
 */
AncientSceptres.QUARTZ_UPGRADES.forEach { (quartz, sceptre) ->
    on_item_on_item(item1 = quartz, item2 = Items.ANCIENT_SCEPTRE) {
        val quartzSlot = player.inventory.getItemIndex(quartz, skipAttrItems = false)
        val sceptreSlot = player.inventory.getItemIndex(Items.ANCIENT_SCEPTRE, skipAttrItems = false)
        if (quartzSlot == -1 || sceptreSlot == -1) return@on_item_on_item
        player.inventory.remove(item = quartz, beginSlot = quartzSlot)
        player.inventory[sceptreSlot] = gg.rsmod.game.model.item.Item(sceptre)
    }

    on_item_option(item = sceptre, option = "Dismantle") {
        if (player.inventory.freeSlotCount < 1) {
            player.message("You need a free inventory space to do that.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = sceptre, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) return@on_item_option
        player.inventory.add(Items.ANCIENT_SCEPTRE, 1)
        player.inventory.add(quartz, 1)
    }
}

package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT sceptres: quartz on the Ancient sceptre and Dismantle of the unlocked quartz sceptres ([AncientSceptres.QUARTZ_UPGRADES]).
 * ADAPTED: no message (none sourced). Desert Treasure II completion is enforced since the owner's 2026-09-26 instruction to apply the
 * OSRS requirements of the choice quests (OsrsQuestRequirements). BLOCKED: the Ancient sceptre itself is made by Eblis in OSRS.
 */
AncientSceptres.QUARTZ_UPGRADES.forEach { (quartz, sceptre) ->
    on_item_on_item(item1 = quartz, item2 = Items.ANCIENT_SCEPTRE) {
        if (!OsrsQuestRequirements.canUpgradeSceptre(player)) {
            player.message("You need to complete Desert Treasure II to empower the sceptre.")
            return@on_item_on_item
        }
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

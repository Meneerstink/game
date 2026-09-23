package gg.rsmod.plugins.content.skills.fletching.crossbows

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.grantOrRefund

/**
 * OSRS Wiki "Zaryte crossbow" (raw wikitext, fetched 2026-09-16): "The crossbow itself is crafted by attaching a
 * nihil horn ... with an Armadyl crossbow and 250 nihil shards." No skill requirement is stated for the crafting
 * process itself.
 */
on_item_on_item(item1 = Items.NIHIL_HORN, item2 = Items.ARMADYL_CROSSBOW) {
    if (player.inventory.getItemCount(Items.NIHIL_SHARD) < 250) {
        player.message("You need 250 nihil shards to attach the horn to the crossbow.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.NIHIL_HORN, 1, assureFullRemoval = true)
    player.inventory.remove(Items.ARMADYL_CROSSBOW, 1, assureFullRemoval = true)
    player.inventory.remove(Items.NIHIL_SHARD, 250, assureFullRemoval = true)
    if (!player.grantOrRefund(
            Item(Items.ZARYTE_CROSSBOW, 1),
            listOf(Item(Items.NIHIL_HORN, 1), Item(Items.ARMADYL_CROSSBOW, 1), Item(Items.NIHIL_SHARD, 250)),
        )
    ) {
        return@on_item_on_item
    }
    player.filterableMessage("You attach the nihil horn to your Armadyl crossbow, forming a Zaryte crossbow.")
}

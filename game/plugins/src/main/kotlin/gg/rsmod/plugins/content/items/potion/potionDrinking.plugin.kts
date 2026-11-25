package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.plugins.api.cfg.Items

val potionValues = Potion.values()

potionValues.forEach { potion ->
    on_item_option(item = potion.item, option = "Drink") {
        Potions.drink(player, potion)
    }
}

// Empty is a cache option, not a potion-effect concern. Bind every real dose item independently.
PotionDecanting.emptyableDoseItems(world.definitions).forEach { item ->
    on_item_option(item = item, option = "Empty") {
        val slot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option
        PotionDecanting.empty(player, slot, forcedContainer = Items.VIAL)
    }
}

// N537 stap 3a (census 25-11: Drink/Empty of every divine potion and the blighted overload did nothing): the owner took these out of
// play (2026-09-29, RemovedPotions: "cannot be drunk, mixed or decanted"); a click now says so instead of a silent dead option.
RemovedPotions.divineAndBlightedDoseIds.forEach { item ->
    listOf("Drink", "Empty").forEach { option ->
        if (world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).inventoryMenu.any { it.equals(option, ignoreCase = true) }) {
            on_item_option(item = item, option = option) {
                player.message("This potion has been removed from the game and can no longer be used.")
            }
        }
    }
}

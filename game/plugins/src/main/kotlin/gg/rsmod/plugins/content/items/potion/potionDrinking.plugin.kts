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

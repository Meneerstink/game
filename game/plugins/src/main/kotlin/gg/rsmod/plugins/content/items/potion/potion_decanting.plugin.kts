package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/** RCV-010 A3: derive bindings from the real cache roster, not only implemented drink effects. */
val decantFamilies = PotionDecanting.cacheFamilies(world.definitions)
PotionDecanting.bindingPairs(decantFamilies).forEach { (first, second) ->
    on_item_on_item(item1 = first, item2 = second) {
        val fromSlot = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val toSlot = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        PotionDecanting.decant(player, fromSlot, toSlot, decantFamilies)
    }
}

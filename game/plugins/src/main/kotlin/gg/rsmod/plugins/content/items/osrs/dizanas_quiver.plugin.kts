package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Dizana's quiver charging (rules in [DizanasQuiver]): Sunfire splinters used on an uncharged or charged quiver
 * add one charge per splinter, up to 20,000. Messages are ADAPTED (no sourced OSRS text for charging).
 */

val chargeableQuivers = DizanasQuiver.CHARGED_FOR.keys + DizanasQuiver.CHARGED_FOR.values

chargeableQuivers.forEach { quiverId ->
    on_item_on_item(item1 = Items.SUNFIRE_SPLINTERS, item2 = quiverId) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val quiverSlot = if (player.inventory[first]?.id in chargeableQuivers) first else second
        val quiver = player.inventory[quiverSlot] ?: return@on_item_on_item
        val splinters = player.inventory.getItemCount(Items.SUNFIRE_SPLINTERS)
        val charge = DizanasQuiver.charge(quiver, splinters)
        if (charge.added <= 0) {
            player.message("Your quiver cannot hold any more charges.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.SUNFIRE_SPLINTERS, charge.added)
        player.inventory[quiverSlot] = charge.result
        player.message("Your quiver now has ${DizanasQuiver.charges(charge.result)} charges.")
    }
}

package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item

/**
 * OSRS-IMPORT Ring of suffering recoil charging, Check and Recoil settings (rules and sources in [RingOfSuffering]).
 * Messages are ADAPTED (not sourced).
 */

fun Player.sufferingStatus(ring: Item): String {
    val state = if (RingOfSuffering.recoilEnabled(ring)) "enabled" else "disabled"
    return "Your ring of suffering has ${RingOfSuffering.charges(ring)} recoil charges. Recoil is $state."
}

RingOfSuffering.ALL.forEach { ringId ->
    listOf(Items.RING_OF_RECOIL, Items.RING_OF_RECOIL_NOTED).forEach { recoilId ->
        on_item_on_item(item1 = recoilId, item2 = ringId) {
            val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            val slot = if (player.inventory[first]?.id == ringId) first else second
            val ring = player.inventory[slot] ?: return@on_item_on_item
            val rings = RingOfSuffering.recoilsToAdd(ring, player.inventory.getItemCount(recoilId))
            if (rings <= 0) {
                player.message("Your ring of suffering cannot hold any more recoil charges.")
                return@on_item_on_item
            }
            player.inventory.remove(recoilId, rings)
            val charged = RingOfSuffering.withCharges(ring, RingOfSuffering.charges(ring) + rings * RingOfSuffering.CHARGES_PER_RECOIL)
            player.inventory[slot] = charged
            player.message(player.sufferingStatus(charged))
        }
    }
}

listOf(Items.RING_OF_SUFFERING_R, Items.RING_OF_SUFFERING_RI).forEach { ringId ->
    on_item_option(item = ringId, option = "Check") {
        val ring = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
        player.message(player.sufferingStatus(ring))
    }
    on_item_option(item = ringId, option = "Recoil settings") {
        val slot = player.getInteractingItemSlot()
        val ring = player.inventory[slot]?.takeIf { it.id == ringId } ?: return@on_item_option
        val toggled = RingOfSuffering.toggled(ring)
        player.inventory[slot] = toggled
        player.message(player.sufferingStatus(toggled))
    }
    on_equipment_option(item = ringId, option = "Check") {
        val ring = player.getEquipment(EquipmentType.RING)?.takeIf { it.id == ringId } ?: return@on_equipment_option
        player.message(player.sufferingStatus(ring))
    }
    on_equipment_option(item = ringId, option = "Recoil settings") {
        val ring = player.getEquipment(EquipmentType.RING)?.takeIf { it.id == ringId } ?: return@on_equipment_option
        val toggled = RingOfSuffering.toggled(ring)
        player.equipment[EquipmentType.RING.id] = toggled
        player.message(player.sufferingStatus(toggled))
    }
}

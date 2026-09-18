package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Amulet of blood fury creation, charging and Check (rules and sources in [BloodFury]). Messages are ADAPTED; the
 * Revert option is not bound (its result is not stated on the page read).
 */

listOf(Items.AMULET_OF_FURY, Items.AMULET_OF_BLOOD_FURY).forEach { amuletId ->
    on_item_on_item(item1 = Items.BLOOD_SHARD, item2 = amuletId) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory[first]?.id == amuletId) first else second
        val amulet = player.inventory[slot] ?: return@on_item_on_item
        val shards = BloodFury.shardsToAdd(amulet, player.inventory.getItemCount(Items.BLOOD_SHARD))
        if (shards <= 0) {
            player.message("The amulet cannot hold any more charges.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.BLOOD_SHARD, shards)
        val charged = BloodFury.withCharges(amulet, BloodFury.charges(amulet) + shards * BloodFury.CHARGES_PER_SHARD)
        player.inventory[slot] = charged
        player.message("Your amulet of blood fury has ${BloodFury.charges(charged)} charges.")
    }
}

on_item_option(item = Items.AMULET_OF_BLOOD_FURY, option = "Check") {
    val amulet = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    player.message("Your amulet of blood fury has ${BloodFury.charges(amulet)} charges.")
}

// OSRS worn op 451 "Check" (imported 2026-09-18 with the rest of the OSRS worn menus).
if (world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.AMULET_OF_BLOOD_FURY).equipmentMenu.any { it.equals("Check", true) }) {
    on_equipment_option(item = Items.AMULET_OF_BLOOD_FURY, option = "Check") {
        val amulet = player.getEquipment(EquipmentType.AMULET)?.takeIf { it.id == Items.AMULET_OF_BLOOD_FURY } ?: return@on_equipment_option
        player.message("Your amulet of blood fury has ${BloodFury.charges(amulet)} charges.")
    }
}

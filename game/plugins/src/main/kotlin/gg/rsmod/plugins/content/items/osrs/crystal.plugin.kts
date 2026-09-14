package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Bow of Faerdhinen and crystal armour charging and Check (rules and sources in [CrystalEquipment]). Messages are
 * ADAPTED. Not bound (SOURCE_GAP): the bow's Uncharge option and the armour's Revert option (the seeds returned per piece are
 * not quoted on the pages read).
 */

fun crystalHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

fun crystalStatus(item: gg.rsmod.game.model.item.Item): String = "It has ${CrystalEquipment.charges(item)} charges."

(CrystalEquipment.INACTIVE_FOR.keys + CrystalEquipment.INACTIVE_FOR.values).forEach { id ->
    // "crystal shards must be used on it (this can be done before it is inactive as well)".
    on_item_on_item(item1 = Items.CRYSTAL_SHARD, item2 = id) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory[first]?.id == id) first else second
        val item = player.inventory[slot] ?: return@on_item_on_item
        val shards = CrystalEquipment.shardsToAdd(item, player.inventory.getItemCount(Items.CRYSTAL_SHARD))
        if (shards <= 0) {
            player.message("It cannot hold any more charges.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.CRYSTAL_SHARD, shards)
        val charged = CrystalEquipment.withCharges(item, CrystalEquipment.charges(item) + shards * CrystalEquipment.CHARGES_PER_SHARD)
        player.inventory[slot] = charged
        player.message(crystalStatus(charged))
    }
    if (crystalHasOption(id, "Check", worn = false)) {
        on_item_option(item = id, option = "Check") {
            val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
            player.message(crystalStatus(item))
        }
    }
    if (crystalHasOption(id, "Check", worn = true)) {
        on_equipment_option(item = id, option = "Check") {
            val worn = EquipmentType.values().mapNotNull { player.getEquipment(it) }.firstOrNull { it.id == id } ?: return@on_equipment_option
            player.message(crystalStatus(worn))
        }
    }
}

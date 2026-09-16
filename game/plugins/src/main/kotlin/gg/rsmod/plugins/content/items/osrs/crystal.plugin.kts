package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Bow of Faerdhinen and crystal armour charging, Check, Uncharge and Revert (rules and sources in
 * [CrystalEquipment]). Messages are ADAPTED.
 *
 * Uncharge/Revert sourced from OSRS Wiki "Bow of faerdhinen" and "Crystal equipment" (fetched 2026-09-16):
 * - Armour ("Crystal equipment#Reverting"): "Dismantling a crystal armour piece will return the crystal armour seeds to the
 *   player but all crystal shard charges that were previously loaded will be lost" - exactly one [Items.CRYSTAL_ARMOUR_SEED]
 *   per piece, from either its active or inactive state (both list "Revert" in this cache).
 * - Bow of Faerdhinen ("Bow of faerdhinen#Reverting"): "Unlike other crystal equipment, players cannot directly revert the
 *   bow to an enhanced crystal weapon seed. In order to receive the seed back, players must bring an inactive bow to a
 *   singing bowl" - so its "Uncharge" (only listed on the active [Items.BOW_OF_FAERDHINEN], not the inactive one) is not a
 *   seed reversion at all, only a manual discharge to the inactive item with the shards lost, i.e. the same transition
 *   [CrystalEquipment.withCharges] already performs at 0 charges. Singing bowls are not implemented (SOURCE_GAP, unrelated
 *   Prifddinas content) so the true seed-reversion route stays unavailable, matching real OSRS' own "not recommended" framing.
 * - Crystal bow ("Crystal bow#Reverting"): the wiki only documents the uncharged case - "When uncharged, it can also be
 *   converted back into a crystal weapon seed" - and says nothing about reverting a still-charged one; that route was
 *   already correctly left unbound below (`osrs_bows.plugin.kts` binds [Items.CRYSTAL_BOW_OSRS_INACTIVE]'s "Revert" only).
 *   Directly re-checking that page for the charged case found no further detail, so it stays SOURCE_GAP rather than
 *   assuming the same one-seed-and-lose-charges shape crystal armour uses.
 * - Bow of Faerdhinen (c)'s own "Uncharge" (it "stays charged permanently" per the wiki, so what discharging it actually
 *   produces is not stated) is left unbound: SOURCE_GAP, not guessed.
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
    if (crystalHasOption(id, "Uncharge", worn = false)) {
        on_item_option(item = id, option = "Uncharge") {
            val slot = player.getInteractingItemSlot()
            val item = player.inventory[slot] ?: return@on_item_option
            player.inventory[slot] = CrystalEquipment.withCharges(item, 0)
            player.message("You discharge the bow. Any crystal shards used to charge it are lost.")
        }
    }
}

CrystalEquipment.REVERT_SEED.forEach { (id, seed) ->
    if (crystalHasOption(id, "Revert", worn = false)) {
        on_item_option(item = id, option = "Revert") {
            val slot = player.getInteractingItemSlot()
            player.inventory[slot] ?: return@on_item_option
            player.inventory[slot] = gg.rsmod.game.model.item.Item(seed, 1)
            val itemName = world.definitions.get(ItemDef::class.java, seed).name.lowercase()
            player.message("You revert the item back into a $itemName. Any crystal shard charges are lost.")
        }
    }
}

package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * Serpentine helm: Zulrah's scales charging, Check, Uncharge and the uncharged-helm equip block (rules and sources in
 * [SerpentineHelm]). ADAPTED: the messages. Options are bound only when the imported definition carries them.
 */

fun helmHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

fun helmStatus(scales: Int): String = "Your serpentine helm has $scales Zulrah's scales (${scales * 100 / SerpentineHelm.MAX_SCALES}%)."

// "It cannot be equipped if it is uncharged".
can_equip_item(Items.SERPENTINE_HELM_UNCHARGED) {
    player.message("You need to charge the helm with Zulrah's scales before you can wear it.")
    false
}

listOf(Items.SERPENTINE_HELM_UNCHARGED, Items.SERPENTINE_HELM).forEach { helm ->
    on_item_on_item(item1 = Items.ZULRAHS_SCALES, item2 = helm) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory[first]?.id == helm) first else second
        val item = player.inventory[slot]?.takeIf { it.id == helm } ?: return@on_item_on_item
        val current = SerpentineHelm.scales(item)
        val added = minOf(player.inventory.getItemCount(Items.ZULRAHS_SCALES), SerpentineHelm.MAX_SCALES - current)
        if (added <= 0) {
            player.message("Your serpentine helm cannot hold any more Zulrah's scales.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.ZULRAHS_SCALES, added)
        player.inventory[slot] = SerpentineHelm.withScales(item, current + added)
        player.message(helmStatus(current + added))
    }
}

if (helmHasOption(Items.SERPENTINE_HELM, "Check", worn = false)) {
    on_item_option(item = Items.SERPENTINE_HELM, option = "Check") {
        val item = player.inventory[player.getInteractingItemSlot()]?.takeIf { it.id == Items.SERPENTINE_HELM } ?: return@on_item_option
        player.message(helmStatus(SerpentineHelm.scales(item)))
    }
}
if (helmHasOption(Items.SERPENTINE_HELM, "Check", worn = true)) {
    on_equipment_option(item = Items.SERPENTINE_HELM, option = "Check") {
        player.message(helmStatus(SerpentineHelm.scales(SerpentineHelm.worn(player))))
    }
}
if (helmHasOption(Items.SERPENTINE_HELM, "Uncharge", worn = false)) {
    on_item_option(item = Items.SERPENTINE_HELM, option = "Uncharge") {
        val slot = player.getInteractingItemSlot()
        val item = player.inventory[slot]?.takeIf { it.id == Items.SERPENTINE_HELM } ?: return@on_item_option
        val scales = SerpentineHelm.scales(item)
        if (scales > 0 && !player.inventory.add(Items.ZULRAHS_SCALES, scales, assureFullInsertion = true).hasSucceeded()) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        player.inventory[slot] = SerpentineHelm.withScales(item, 0)
    }
}

// OSRS Wiki "Serpentine helm": "charged with a chisel on a serpentine visage" - 52 Crafting, "120 Crafting experience" - and the
// uncharged helm "can be dismantled to receive 20,000 Zulrah's scales".
on_item_on_item(item1 = Items.CHISEL, item2 = Items.SERPENTINE_VISAGE) {
    if (player.skills.getCurrentLevel(Skills.CRAFTING) < 52) {
        player.message("You need a Crafting level of 52 to do that.")
        return@on_item_on_item
    }
    if (!player.inventory.remove(Items.SERPENTINE_VISAGE, 1).hasSucceeded()) return@on_item_on_item
    player.inventory.add(Items.SERPENTINE_HELM_UNCHARGED, 1)
    player.addXp(Skills.CRAFTING, 120.0)
    player.message("You carve the serpentine visage into a helm.")
}

if (helmHasOption(Items.SERPENTINE_HELM_UNCHARGED, "Dismantle", worn = false)) {
    on_item_option(item = Items.SERPENTINE_HELM_UNCHARGED, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.SERPENTINE_HELM_UNCHARGED) return@on_item_option
        player.inventory[slot] = null
        if (!player.inventory.add(Items.ZULRAHS_SCALES, 20_000, assureFullInsertion = true).hasSucceeded()) {
            player.inventory[slot] = gg.rsmod.game.model.item.Item(Items.SERPENTINE_HELM_UNCHARGED)
            player.message("You don't have enough inventory space to do that.")
        }
    }
}

// Neitiznot faceguard: OSRS makes it with Olaf the Bard ("Talk to Olaf the Bard with a basilisk jaw"); ADAPTED here as the basilisk jaw
// used on a helm of neitiznot. Dismantle gives both parts back.
on_item_on_item(item1 = Items.BASILISK_JAW, item2 = Items.HELM_OF_NEITIZNOT) {
    if (!player.inventory.contains(Items.BASILISK_JAW) || !player.inventory.contains(Items.HELM_OF_NEITIZNOT)) return@on_item_on_item
    player.inventory.remove(Items.BASILISK_JAW, 1)
    player.inventory.remove(Items.HELM_OF_NEITIZNOT, 1)
    player.inventory.add(Items.NEITIZNOT_FACEGUARD, 1)
    player.message("You fit the basilisk jaw onto the helm.")
}

if (helmHasOption(Items.NEITIZNOT_FACEGUARD, "Dismantle", worn = false)) {
    on_item_option(item = Items.NEITIZNOT_FACEGUARD, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.NEITIZNOT_FACEGUARD) return@on_item_option
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        player.inventory[slot] = gg.rsmod.game.model.item.Item(Items.HELM_OF_NEITIZNOT)
        player.inventory.add(Items.BASILISK_JAW, 1)
    }
}

// Amulet of the damned "Check": the degradation left of the used amulet (EquipmentDegradation charges). ADAPTED wording.
fun damnedStatus(item: gg.rsmod.game.model.item.Item?): String {
    val total = gg.rsmod.plugins.content.items.armor.BarrowsDegradation.STAGE_CYCLES * 4
    val left = item?.attr?.get(gg.rsmod.game.model.item.ItemAttribute.CHARGES) ?: total
    return "Your amulet of the damned has ${(left.toLong() * 100 / total).coerceIn(0, 100)}% of its charge left."
}
if (helmHasOption(Items.AMULET_OF_THE_DAMNED, "Check", worn = false)) {
    on_item_option(item = Items.AMULET_OF_THE_DAMNED, option = "Check") {
        player.message(damnedStatus(player.inventory[player.getInteractingItemSlot()]))
    }
}
if (helmHasOption(Items.AMULET_OF_THE_DAMNED, "Check", worn = true)) {
    on_equipment_option(item = Items.AMULET_OF_THE_DAMNED, option = "Check") {
        player.message(damnedStatus(player.getEquipment(EquipmentType.AMULET)))
    }
}
package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.plugins.content.combat.Combat

/**
 * OSRS-IMPORT powered staves: charging (use a charge item on the staff, or the Sanguinesti staff's Charge option), Check,
 * Uncharge, the Magic fang upgrade and Dismantle (rules and sources in [PoweredStaves]). Messages are ADAPTED (no sourced OSRS
 * text on the pages read). SOURCE_GAP: Crafting experience for the Magic fang upgrade (not stated on the Magic fang or Trident
 * of the Swamp pages) - none is given; the amount prompt of Charge (all affordable charges are added).
 */

fun hasOption(
    itemId: Int,
    option: String,
    worn: Boolean = false,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    val menu = if (worn) def.equipmentMenu else def.inventoryMenu
    return menu.any { it.equals(option, ignoreCase = true) }
}

fun chargesMessage(item: gg.rsmod.game.model.item.Item): String = "Your weapon has ${PoweredStaves.charges(item)} charges."

/** Adds every affordable charge to the staff in inventory [slot]. */
fun chargeStaff(
    player: gg.rsmod.game.model.entity.Player,
    staff: PoweredStaves.Staff,
    slot: Int,
) {
    val item = player.inventory[slot]?.takeIf { it.id in staff.ids } ?: return
    if (player.skills.getCurrentLevel(Skills.MAGIC) < staff.requiredMagic) {
        player.message("You need a Magic level of ${staff.requiredMagic} to charge this weapon.")
        return
    }
    val current = PoweredStaves.charges(item)
    val added = PoweredStaves.chargesAffordable(staff, current) { id -> player.inventory.getItemCount(id) }
    if (added <= 0) {
        player.message(if (current >= staff.maxCharges) "Your weapon is already fully charged." else "You don't have the items needed to charge this weapon.")
        return
    }
    staff.chargeCost.forEach { cost -> player.inventory.remove(cost.id, cost.amount * added) }
    val charged = PoweredStaves.withCharges(item, current + added)
    player.inventory[slot] = charged
    player.message(chargesMessage(charged))
}

PoweredStaves.Staff.values().forEach { staff ->
    // Charging: use any charge item on an uncharged or partially charged staff ((full) is at the cap).
    listOf(staff.uncharged, staff.charged).forEach { staffId ->
        staff.chargeCost.map { it.id }.forEach { costId ->
            on_item_on_item(item1 = costId, item2 = staffId) {
                val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
                val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
                chargeStaff(player, staff, if (player.inventory[first]?.id in staff.ids) first else second)
            }
        }
        if (hasOption(staffId, "Charge")) {
            on_item_option(item = staffId, option = "Charge") {
                chargeStaff(player, staff, player.getInteractingItemSlot())
            }
        }
    }

    staff.ids.forEach { staffId ->
        if (hasOption(staffId, "Check")) {
            on_item_option(item = staffId, option = "Check") {
                val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
                player.message(chargesMessage(item))
            }
        }
        if (hasOption(staffId, "Check", worn = true)) {
            on_equipment_option(item = staffId, option = "Check") {
                val item = player.getEquipment(EquipmentType.WEAPON) ?: return@on_equipment_option
                player.message(chargesMessage(item))
            }
        }
        if (hasOption(staffId, "Uncharge")) {
            on_item_option(item = staffId, option = "Uncharge") {
                val slot = player.getInteractingItemSlot()
                val item = player.inventory[slot]?.takeIf { it.id == staffId } ?: return@on_item_option
                val charges = PoweredStaves.charges(item)
                if (charges <= 0) return@on_item_option
                val refund = PoweredStaves.refund(staff, charges)
                val slotsNeeded = refund.count { !player.inventory.contains(it.id) }
                if (player.inventory.freeSlotCount < slotsNeeded) {
                    player.message("You don't have enough inventory space.")
                    return@on_item_option
                }
                refund.forEach { player.inventory.add(it.id, it.amount) }
                player.inventory[slot] = PoweredStaves.withCharges(item, 0)
                player.message("You uncharge your weapon.")
            }
        }
        // Powered staves "cannot be used to autocast spells": equipping one forgets the autocast choice - done centrally by
        // Autocast.onWeaponChanged ("unless you equip a staff that cannot autocast the chosen spell").
    }
}

// Trident of the Swamp: "using a magic fang on it with a chisel" at 59 Crafting; reversible while uncharged.
PoweredStaves.TOXIC_UPGRADE.forEach { (trident, toxic) ->
    on_item_on_item(item1 = Items.MAGIC_FANG, item2 = trident) {
        if (!player.inventory.contains(Items.CHISEL)) {
            player.message("You need a chisel to do that.")
            return@on_item_on_item
        }
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < PoweredStaves.SWAMP_CRAFTING_LEVEL) {
            player.message("You need a Crafting level of ${PoweredStaves.SWAMP_CRAFTING_LEVEL} to do that.")
            return@on_item_on_item
        }
        if (player.inventory.remove(Items.MAGIC_FANG, 1).hasSucceeded() && player.inventory.remove(trident, 1).hasSucceeded()) {
            player.inventory.add(toxic, 1)
        }
    }
    if (hasOption(toxic, "Dismantle")) {
        on_item_option(item = toxic, option = "Dismantle") {
            val slot = player.getInteractingItemSlot()
            if (player.inventory[slot]?.id != toxic) return@on_item_option
            if (!player.inventory.hasSpace) {
                player.message("You don't have enough inventory space.")
                return@on_item_option
            }
            player.inventory[slot] = gg.rsmod.game.model.item.Item(trident)
            player.inventory.add(Items.MAGIC_FANG, 1)
        }
    }
}

// Magic fang: "Players can dismantle the fang to receive 20,000 Zulrah's scales."
if (hasOption(Items.MAGIC_FANG, "Dismantle")) {
    on_item_option(item = Items.MAGIC_FANG, option = "Dismantle") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != Items.MAGIC_FANG) return@on_item_option
        if (!player.inventory.contains(Items.ZULRAHS_SCALES) && player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space.")
            return@on_item_option
        }
        player.inventory.remove(Items.MAGIC_FANG, 1, beginSlot = slot)
        player.inventory.add(Items.ZULRAHS_SCALES, 20_000)
    }
}

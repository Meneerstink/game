package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.plugins.content.skills.slayer.SlayersStaffE.ENCHANTMENT
import gg.rsmod.plugins.content.skills.slayer.SlayersStaffE.STAFF
import gg.rsmod.plugins.content.skills.slayer.SlayersStaffE.STAFF_E

/**
 * N570 Slayer's enchantment on a Slayer's staff (Transcript:Slayer's enchantment) and the staff (e) options: inventory Wield/Check/Revert,
 * worn Check (OSRS Wiki infobox). Using the enchantment on a staff (e) is not bound: the game's "Nothing interesting happens.".
 */
on_item_on_item(ENCHANTMENT, STAFF) {
    player.queue {
        itemMessageBox(SlayersStaffE.ENCHANT_INFO, STAFF_E)
        if (options("Yes", "No", title = SlayersStaffE.ENCHANT_QUESTION) != 1) return@queue
        // Re-check after the dialogue: the items may have moved or gone meanwhile.
        val slot = player.inventory.getItemIndex(STAFF, skipAttrItems = false)
        if (slot == -1 || !player.inventory.contains(ENCHANTMENT)) return@queue
        if (!player.inventory.remove(ENCHANTMENT, 1).hasSucceeded()) return@queue
        player.inventory[slot] = SlayersStaffE.withCharges(SlayersStaffE.MAX_CHARGES)
        doubleItemMessageBox(SlayersStaffE.ENCHANT_DONE, STAFF, ENCHANTMENT)
    }
}

on_item_option(item = STAFF_E, option = "check") {
    val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    SlayersStaffE.check(player, item)
}

on_equipment_option(item = STAFF_E, option = "check") {
    val item = player.equipment[gg.rsmod.plugins.api.EquipmentType.WEAPON.id] ?: return@on_equipment_option
    SlayersStaffE.check(player, item)
}

on_item_option(item = STAFF_E, option = "revert") {
    val slot = player.getInteractingItemSlot()
    val item = player.inventory[slot] ?: return@on_item_option
    player.queue {
        messageBox(SlayersStaffE.chargesLine(SlayersStaffE.charges(item)))
        if (options("Yes", "No") != 1) return@queue
        if (player.inventory[slot]?.id != STAFF_E) return@queue
        player.inventory[slot] = gg.rsmod.game.model.item.Item(STAFF)
    }
}

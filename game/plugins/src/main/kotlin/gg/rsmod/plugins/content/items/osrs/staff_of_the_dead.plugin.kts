package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS-IMPORT Staff of the dead / Toxic staff of the dead actions and Power of Death (rules and sources in [StaffOfTheDead]).
 * Power of Death uses the 667 Staff of light look (animation 12804, graphic 2319): ADAPTED, OSRS sequence/spotanim not imported.
 * Check/Uncharge/Dismantle/fang messages are ADAPTED (no sourced wording).
 */

StaffOfTheDead.POWER_OF_DEATH_STAVES.forEach { staff ->
    SpecialAttacks.registerInstant(StaffOfTheDead.POWER_OF_DEATH_ENERGY, staff) { p ->
        p.animate(12804)
        // Staff of the dead family: OSRS SOTD_SPECIAL_START (fxpilot); the Staff of light keeps its 667 graphic.
        p.graphic(if (staff == Items.STAFF_OF_LIGHT) 2319 else OsrsGfx.SOTD_SPECIAL_START)
        StaffOfTheDead.activatePowerOfDeath(p)
    }
}

listOf(Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD).forEach { staffId ->
    on_item_on_item(item1 = Items.ZULRAHS_SCALES, item2 = staffId) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory[first]?.id == staffId) first else second
        val staff = player.inventory[slot] ?: return@on_item_on_item
        val added = StaffOfTheDead.scalesToAdd(staff, player.inventory.getItemCount(Items.ZULRAHS_SCALES))
        if (added <= 0) {
            player.message("Your staff cannot hold any more scales.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.ZULRAHS_SCALES, added)
        val charged = StaffOfTheDead.withScales(staff, StaffOfTheDead.scales(staff) + added)
        player.inventory[slot] = charged
        player.message("Scales: ${StaffOfTheDead.scales(charged)}")
    }
}

on_item_option(item = Items.TOXIC_STAFF_OF_THE_DEAD, option = "Check") {
    val staff = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    player.message("Scales: ${StaffOfTheDead.scales(staff)}")
}

on_equipment_option(item = Items.TOXIC_STAFF_OF_THE_DEAD, option = "Check") {
    val staff = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == Items.TOXIC_STAFF_OF_THE_DEAD } ?: return@on_equipment_option
    player.message("Scales: ${StaffOfTheDead.scales(staff)}")
}

// Wiki: Uncharge returns the scales and leaves the uncharged staff.
on_item_option(item = Items.TOXIC_STAFF_OF_THE_DEAD, option = "Uncharge") {
    val slot = player.getInteractingItemSlot()
    val staff = player.inventory[slot]?.takeIf { it.id == Items.TOXIC_STAFF_OF_THE_DEAD } ?: return@on_item_option
    val scales = StaffOfTheDead.scales(staff)
    if (scales > 0 && !player.inventory.add(Items.ZULRAHS_SCALES, scales, assureFullInsertion = true).hasSucceeded()) {
        player.message("You don't have enough inventory space to uncharge the staff.")
        return@on_item_option
    }
    player.inventory[slot] = StaffOfTheDead.withScales(staff, 0)
}

// Wiki: Magic fang on a Staff of the dead, 59 Crafting (no experience); reversible while uncharged (Dismantle).
on_item_on_item(item1 = Items.MAGIC_FANG, item2 = Items.STAFF_OF_THE_DEAD) {
    if (player.skills.getCurrentLevel(Skills.CRAFTING) < StaffOfTheDead.FANG_CRAFTING_LEVEL) {
        player.message("You need a Crafting level of ${StaffOfTheDead.FANG_CRAFTING_LEVEL} to do that.")
        return@on_item_on_item
    }
    if (!player.inventory.contains(Items.MAGIC_FANG) || !player.inventory.contains(Items.STAFF_OF_THE_DEAD)) return@on_item_on_item
    player.inventory.remove(Items.MAGIC_FANG, 1)
    player.inventory.remove(Items.STAFF_OF_THE_DEAD, 1)
    player.inventory.add(Items.TOXIC_STAFF_UNCHARGED, 1)
    player.message("You attach the magic fang to the staff of the dead.")
}

on_item_option(item = Items.TOXIC_STAFF_UNCHARGED, option = "Dismantle") {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != Items.TOXIC_STAFF_UNCHARGED) return@on_item_option
    if (player.inventory.freeSlotCount < 1) {
        player.message("You need some free space to dismantle it.")
        return@on_item_option
    }
    player.inventory[slot] = Item(Items.STAFF_OF_THE_DEAD)
    player.inventory.add(Items.MAGIC_FANG, 1)
}

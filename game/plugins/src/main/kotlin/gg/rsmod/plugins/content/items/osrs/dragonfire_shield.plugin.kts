package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef

/**
 * OSRS-IMPORT Inspect / Check / Empty of the breath-charged shields: Dragonfire shield, Dragonfire ward, Ancient wyvern shield (rules
 * and sources in [DragonfireShield]). Owner live report 2026-09-17c: "dragonfire ward inspect empty unhandled item" - the first build
 * bound the Dragonfire shield alone. The wyvern shield's Empty message is the wiki's; the other wording follows it (ADAPTED).
 */
fun shieldHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

DragonfireShield.FAMILIES.forEach { family ->
    family.ids.forEach { id ->
        if (shieldHasOption(id, "Inspect", worn = false)) {
            on_item_option(item = id, option = "Inspect") {
                val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
                player.message(DragonfireShield.inspectMessage(item))
            }
        }
        if (shieldHasOption(id, "Check", worn = true)) {
            on_equipment_option(item = id, option = "Check") {
                val item = player.getEquipment(EquipmentType.SHIELD)?.takeIf { it.id == id } ?: return@on_equipment_option
                player.message(DragonfireShield.inspectMessage(item))
            }
        }
        if (shieldHasOption(id, "Empty", worn = false)) {
            on_item_option(item = id, option = "Empty") {
                val slot = player.getInteractingItemSlot()
                val item = player.inventory[slot]?.takeIf { it.id == id } ?: return@on_item_option
                if (DragonfireShield.charges(item) <= 0) {
                    player.message("The ${family.noun} has no charges.")
                    // A charged-id item without charges (spawned, or from before this model) still becomes the uncharged item.
                    player.inventory[slot] = DragonfireShield.withCharges(item, 0)
                    return@on_item_option
                }
                player.inventory[slot] = DragonfireShield.withCharges(item, 0)
                // Jagex config name "dragonslayer_shield_empty" (OSRS Wiki sound list; same id in the 667 cache).
                player.playSound(Sfx.DRAGONSLAYER_SHIELD_EMPTY)
                player.message(family.emptyMessage)
            }
        }
    }
}

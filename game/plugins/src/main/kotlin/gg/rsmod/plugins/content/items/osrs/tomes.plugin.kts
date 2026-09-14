package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR

/**
 * OSRS-IMPORT Tome of Fire / Tome of Water pages, Check and Pages (rules and sources in [Tomes]). Messages are ADAPTED; the
 * Pages option shows the page count (its OSRS behaviour is not described on the pages read).
 */

fun tomeHasOption(
    itemId: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, itemId)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

fun tomeStatus(item: gg.rsmod.game.model.item.Item): String {
    val charges = Tomes.charges(item)
    return "Your tome has $charges charges (${charges / Tomes.CHARGES_PER_PAGE} pages)."
}

Tomes.Tome.values().forEach { tome ->
    listOf(tome.empty, tome.charged).forEach { tomeId ->
        on_item_on_item(item1 = tome.page, item2 = tomeId) {
            val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            val slot = if (player.inventory[first]?.id == tomeId) first else second
            val item = player.inventory[slot] ?: return@on_item_on_item
            val pages = Tomes.pagesToAdd(item, player.inventory.getItemCount(tome.page))
            if (pages <= 0) {
                player.message("Your tome cannot hold any more pages.")
                return@on_item_on_item
            }
            player.inventory.remove(tome.page, pages)
            val charged = Tomes.withCharges(item, Tomes.charges(item) + pages * Tomes.CHARGES_PER_PAGE)
            player.inventory[slot] = charged
            player.message(tomeStatus(charged))
        }
        listOf("Check", "Pages").forEach { option ->
            if (tomeHasOption(tomeId, option, worn = false)) {
                on_item_option(item = tomeId, option = option) {
                    val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
                    player.message(tomeStatus(item))
                }
            }
            if (tomeHasOption(tomeId, option, worn = true)) {
                on_equipment_option(item = tomeId, option = option) {
                    val item = player.getEquipment(EquipmentType.SHIELD) ?: return@on_equipment_option
                    player.message(tomeStatus(item))
                }
            }
        }
    }
}

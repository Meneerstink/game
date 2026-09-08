package gg.rsmod.plugins.content.items.charges

import gg.rsmod.game.fs.def.ItemDef

/**
 * Binds `Check-charges` for every item the cache actually gives that option to, in both the
 * inventory and the worn-equipment menu.
 *
 * The set is taken from the item definitions at load time rather than from a hand-written id list:
 * the option sits at inventory index 3 for most items but 4 for the Crystal saw, the Enchanted
 * water tiara and the Sceptre of the gods, and at worn index 1, 2 or 4 depending on the item, and
 * every one of those variants is a slot a hand-written list would have missed.
 *
 * Worn options are offset by one against the cache's `equipmentMenu`: op1 on an interface 387 slot
 * is always "Remove", and `equipment.plugin.kts` maps the remaining opcodes onto
 * `executeEquipmentOption` starting at 1 - which is what `on_equipment_option` binds. 387's worn
 * slots bake ops 1-6 and 10 (`events=0x00047e`), so all of these reach the server.
 */

world.definitions.getAllKeys(ItemDef::class.java).forEach { id ->
    val def = world.definitions.get(ItemDef::class.java, id)

    if (def.inventoryMenu.any { it.equals(ItemCharges.OPTION, ignoreCase = true) }) {
        on_item_option(item = id, option = ItemCharges.OPTION) {
            ItemCharges.check(player, player.inventory[player.getInteractingItemSlot()])
        }
    }

    if (def.equipmentMenu.any { it.equals(ItemCharges.OPTION, ignoreCase = true) }) {
        on_equipment_option(item = id, option = ItemCharges.OPTION) {
            ItemCharges.check(player, player.equipment[def.equipSlot])
        }
    }
}

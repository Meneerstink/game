package gg.rsmod.plugins.content.items.combine

import gg.rsmod.util.Misc

/*
 * One binding per CombinationData row. The rows used to be looked up by their first item only, which bound the same first item
 * again for every row sharing it (OSRS-IMPORT magearmour: one colour kit combines with three robe pieces) and crashed the boot with
 * a duplicate item-on-item binding.
 */
CombinationData.values.forEach { def ->
    if (def.tool != CombinationTool.NONE) {
        on_item_on_item(item1 = def.items[0], item2 = def.tool.item) {
            player.queue(TaskPriority.WEAK) {
                CombinationAction.combine(this, def)
            }
        }
    } else {
        on_item_on_item(itemUsed = def.items[0], itemsList = def.items) {
            player.queue(TaskPriority.WEAK) {
                CombinationAction.combine(this, def)
            }
        }
    }
}

/*
 * K12 N11 (census 2026-10-30: "Attach" on the Menaphite ornament kit did nothing - the owner clicked it live): an item whose own
 * inventory menu says "Attach" attaches itself through its combination row, exactly as using it on the item does. The row is picked
 * by the other item the player carries; without one the player is told what it goes on.
 */
CombinationData.values
    .filter { def -> def.tool == CombinationTool.NONE }
    .groupBy { def -> def.items[0] }
    .filterKeys { kit -> world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, kit).inventoryMenu.any { it.equals("Attach", true) } }
    .forEach { (kit, rows) ->
        on_item_option(item = kit, option = "Attach") {
            val row = rows.firstOrNull { def -> def.items.drop(1).all { player.inventory.contains(it) } }
            if (row == null) {
                val targets = rows.map { world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, it.items[1]).name }.distinct()
                player.message("You need ${targets.joinToString(" or ") { Misc.formatWithIndefiniteArticle(it) }} to attach this to.")
                return@on_item_option
            }
            player.queue(TaskPriority.WEAK) {
                CombinationAction.combine(this, row)
            }
        }
    }

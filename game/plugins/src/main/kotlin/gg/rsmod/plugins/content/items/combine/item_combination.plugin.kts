package gg.rsmod.plugins.content.items.combine

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

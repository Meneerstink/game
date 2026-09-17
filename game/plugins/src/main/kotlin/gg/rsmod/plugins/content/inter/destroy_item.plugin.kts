package gg.rsmod.plugins.content.inter

val destroyableItems = mutableListOf<Int>()
val destroyItemAction = mutableMapOf<Int, (Player) -> Unit>()

// define destroy item action
destroyItemAction[553] = { player -> player.setVarbit(Varbits.MUDDY_SKULL_TAKEN, 0) }

// Loop through each item in the specified range
for (itemId in 1..20653) {
    // Check if item is in cache
    if (itemId < world.definitions.getCount(ItemDef::class.java)) {
        // Get the item definition
        val def = world.definitions.get(ItemDef::class.java, itemId)

        // Check if the destroy option exists
        if (def.inventoryMenu.getOrNull(4)?.lowercase() == "destroy") {
            // Add the item id to the destroyableItems list
            destroyableItems.add(itemId)
        }
    }
}
// Then, bind the "destroy" option to each item
destroyableItems.forEach {
    on_item_option(it, 10) {
        val itemId = it
        player.queue(TaskPriority.WEAK) {
            destroyItemAction[itemId]?.invoke(player)
            destroyItem(itemId)
        }
    }
}

// Items above the old fixed range (the OSRS imports: Dizana's quivers and max capes, rune pouches, ...) had no Destroy dialogue unless
// their own plugin bound one. Bound late, after every plugin, so an item-specific Destroy (e.g. one that refunds contents) keeps priority.
on_world_init_late {
    for (itemId in 20654 until world.definitions.getCount(ItemDef::class.java)) {
        val def = world.definitions.getNullable(ItemDef::class.java, itemId) ?: continue
        if (def.inventoryMenu.getOrNull(4)?.lowercase() != "destroy" || world.plugins.hasItemOption(itemId, 10)) continue
        on_item_option(itemId, 10) {
            player.queue(TaskPriority.WEAK) { destroyItem(itemId) }
        }
    }
}

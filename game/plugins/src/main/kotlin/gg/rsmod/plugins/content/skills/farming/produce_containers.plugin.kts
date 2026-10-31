package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.game.fs.def.ItemDef

ProduceContainers.ids.forEach { id ->
    listOf("Fill", "Empty", "Remove-one").forEach { option ->
        // The 667 net has Empty, sacks have Remove-one too. Never fabricate cache options.
        if (world.definitions.get(ItemDef::class.java, id).inventoryMenu.any { it.equals(option, true) }) {
            on_item_option(id, option) {
                ProduceContainers.operate(player.inventory, player.getInteractingItemSlot(), id, option)?.let { player.message(it) }
            }
        }
    }
    (ProduceContainers.families.map { it.produce } + ProduceContainers.families.map { it.produce + 1 }).distinct().forEach { produce ->
        on_item_on_item(id, produce) {
            val slot = listOf(player.getInteractingItemSlot(), player.getInteractingOtherItemSlot())
                .firstOrNull { player.inventory[it]?.id == id } ?: return@on_item_on_item
            ProduceContainers.operate(player.inventory, slot, id, "Fill", produce)?.let { player.message(it) }
        }
    }
}

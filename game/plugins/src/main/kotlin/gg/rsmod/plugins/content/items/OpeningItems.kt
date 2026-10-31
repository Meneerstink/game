package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/** Item-specific results, one inventory transaction. Sources: OSRS Wiki Oyster, Hunter kit, Airtight pot. */
object OpeningItems {
    val fixed = mapOf(
        Items.HUNTER_KIT to listOf(Items.NOOSE_WAND, Items.BUTTERFLY_NET, Items.BIRD_SNARE, Items.RABBIT_SNARE,
            Items.TEASING_STICK, Items.UNLIT_TORCH, Items.BOX_TRAP, Items.IMPLING_JAR),
        Items.AIRTIGHT_POT to listOf(Items.EMPTY_POT, Items.POT_LID),
    )

    fun rewards(id: Int, oysterRoll: Int): List<Item>? = when (id) {
        Items.OYSTER -> listOf(Item(if (oysterRoll < 25) Items.OYSTER_PEARL else Items.EMPTY_OYSTER))
        else -> fixed[id]?.map { Item(it) }
    }

    fun open(inventory: ItemContainer, slot: Int, id: Int, oysterRoll: Int): Boolean {
        require(oysterRoll in 0..31)
        val outputs = rewards(id, oysterRoll) ?: return false
        return ItemContents.exchange(inventory, slot, id, outputs, requireFreeSlot = true)
    }
}

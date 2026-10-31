package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.ItemContents

/** OSRS Wiki Potatoes / Onions / Cabbages; RS Wiki Seaweed (667-only net). Only unnoted ids. */
object ProduceContainers {
    data class Family(val produce: Int, val empty: Int, val first: Int, val stride: Int, val name: String) {
        fun id(count: Int): Int = if (count == 0) empty else first + (count - 1) * stride
        fun count(id: Int): Int? = if (id == empty) 0 else (1..10).firstOrNull { id(it) == id }
    }
    val sacks = listOf(
        Family(Items.CABBAGE, Items.EMPTY_SACK, 5460, 2, "cabbage"),
        Family(Items.POTATO, Items.EMPTY_SACK, 5420, 2, "potato"),
        Family(Items.ONION, Items.EMPTY_SACK, 5440, 2, "onion"),
    )
    val net = Family(Items.SEAWEED, Items.EMPTY_SEAWEED_NET, 14860, 1, "seaweed")
    val families = sacks + net
    val ids = families.flatMap { family -> (0..10).map { family.id(it) } }.toSet()

    fun operate(inventory: ItemContainer, slot: Int, expected: Int, option: String, produce: Int? = null): String? {
        if (inventory[slot]?.id != expected || inventory[slot]?.amount != 1) return null
        val candidates = families.filter { it.count(expected) != null }
        if (candidates.isEmpty()) return null
        val family = if (produce != null) candidates.firstOrNull { it.produce == produce }
            else candidates.firstOrNull { inventory.contains(it.produce) } ?: candidates.first()
        if (family == null) return "You can't put that in this container."
        val count = family.count(expected)!!
        val label = if (family == net) "net" else "sack"
        val plural = if (family == net) family.name else family.name + if (family.name.endsWith("o")) "es" else "s"
        when (option.lowercase()) {
            "fill" -> {
                if (count == 10) return "The ${family.name} $label is already full."
                if (expected == Items.EMPTY_SACK && produce == null && candidates.none { inventory.contains(it.produce) })
                    return "You don't have any potatoes, onions or cabbages."
                val amount = minOf(10 - count, inventory.getItemCount(family.produce))
                if (amount == 0) return "You don't have any $plural to put in the $label."
                // Filling only removes produce and changes the container in place: it cannot need more space.
                if (!inventory.remove(family.produce, amount, assureFullRemoval = true).hasSucceeded()) return null
                inventory[slot] = Item(family.id(count + amount))
            }
            "empty", "remove-one" -> {
                if (count == 0) return "This $label is already empty."
                val amount = if (option.equals("empty", true)) minOf(count, inventory.freeSlotCount) else 1
                if (amount == 0) return ItemContents.NO_SPACE
                if (!ItemContents.exchange(inventory, slot, expected,
                        listOf(Item(family.id(count - amount)), Item(family.produce, amount)))) return ItemContents.NO_SPACE
                if (option.equals("remove-one", true)) {
                    val article = if (family.name.first() in "aeiou") "an" else "a"
                    return "You take $article ${family.name} out of the ${family.name} $label."
                }
            }
        }
        return null
    }
}

package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * RCV-010 A3 (owner live 2026-09-13: potion on potion did nothing): decanting for every four-dose potion
 * family, derived once from the [Potion] replacement chains so a new potion can never miss it.
 *
 * Source: Novite 667 `Pots.mixPot` (pour, split into an empty vial, one-dose swap, full target refuses)
 * and its messages.
 */
object PotionDecanting {
    const val DOSES = 4
    const val POUR_MESSAGE = "You pour from one container into the other."
    const val SPLIT_MESSAGE = "You split the potion between the two vials."

    /** Each family's item ids ordered 4, 3, 2, 1 doses. */
    val families: List<IntArray> by lazy {
        val byItem = Potion.values().associateBy { it.item }
        val replacements = Potion.values().map { it.replacement }.toSet()
        Potion.values()
            .filter { it.item !in replacements }
            .map { start ->
                generateSequence(start) { byItem[it.replacement] }.toList()
            }
            .filter { chain -> chain.size == DOSES && chain.last().replacement == Items.VIAL }
            .map { chain -> chain.map { it.item }.toIntArray() }
    }

    private val familyOf: Map<Int, IntArray> by lazy {
        families.flatMap { family -> family.map { it to family } }.toMap()
    }

    fun doses(item: Int): Int {
        val family = familyOf[item] ?: return 0
        return DOSES - family.indexOf(item)
    }

    private fun idFor(family: IntArray, doses: Int): Int = family[DOSES - doses]

    /** Every unordered item pair that must be bound: same family (any doses) and potion + empty vial. */
    fun bindingPairs(): List<Pair<Int, Int>> =
        families.flatMap { family ->
            val pairs = mutableListOf<Pair<Int, Int>>()
            for (i in family.indices) {
                for (j in i until family.size) pairs += family[i] to family[j]
                pairs += family[i] to Items.VIAL
            }
            pairs
        }

    /** [fromSlot] holds the item used, [toSlot] the item it was used on. */
    fun decant(player: Player, fromSlot: Int, toSlot: Int): Boolean {
        if (fromSlot == toSlot) return false
        val inventory = player.inventory
        val from = inventory[fromSlot] ?: return false
        val to = inventory[toSlot] ?: return false

        if (from.id == Items.VIAL || to.id == Items.VIAL) {
            val potionSlot = if (from.id == Items.VIAL) toSlot else fromSlot
            val vialSlot = if (from.id == Items.VIAL) fromSlot else toSlot
            val potion = inventory[potionSlot] ?: return false
            val family = familyOf[potion.id] ?: return false
            val doses = doses(potion.id)
            if (doses == 1) {
                inventory.swap(fromSlot, toSlot)
                player.message(POUR_MESSAGE)
                return true
            }
            val vialDoses = doses / 2
            inventory[potionSlot] = Item(idFor(family, doses - vialDoses))
            inventory[vialSlot] = Item(idFor(family, vialDoses))
            player.message(SPLIT_MESSAGE)
            return true
        }

        val family = familyOf[from.id] ?: return false
        if (familyOf[to.id] !== family) return false
        var toDoses = doses(to.id)
        if (toDoses == DOSES) {
            player.message("Nothing interesting happens.")
            return false
        }
        toDoses += doses(from.id)
        val remaining = if (toDoses > DOSES) toDoses - DOSES else 0
        toDoses -= remaining
        inventory[fromSlot] = Item(if (remaining > 0) idFor(family, remaining) else Items.VIAL)
        inventory[toSlot] = Item(idFor(family, toDoses))
        player.message(POUR_MESSAGE)
        return true
    }
}

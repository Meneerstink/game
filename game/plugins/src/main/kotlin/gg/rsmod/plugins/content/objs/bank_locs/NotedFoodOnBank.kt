package gg.rsmod.plugins.content.objs.bank_locs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.items.food.Food
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity

/**
 * Noted food used on a bank gives the food unnoted (DMM-ARMAGEDDON D6, owner 20-11-2026).
 *
 * OSRS Wiki Deadman: Armageddon: "Players will be able to use noted food on banks to withdraw unnoted food. There will be a short
 * delay before you can eat food that you've unnoted this way, similar to the delay present for eating after opening and closing
 * the bank interface." Wiki Deadman Mode: "Eating and drinking potions is blocked for 3 seconds (5 ticks) after banking or
 * un-noting outside of a safezone" - the same gate as closing the bank ([BankSecurity.onBankClosed]).
 *
 * Only food ([Food]); any other noted item on a bank does nothing, as before.
 */
object NotedFoodOnBank {
    private val BANK_NAMES = setOf("bank booth", "bank chest", "bank counter")

    /** A loc that works as a bank (same rule as the bank-object fallback in bank_booths.plugin.kts); deposit boxes are not banks. */
    fun isBank(def: ObjectDef): Boolean {
        val name = def.name.lowercase()
        return name in BANK_NAMES || (name == "counter" && def.options.any { it?.lowercase() == "use-quickly" })
    }

    private val FOODS: Set<Int> by lazy { Food.values.map { it.item }.toSet() }

    fun isFood(itemId: Int): Boolean = itemId in FOODS

    enum class Result { UNNOTED, NOT_NOTED_FOOD, NO_SPACE }

    /** Unnotes as much of the noted food [item] as fits in the inventory. */
    fun use(
        player: Player,
        item: Item,
    ): Result {
        val def = player.world.definitions.getNullable(ItemDef::class.java, item.id)
        val unnoted = def?.noteLinkId ?: 0
        if (def == null || !def.noted || unnoted <= 0 || !isFood(unnoted)) {
            player.message("Nothing interesting happens.")
            return Result.NOT_NOTED_FOOD
        }
        val have = player.inventory.getItemCount(item.id)
        val free = player.inventory.freeSlotCount
        // Using the whole note stack frees its own slot as well.
        val amount = if (have <= free + 1) have else free
        if (amount <= 0) {
            player.message("You don't have enough inventory space.")
            return Result.NO_SPACE
        }
        val removed = player.inventory.remove(item.id, amount, assureFullRemoval = true)
        if (!removed.hasSucceeded()) return Result.NO_SPACE
        val added = player.inventory.add(unnoted, amount, assureFullInsertion = true)
        if (!added.hasSucceeded()) {
            player.inventory.add(item.id, amount) // put the notes back
            player.message("You don't have enough inventory space.")
            return Result.NO_SPACE
        }
        BankSecurity.onBankClosed(player)
        player.message("The bank exchanges your banknotes for items.")
        return Result.UNNOTED
    }
}

package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.item.Item

/**
 * Identifies which container a [DeathSlotItem] came from, so death execution
 * knows where to remove it from.
 */
enum class DeathContainerSource {
    INVENTORY,
    EQUIPMENT,
}

/**
 * A single item stack at risk of loss on death, tagged with where it lives.
 */
data class DeathSlotItem(
    val source: DeathContainerSource,
    val slot: Int,
    val item: Item,
)

/**
 * The result of running [DeathItemRiskCalculator.calculate] against a
 * player's inventory and equipment at the moment of death: which item stacks
 * are protected (kept by the player) and which are lost.
 */
data class DeathItemRiskResult(
    val protectedItemCount: Int,
    val protected: List<DeathSlotItem>,
    val lost: List<DeathSlotItem>,
)

/**
 * Pure, side-effect-free calculation of which item stacks a player keeps and
 * loses on death. Performs no game-state mutation - see `DeathExecutor` for
 * the execution layer that acts on a [DeathItemRiskResult].
 */
object DeathItemRiskCalculator {
    /**
     * The number of single items a player keeps on death: [DeathRules.keepCount] (owner 2026-09-26 - Protect Item keeps
     * exactly 1, skulled or not; nothing otherwise). [itemProtectionActive] must reflect an *active* Protect Item
     * prayer/Ancient-Curse effect at the time of death, not merely an unlocked prayer level.
     */
    fun protectedItemCount(itemProtectionActive: Boolean): Int = DeathRules.keepCount(itemProtectionActive)
    /**
     * Calculates protected vs. lost items across [inventory] and [equipment] combined, keeping
     * the [protectedItemCount] most valuable *individual items* and losing the rest.
     *
     * Owner 2026-09-18 (MAJOR, "exactly RuneScape"): the kept count is a count of single items,
     * not of stacks. Ranking is by [valueProvider]'s per-unit value; a stack contributes one
     * unit per kept slot, so a player with Protect Item and 1,000 coins keeps 1 coin
     * and loses 999 (RuneScape Wiki "Items Kept on Death": "if you have a stack of items, only
     * up to three of that stack will be kept"; OSRS Wiki "Items Kept on Death" agrees). A
     * partially kept stack appears in both lists: the kept units in [DeathItemRiskResult.protected]
     * and the remainder, same slot, in [DeathItemRiskResult.lost] - `DeathExecutor` removes by
     * amount, never the whole slot, for exactly this case. The old model kept whole stacks
     * ranked by total value, which let a cash stack (or three food stacks) survive a death.
     *
     * Ties in value are broken by original slot order (inventory before
     * equipment, ascending slot index) so results are deterministic.
     *
     * @param alwaysProtected
     * Optional extension/policy hook for special-cased items (e.g.
     * untradeable quest items) that should always be kept regardless of
     * value or the protected-item count. Defaults to no special-casing -
     * every item, tradeable or not, is currently risk-evaluated purely by
     * value. No such special cases are implemented in this milestone; this
     * hook exists so a future milestone can add them without reworking this
     * calculator. Items matched by this hook count toward, and are capped
     * by, nothing - they are always kept in addition to the normal
     * value-ranked selection, mirroring how untradeables behave in-game.
     */
    fun calculate(
        inventory: Array<Item?>,
        equipment: Array<Item?>,
        itemProtectionActive: Boolean,
        valueProvider: ItemRiskValueProvider,
        alwaysProtected: (itemId: Int) -> Boolean = { false },
        alwaysLost: (itemId: Int) -> Boolean = { false },
    ): DeathItemRiskResult {
        val slots = mutableListOf<DeathSlotItem>()
        inventory.forEachIndexed { slot, item ->
            if (item != null) slots.add(DeathSlotItem(DeathContainerSource.INVENTORY, slot, item))
        }
        equipment.forEachIndexed { slot, item ->
            if (item != null) slots.add(DeathSlotItem(DeathContainerSource.EQUIPMENT, slot, item))
        }

        val forcedProtected = slots.filter { alwaysProtected(it.item.id) }
        // RCV-012 decision 3b: loot keys are always lost ("The effects of being unskulled and the Protect Item prayer apply only to
        // the items in the inventory, but not associated with the loot keys themselves").
        val forcedLost = slots.filter { !alwaysProtected(it.item.id) && alwaysLost(it.item.id) }
        val remaining = slots.filterNot { alwaysProtected(it.item.id) || alwaysLost(it.item.id) }

        val keepCount = protectedItemCount(itemProtectionActive)

        // Stable sort (Kotlin's sortedByDescending preserves relative order of
        // equal keys), so equally-valued stacks keep their original slot order.
        val ranked = remaining.sortedByDescending { valueProvider.getValue(it.item.id) }
        val kept = mutableListOf<DeathSlotItem>()
        val lostRemaining = mutableListOf<DeathSlotItem>()
        var budget = keepCount
        for (slotItem in ranked) {
            val keepAmount = minOf(budget, slotItem.item.amount)
            if (keepAmount <= 0) {
                lostRemaining.add(slotItem)
                continue
            }
            budget -= keepAmount
            if (keepAmount == slotItem.item.amount) {
                kept.add(slotItem)
            } else {
                kept.add(slotItem.copy(item = Item(slotItem.item, keepAmount)))
                lostRemaining.add(slotItem.copy(item = Item(slotItem.item, slotItem.item.amount - keepAmount)))
            }
        }

        return DeathItemRiskResult(
            protectedItemCount = forcedProtected.size + kept.size,
            protected = forcedProtected + kept,
            lost = lostRemaining + forcedLost,
        )
    }
}

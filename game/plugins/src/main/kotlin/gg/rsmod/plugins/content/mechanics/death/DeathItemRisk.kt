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
     * The number of item stacks a player is allowed to keep on death.
     *
     * Confirmed rules: unskulled players keep the 3 most valuable stacks
     * normally, or 4 with an active Protect Item effect; skulled players
     * keep 0 normally, or 1 with an active Protect Item effect.
     * [itemProtectionActive] must reflect an *active* Protect Item
     * prayer/Ancient-Curse effect at the time of death, not merely an
     * unlocked prayer level.
     */
    fun protectedItemCount(
        skulled: Boolean,
        itemProtectionActive: Boolean,
    ): Int {
        val base = if (skulled) 0 else 3
        return base + if (itemProtectionActive) 1 else 0
    }

    /**
     * Calculates protected vs. lost item stacks across [inventory] and
     * [equipment] combined, keeping the [protectedItemCount] highest-value
     * stacks and losing the rest.
     *
     * A stack's value is its *total* value - [valueProvider]'s per-unit
     * value multiplied by the stack's amount - matching this era's
     * established Protect Item behavior (e.g. a large stack of a cheap
     * stackable can outrank a single expensive item). This keeps or loses an
     * entire stack as one deterministic unit; stacks are never partially
     * split between the protected and lost lists.
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
        skulled: Boolean,
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

        val keepCount = protectedItemCount(skulled, itemProtectionActive)

        // Stable sort (Kotlin's sortedByDescending preserves relative order of
        // equal keys), so equally-valued stacks keep their original slot order.
        val ranked = remaining.sortedByDescending { valueProvider.getValue(it.item.id) * it.item.amount.toLong() }
        val kept = ranked.take(keepCount)
        val lostRemaining = ranked.drop(keepCount)

        return DeathItemRiskResult(
            protectedItemCount = forcedProtected.size + kept.size,
            protected = forcedProtected + kept,
            lost = lostRemaining + forcedLost,
        )
    }
}

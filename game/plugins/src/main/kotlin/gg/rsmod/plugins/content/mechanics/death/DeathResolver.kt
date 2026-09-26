package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.plugins.api.cfg.Items

/**
 * Where a death took place, for the purposes of item-risk resolution:
 * a Wilderness/PvP death (no gravestone - lost items become killer-owned
 * ground loot) or a PvM/safe death (lost items go into recovery/gravestone
 * state).
 *
 * Classification is cause-based: a player killer means PvP loot regardless of the victim's
 * coordinates, while an NPC/environmental death means recovery even in the Wilderness. The
 * legacy [WILDERNESS_PVP] enum name is retained for compatibility with existing breakable,
 * loot-key and logging consumers; its meaning is now "player-caused PvP loot".
 *
 * A player who is killed by an NPC immediately after being hit by another player is still a PvP
 * death while the shared aggressor window is active. This closes the delayed-hit/NPC-last-hit
 * boundary without inventing a new timer or a second combat attribution system.
 */
enum class DeathContext {
    WILDERNESS_PVP,
    PVM_SAFE,
}

/**
 * The complete, side-effect-free result of resolving a player's death: where
 * it happened, who (if anyone) is credited as the killer, and which item
 * stacks are protected vs. lost. Nothing about the player or world has been
 * mutated yet - see `DeathExecutor` for the execution layer that acts on
 * this result.
 */
data class DeathResolutionResult(
    val context: DeathContext,
    val victim: Player,
    val killer: Player?,
    val itemRisk: DeathItemRiskResult,
)

/**
 * Computes a [DeathResolutionResult] for a player's death without mutating
 * any game state. Calculation is intentionally kept separate from execution
 * (see `DeathExecutor`) so it can be reasoned about and tested in isolation,
 * and so a death's full outcome is always known before anything is removed
 * or granted.
 */
object DeathResolver {
    /**
     * Audit X-04: the player a death is credited to - the direct killer from the damage map, else the
     * recent PvP aggressor. A killer that is no longer online is never credited: loot handed to an
     * offline (already saved) player object is silently destroyed, and teammates could grief a kill
     * by hitting hard and logging out.
     */
    fun resolveKiller(victim: Player): Player? {
        val direct = victim.attr[KILLER_ATTR]?.get() as? Player
        val candidate =
            direct ?: if (victim.timers.has(PVP_AGGRESSOR_WINDOW_TIMER)) victim.attr[PVP_AGGRESSOR_ATTR]?.get() else null
        return candidate?.takeIf { it !== victim && it.isOnline }
    }

    fun resolveContext(victim: Player, killer: Player? = null): DeathContext {
        val recentAggressor =
            if (victim.timers.has(PVP_AGGRESSOR_WINDOW_TIMER)) {
                victim.attr[PVP_AGGRESSOR_ATTR]?.get()
            } else {
                null
            }
        return if (killer != null || recentAggressor != null) {
            DeathContext.WILDERNESS_PVP
        } else {
            DeathContext.PVM_SAFE
        }
    }

    /**
     * @param itemProtectionActive
     * Whether Protect Item is *active* right now (i.e. [PROTECT_ITEM_ATTR] is true), not merely unlocked. The skull no longer
     * changes what is kept (owner 2026-09-26, [DeathRules.keepCount]).
     * @param valueProvider the keep-1 ranking value - production callers pass [DeathRules.rankValue].
     */
    fun resolve(
        victim: Player,
        killer: Player?,
        itemProtectionActive: Boolean = victim.attr[PROTECT_ITEM_ATTR] == true,
        valueProvider: ItemRiskValueProvider,
        alwaysProtected: (itemId: Int) -> Boolean = { false },
        contextOverride: DeathContext? = null,
    ): DeathResolutionResult {
        val context = contextOverride ?: resolveContext(victim, killer)
        // Snapshot the container backing arrays before any mutation happens.
        // ItemContainer.rawItems is a live alias to the same array the
        // container mutates in place, not a defensive copy, so calculation
        // must copy it explicitly to stay fully decoupled from execution.
        val itemRisk =
            DeathItemRiskCalculator.calculate(
                inventory = victim.inventory.items.copyOf(),
                equipment = victim.equipment.items.copyOf(),
                itemProtectionActive = itemProtectionActive,
                valueProvider = valueProvider,
                alwaysProtected = alwaysProtected,
                // Loot keys, looting bags and Deadman emblems are never kept (RCV-012 3b, owner 2026-09-25/26).
                alwaysLost = DeathRules::alwaysLost,
            )
        return DeathResolutionResult(context, victim, killer, itemRisk)
    }
}
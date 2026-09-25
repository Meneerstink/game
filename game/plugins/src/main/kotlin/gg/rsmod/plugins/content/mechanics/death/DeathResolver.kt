package gg.rsmod.plugins.content.mechanics.death

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
     * @param skulled
     * Whether [victim] is currently skulled. Defaults to the running PK skull timer
     * ([gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled]) - the only skull state this
     * milestone wires trigger logic for; a full aggressor/timer skull system
     * (Bounty Hunter targeting, PK points) is explicitly out of scope and is
     * a follow-up milestone. Exposed as a parameter (rather than only ever
     * read internally) so callers/tests can inject it directly.
     *
     * @param itemProtectionActive
     * Whether Protect Item is *active* right now (i.e. [PROTECT_ITEM_ATTR]
     * is true), not merely unlocked. [gg.rsmod.plugins.content.mechanics.prayer.Prayers]
     * already keeps this attribute live-updated on activate/deactivate, so
     * it is reused directly rather than re-deriving prayer state here.
     */
    fun resolve(
        victim: Player,
        killer: Player?,
        skulled: Boolean = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(victim),
        itemProtectionActive: Boolean = victim.attr[PROTECT_ITEM_ATTR] == true,
        valueProvider: ItemRiskValueProvider,
        alwaysProtected: (itemId: Int) -> Boolean = { false },
    ): DeathResolutionResult {
        val context = resolveContext(victim, killer)
        // Snapshot the container backing arrays before any mutation happens.
        // ItemContainer.rawItems is a live alias to the same array the
        // container mutates in place, not a defensive copy, so calculation
        // must copy it explicitly to stay fully decoupled from execution.
        val itemRisk =
            DeathItemRiskCalculator.calculate(
                inventory = victim.inventory.items.copyOf(),
                equipment = victim.equipment.items.copyOf(),
                skulled = skulled,
                itemProtectionActive = itemProtectionActive,
                valueProvider = valueProvider,
                alwaysProtected = alwaysProtected,
                alwaysLost = { itemId ->
                    gg.rsmod.plugins.content.mechanics.pvp.LootKeys.isKey(itemId) ||
                        gg.rsmod.plugins.content.mechanics.pvp.LootingBag.isBag(itemId) ||
                        // Deadman emblems: always lost on a PvP death, Protect Item never applies (owner 2026-09-25).
                        // On a PvM death the caller's alwaysProtected keeps them, and alwaysProtected wins.
                        gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.isEmblem(itemId)
                },
            )
        return DeathResolutionResult(context, victim, killer, itemRisk)
    }
}

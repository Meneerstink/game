package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.hasSkullIcon

/**
 * Where a death took place, for the purposes of item-risk resolution:
 * a Wilderness/PvP death (no gravestone - lost items become killer-owned
 * ground loot) or a PvM/safe death (lost items go into recovery/gravestone
 * state).
 *
 * Classification is purely location-based - any death while standing in the
 * Wilderness is treated as [WILDERNESS_PVP], matching this codebase's
 * existing [gg.rsmod.plugins.content.combat.Combat] private `inPvpArea`
 * convention (`tile.getWildernessLevel() > 0`). This resolves the milestone's
 * "PvM/PvP/safe" framing as a two-way split, since a location-based check is
 * the only classification this codebase already establishes as a convention;
 * a future Practice PvP mode can override this per-player without changing
 * this resolver's shape (e.g. by short-circuiting to [PVM_SAFE] for players
 * flagged as being in that mode).
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
    fun resolveContext(victim: Player): DeathContext =
        if (BountyHunterHome.isDangerousWilderness(victim)) DeathContext.WILDERNESS_PVP else DeathContext.PVM_SAFE

    /**
     * @param skulled
     * Whether [victim] is currently skulled. Defaults to checking
     * [SkullIcon.RED] via [hasSkullIcon] - the only skull state this
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
        skulled: Boolean = victim.hasSkullIcon(SkullIcon.RED),
        itemProtectionActive: Boolean = victim.attr[PROTECT_ITEM_ATTR] == true,
        valueProvider: ItemRiskValueProvider,
        alwaysProtected: (itemId: Int) -> Boolean = { false },
    ): DeathResolutionResult {
        val context = resolveContext(victim)
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
            )
        return DeathResolutionResult(context, victim, killer, itemRisk)
    }
}

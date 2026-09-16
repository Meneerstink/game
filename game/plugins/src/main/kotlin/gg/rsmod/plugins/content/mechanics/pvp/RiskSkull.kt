package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator
import gg.rsmod.plugins.content.mechanics.death.ItemDefCostValueProvider
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider

/**
 * Risk-coloured skulls (master plan section 1C "Deadman-style onderdelen"): a five-tier icon showing
 * how much value a player currently has at risk of loss on death, distinct from the real PK
 * [SkullIcon.RED] skull ([PvpSkull]). Wires up [SkullIcon.DMM_VERY_HIGH_RISK]..
 * [SkullIcon.DMM_VERY_LOW_RISK] - real cache-verified Deadman Mode icon ids that already
 * existed in [SkullIcon] with zero usages anywhere in this codebase before this pass.
 *
 * "Risked value" reuses the same centralized death/risk engine
 * ([gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator]) death itself resolves
 * against - it is specifically the value of the item stacks that would actually be LOST right
 * now (i.e. excluded by Protect Item/skull-based protected-stack count), not raw total held
 * wealth, matching the real Deadman Mode concept this table is sourced from.
 *
 * Thresholds are the owner's explicit 2026-09-16 values (Deadman PvP guards plan), superseding
 * the master plan's earlier provisional table ("Exacte bedragen later balancen"):
 *
 * | Tier   | Risk                  |
 * |--------|-----------------------|
 * | Bronze | <= 200,000            |
 * | Iron   | 200,001 - 800,000     |
 * | Green  | 800,001 - 2,000,000   |
 * | Blue   | 2,000,001 - 8,000,000 |
 * | Red    | 8,000,001+            |
 *
 * A player with nothing at risk (e.g. an empty inventory/equipment) shows no risk skull at all
 * ([SkullIcon.NONE]) - not in the plan's table, but the only sane behaviour for the zero case.
 *
 * A real PK [SkullIcon.RED] skull always takes priority and is never overwritten here: a
 * player only has one skull-icon client slot ([Player.skullIcon]), matching how real Deadman
 * Mode shows RED instead of the risk-tier icon while genuinely PK-skulled.
 */
object RiskSkull {
    const val BRONZE_MAX = 200_000L
    const val IRON_MAX = 800_000L
    const val GREEN_MAX = 2_000_000L
    const val BLUE_MAX = 8_000_000L

    fun tierFor(riskedValue: Long): SkullIcon =
        when {
            riskedValue <= 0L -> SkullIcon.NONE
            riskedValue <= BRONZE_MAX -> SkullIcon.DMM_VERY_LOW_RISK
            riskedValue <= IRON_MAX -> SkullIcon.DMM_LOW_RISK
            riskedValue <= GREEN_MAX -> SkullIcon.DMM_MEDIUM_RISK
            riskedValue <= BLUE_MAX -> SkullIcon.DMM_HIGH_RISK
            else -> SkullIcon.DMM_VERY_HIGH_RISK
        }

    /** The total value of exactly the item stacks [player] would lose if they died right now -
     * the same [DeathItemRiskCalculator] the real death-resolution path uses, run non-
     * destructively against a snapshot of the live containers. [valueProvider] defaults to the
     * same cache-cost-backed provider death resolution uses, but is overridable (matching
     * [ItemRiskValueProvider]'s own "swappable, not hard-wired" design intent) so tests don't
     * need a loaded cache to exercise this. */
    fun calculateRiskedValue(
        player: Player,
        valueProvider: ItemRiskValueProvider = ItemDefCostValueProvider(player.world.definitions),
    ): Long {
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = player.inventory.items.copyOf(),
                equipment = player.equipment.items.copyOf(),
                skulled = player.hasSkullIcon(SkullIcon.RED),
                itemProtectionActive = player.attr[PROTECT_ITEM_ATTR] == true,
                valueProvider = valueProvider,
            )
        return result.lost.sumOf { valueProvider.getValue(it.item.id) * it.item.amount }
    }

    /** Refreshes [player]'s skull icon to match their current risk tier, unless they are
     * currently PK-skulled ([SkullIcon.RED] always wins - see class doc). A no-op when the
     * icon already matches, so it is safe to call every cycle without spamming appearance
     * updates. [valueProvider] is threaded through to [calculateRiskedValue] for the same
     * cache-free testability reason. */
    fun refresh(
        player: Player,
        valueProvider: ItemRiskValueProvider = ItemDefCostValueProvider(player.world.definitions),
    ) {
        if (player.hasSkullIcon(SkullIcon.RED)) {
            return
        }
        val tier = tierFor(calculateRiskedValue(player, valueProvider))
        if (!player.hasSkullIcon(tier)) {
            player.setSkullIcon(tier)
        }
    }
}

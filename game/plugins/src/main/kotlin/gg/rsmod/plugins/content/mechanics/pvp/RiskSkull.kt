package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon
import gg.rsmod.plugins.content.mechanics.death.DeathItemRiskCalculator
import gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider

/**
 * Risk-coloured skulls (master plan section 1C "Deadman-style onderdelen"; owner 2026-09-17: "the
 * skull above the head colour needs to be updating with the skull in the timerskull hud, so when
 * the risk changes of a player it needs to recalculate and change colors depending on risk").
 *
 * The head icon is DERIVED state, recomputed every cycle from two facts:
 * - whether the player is PK-skulled ([PvpSkull.isSkulled] - the running skull timer), and
 * - how many loot keys they carry ([LootKeys]).
 *
 * A skulled player always shows a skull, coloured by the value currently at risk (bronze when
 * nothing is at risk, so the skull never vanishes while the timer runs). An unskulled player shows
 * no skull at all (owner 2026-09-17: "als een player unskulled is geeft die nu een witte skull aan,
 * dit mag niet") unless they carry loot keys, in which case the risk-coloured skull is the carrier
 * of the key count (owner example: "1 key above his head and a brown skull"). The five tiers are
 * the cache-verified Deadman icon ids [SkullIcon.DMM_VERY_HIGH_RISK]..[SkullIcon.DMM_VERY_LOW_RISK];
 * the plain red [SkullIcon.RED] frame is never used, so the client's skull-timer HUD (which draws
 * the local player's own head icon) shows exactly the same colour as the icon above the head.
 *
 * "Risked value" reuses the same centralized death/risk engine ([DeathItemRiskCalculator]) death
 * itself resolves against - it is specifically the value of the item stacks that would actually be
 * LOST right now (i.e. excluded by Protect Item/skull-based protected-stack count), not raw total
 * held wealth, matching the real Deadman Mode concept this table is sourced from.
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
        valueProvider: ItemRiskValueProvider = GuidePriceValueProvider(player.world),
    ): Long {
        val result =
            DeathItemRiskCalculator.calculate(
                inventory = player.inventory.items.copyOf(),
                equipment = player.equipment.items.copyOf(),
                skulled = PvpSkull.isSkulled(player),
                itemProtectionActive = player.attr[PROTECT_ITEM_ATTR] == true,
                valueProvider = valueProvider,
            )
        // Owner 2026-09-18 (#5): the risk must follow a kill at once (blue -> red). Loot keys are
        // always lost on death (RCV-012 3b), so the loot INSIDE the carried keys is at risk too -
        // OSRS Deadman: the skull colour reflects the value risked including the held keys' loot.
        val keyLoot =
            LootKeys.heldKeyIndexes(player).sumOf { index ->
                LootKeys.slotItems(player, index).sumOf { valueProvider.getValue(it.id) * it.amount }
            }
        return result.lost.sumOf { valueProvider.getValue(it.item.id) * it.item.amount } + keyLoot
    }

    /** Loot keys carried in the inventory (0-5); OSRS Deadman: "The number of keys on the icon
     * reflects the number of keys a player has in their inventory." */
    fun heldKeys(player: Player): Int = LootKeys.heldKeyIndexes(player).size.coerceIn(0, LootKeys.MAX_KEYS)

    /** The icon [player] should show right now (see the class doc). */
    fun iconFor(
        player: Player,
        valueProvider: ItemRiskValueProvider = GuidePriceValueProvider(player.world),
    ): SkullIcon {
        val skulled = PvpSkull.isSkulled(player)
        val keys = heldKeys(player)
        if (!skulled && keys == 0) return SkullIcon.NONE
        val tier = tierFor(calculateRiskedValue(player, valueProvider))
        return if (tier == SkullIcon.NONE) SkullIcon.DMM_VERY_LOW_RISK else tier
    }

    /**
     * Refreshes [player]'s head icon and loot-key count from the current skull state and risk. A
     * no-op when nothing changed, so it is safe to call every cycle. [valueProvider] is threaded
     * through to [calculateRiskedValue] for cache-free testability.
     */
    fun refresh(
        player: Player,
        valueProvider: ItemRiskValueProvider = GuidePriceValueProvider(player.world),
    ) {
        val keys = heldKeys(player)
        var changed = false
        if (player.lootKeyIcons != keys) {
            player.lootKeyIcons = keys
            changed = true
        }
        val icon = iconFor(player, valueProvider)
        if (!player.hasSkullIcon(icon)) {
            player.setSkullIcon(icon)
            changed = false // setSkullIcon already queued the appearance update
        }
        if (changed) {
            player.addBlock(UpdateBlockType.APPEARANCE)
        }
    }
}

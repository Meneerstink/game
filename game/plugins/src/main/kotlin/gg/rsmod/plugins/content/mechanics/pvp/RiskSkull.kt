package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.setSkullIcon
import gg.rsmod.plugins.content.mechanics.death.DeathRules
import gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider

/**
 * Risk-coloured skulls, exactly like OSRS Deadman: Annihilation (owner 2026-09-26):
 *  - every player always shows a skull in the colour of the value they would lose right now ([calculateRiskedValue] -
 *    the very rules of the real death, [DeathRules.pvpLoss]);
 *  - the colour is only recalculated in a dangerous area and stays frozen in safe zones ([FROZEN_TIER], anti-scouting);
 *  - a really skulled player (or a loot key carrier) shows the yellow-eyed variant of the tier ([SkullIcon.skulled]).
 * The client's skull-timer HUD draws the local player's own head icon, so it always shows the same skull.
 *
 * Thresholds are the owner's explicit 2026-09-16 values:
 *
 * | Tier   | Risk                  |
 * |--------|-----------------------|
 * | Bronze | <= 200,000            |
 * | Iron   | 200,001 - 800,000     |
 * | Green  | 800,001 - 2,000,000   |
 * | Blue   | 2,000,001 - 8,000,000 |
 * | Red    | 8,000,001+            |
 */object RiskSkull {
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

    /**
     * The value [player] would give away if a player killed them right now: [DeathRules.pvpLoss] - the very rules the real
     * death runs (keep 1 with Protect Item, conversions, untradeable repair coins at the level-20 rule of the player's
     * current tile, looting bag contents and the loot behind carried keys), valued like the keep-1 ranking
     * ([DeathRules.rankValue]: guide price, coins at face value).
     */
    fun calculateRiskedValue(
        player: Player,
        valueProvider: ItemRiskValueProvider = DeathRules.rankValue(player.world),
    ): Long =
        DeathRules.pvpLoss(
            player = player,
            itemProtectionActive = player.attr[PROTECT_ITEM_ATTR] == true,
            deepWilderness = DeathRules.deepWilderness(player.tile),
            value = valueProvider,
        ).sumOf { valueProvider.getValue(it.id) * it.amount }
    /** Loot keys carried in the inventory (0-5); OSRS Deadman: "The number of keys on the icon
     * reflects the number of keys a player has in their inventory." */
    fun heldKeys(player: Player): Int = LootKeys.heldKeyIndexes(player).size.coerceIn(0, LootKeys.MAX_KEYS)

    /**
     * The tier colour, last computed in a dangerous area (persisted). Owner 2026-09-26, OSRS Deadman anti-scouting: the colour
     * is only recalculated while the player stands in a dangerous area and stays frozen in safe zones, so it never leaks the
     * live value after a login or a teleport into a city.
     */
    val FROZEN_TIER = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "risk_skull_tier")

    /** The dark-eyed tier [player] shows right now: live in a dangerous area, frozen in a safe one (bronze if never seen). */
    fun tierNow(
        player: Player,
        valueProvider: ItemRiskValueProvider = DeathRules.rankValue(player.world),
    ): SkullIcon {
        if (AreaState.isDangerous(player.zoneTile())) {
            val live = tierFor(calculateRiskedValue(player, valueProvider)).let { if (it == SkullIcon.NONE) SkullIcon.DMM_VERY_LOW_RISK else it }
            player.attr[FROZEN_TIER] = live.id
            return live
        }
        return player.attr[FROZEN_TIER]?.let { SkullIcon.forId(it) }?.takeIf { it.id in 8..12 } ?: SkullIcon.DMM_VERY_LOW_RISK
    }

    /**
     * The icon [player] should show right now. Owner 2026-09-26 (OSRS Deadman: Annihilation): every player always shows a
     * risk-coloured skull; a real skull ([PvpSkull.isSkulled] - attacked first, Emblem Trader) or carried loot keys show its
     * yellow-eyed variant. Only the icon changes here: every "is skulled" decision keeps reading [PvpSkull.isSkulled].
     */
    fun iconFor(
        player: Player,
        valueProvider: ItemRiskValueProvider = DeathRules.rankValue(player.world),
    ): SkullIcon {
        val tier = tierNow(player, valueProvider)
        return if (PvpSkull.isSkulled(player) || heldKeys(player) > 0) tier.skulled() else tier
    }
    /**
     * Refreshes [player]'s head icon and loot-key count from the current skull state and risk. A
     * no-op when nothing changed, so it is safe to call every cycle. [valueProvider] is threaded
     * through to [calculateRiskedValue] for cache-free testability.
     */
    fun refresh(
        player: Player,
        valueProvider: ItemRiskValueProvider = DeathRules.rankValue(player.world),
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

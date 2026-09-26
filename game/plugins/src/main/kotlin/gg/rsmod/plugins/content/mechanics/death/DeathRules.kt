package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem

/**
 * The death rules shared by the real death (`death.plugin.kts`), the Items Kept on Death preview ([ItemsKeptOnDeath]) and
 * the risk skull ([gg.rsmod.plugins.content.mechanics.pvp.RiskSkull]), so none of them can predict something the death does
 * not do. Owner decisions 2026-09-26:
 *
 *  - PvP and PvM: everything is lost; Protect Item (the prayer or the Ancient Curses slot) keeps exactly [PROTECT_ITEM_KEEP]
 *    item, skulled or not. There are no always-kept items.
 *  - The kept item is the most valuable one: tradeables by Grand Exchange guide price, untradeables by their repair price
 *    ([RepairPrices], [rankValue]).
 *  - Loot keys, looting bags and Deadman emblems are never kept ([alwaysLost]).
 */
object DeathRules {
    /** The one keep count: Protect Item keeps this many items. */
    const val PROTECT_ITEM_KEEP = 1

    fun keepCount(itemProtectionActive: Boolean): Int = if (itemProtectionActive) PROTECT_ITEM_KEEP else 0

    /**
     * OSRS: an untradeable breaks "if the death occurred at level 20 Wilderness and below" and is destroyed / mangled "above
     * level 20" (OSRS Wiki "Trouver parchment"). Deadman dangerous areas outside the Wilderness are level 0.
     */
    const val DEEP_WILDERNESS_ABOVE = 20

    fun deepWilderness(level: Int): Boolean = level > DEEP_WILDERNESS_ABOVE

    fun deepWilderness(tile: Tile): Boolean = deepWilderness(tile.getWildernessLevel())

    fun alwaysLost(itemId: Int): Boolean = LootKeys.isKey(itemId) || LootingBag.isBag(itemId) || DeadmanEmblem.isEmblem(itemId)

    /** Guide price for tradeables, repair price for untradeables - the keep-1 ranking and the risk value. */
    fun rankValue(world: World): ItemRiskValueProvider = rankValue(world.definitions, GuidePriceValueProvider(world))

    fun rankValue(
        definitions: DefinitionSet,
        guide: ItemRiskValueProvider,
    ): ItemRiskValueProvider =
        ItemRiskValueProvider { itemId ->
            val def = RepairPrices.itemDef(definitions, itemId)
            if (def != null && !def.tradeable && UntradeableDeathProtection.valuedAtRepairPrice(definitions, itemId)) {
                RepairPrices.repairPrice(definitions, itemId)
            } else {
                guide.getValue(itemId)
            }
        }

    /**
     * Everything an unprotected PvP death at [deepWilderness] would hand the killer, as items: the plain lost stacks, the
     * converted items, the untradeables' coins and runes, a looting bag's contents and the loot behind carried loot keys.
     * Used by the risk skull so its colour is the value the real death gives away.
     */
    fun pvpLoss(
        player: Player,
        itemProtectionActive: Boolean,
        deepWilderness: Boolean,
        value: ItemRiskValueProvider,
    ): List<Item> {
        val definitions = player.world.definitions
        val resolved =
            DeathResolver.resolve(
                victim = player,
                killer = null,
                itemProtectionActive = itemProtectionActive,
                valueProvider = value,
                contextOverride = DeathContext.WILDERNESS_PVP,
            )
        val (stripped, lostAmmo) = QuiverDeathRules.stripLost(resolved)
        val (afterConversions, converting) = PvpDeathBreakables.split(stripped)
        val (result, untradeables) = UntradeableDeathProtection.splitPvp(definitions, afterConversions, deepWilderness)
        val loss = mutableListOf<Item>()
        result.itemRisk.lost.forEach { slot ->
            val id = slot.item.id
            when {
                LootKeys.isKey(id) -> LootKeys.slotItems(player, LootKeys.keyIndex(id)).forEach { loss += it }
                LootingBag.isBag(id) || DeadmanEmblem.isEmblem(id) -> {}
                else -> loss += slot.item
            }
        }
        loss += converting.map { it.item }
        loss += lostAmmo
        loss += UntradeableDeathProtection.killerLoot(untradeables)
        loss += LootingBag.peekContents(player).filterNot { LootingBag.destroyedOnPvpDeath(player, it) }
        return loss
    }
}

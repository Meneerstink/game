package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.AvernicTreads
import gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys

/**
 * OSRS Wiki "Items Kept on Death" (mechanics table, raw wikitext, fetched 2026-09-16) - the general,
 * unnamed-item default for a "non-locked" untradeable item on an unprotected death: "The victim keeps
 * it in their inventory in broken form" (below level 20 Wilderness; above 20 it instead "turns into a
 * tiny pile of coins ... dropped to the PKer" - unsourced amount, and per this project's owner-approved
 * "below-20 rule applies everywhere" convention (`OSRS_IMPORT_MASTER.yml`, 2026-09-13), the broken-kept
 * branch is the one that matters here).
 *
 * Every *named* untradeable item this codebase already special-cases (`PvpDeathBreakables`,
 * `OsrsOrnamentKits`, the Toxic blowpipe/Bow of Faerdhinen/etc. conversions in `PvpDeathBreakables`,
 * `PoweredStaves`) already implements exactly this rule with each item's own sourced broken id and
 * repair cost. This object is the FALLBACK for every other untradeable, non-loot-key item that has no
 * such entry: a real gap this codebase had (see `OSRS_IMPORT_MASTER.yml` "Continued pass" - such items
 * were being spawned as-is, fully working, for the killer to loot, which is neither branch of the
 * sourced rule and is a genuine potential exploit).
 *
 * No broken-item cache id exists for the vast majority of these items (importing one per item is its
 * own future, bounded cache-import task), so guessing one is not an option. Instead this reuses
 * [DeathResolver.resolve]'s existing `alwaysProtected` extension point - the exact "keep the item whole
 * instead of guessing a degradation" fallback `Trouver`'s own class doc already establishes as this
 * codebase's honest answer to the same "no broken variant yet" situation. It is a deliberately narrower
 * outcome than the sourced rule (kept working rather than kept broken-and-unusable), recorded as such,
 * not silently presented as the full mechanic.
 */
object UntradeableDeathProtection {
    /** Whether [itemId] already has its own specific PvP-death rule elsewhere in this codebase. */
    private fun hasSpecificRule(itemId: Int): Boolean =
        PvpDeathBreakables.breakableFor(itemId) != null ||
            OsrsOrnamentKits.forPvpConversion(itemId) != null ||
            itemId == Items.TOXIC_BLOWPIPE || itemId == Items.BLAZING_BLOWPIPE ||
            itemId == Items.BOW_OF_FAERDHINEN || itemId == Items.AMULET_OF_BLOOD_FURY ||
            itemId == Items.TOXIC_STAFF_OF_THE_DEAD || itemId == Items.ANCIENT_SCEPTRE ||
            itemId in AvernicTreads.UPGRADED ||
            PoweredStaves.chargedTierOf(itemId) != null

    /**
     * True when [itemId] should be force-kept with the victim: untradeable, not a loot key (those are
     * always lost regardless - RCV-012 decision 3b, unrelated to tradeability and never overridden
     * here), and not already covered by one of this codebase's item-specific death rules.
     */
    fun shouldProtect(
        definitions: DefinitionSet,
        itemId: Int,
    ): Boolean {
        if (LootKeys.isKey(itemId) || hasSpecificRule(itemId)) return false
        return !definitions.get(ItemDef::class.java, itemId).tradeable
    }

    /**
     * Audit D-15: on a PvP death a generic untradeable is no longer force-kept whole. It takes part in
     * the normal keep-3 ranking; when it ends up lost it is taken out of the killer-bound list here and
     * [breakInPlace] keeps it with the victim "in broken form" (OSRS "Items Kept on Death"), unusable
     * until Perdu repairs it ([BrokenItemRepair]). Nothing reaches the killer for it (below-20 rule).
     */
    fun splitGeneric(
        definitions: DefinitionSet,
        result: DeathResolutionResult,
    ): Pair<DeathResolutionResult, List<DeathSlotItem>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val (generic, rest) = result.itemRisk.lost.partition { shouldProtect(definitions, it.item.id) }
        if (generic.isEmpty()) return result to emptyList()
        return result.copy(itemRisk = result.itemRisk.copy(lost = rest)) to generic
    }

    /**
     * Marks each stack in [broken] as [ItemAttribute.BROKEN]. A worn item is taken off first (to the
     * inventory, else the bank, else a private ground item) so its bonuses no longer apply.
     */
    fun breakInPlace(
        victim: Player,
        broken: List<DeathSlotItem>,
    ) {
        var unequipped = false
        for (slotItem in broken) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            val current = container[slotItem.slot] ?: continue
            if (current.id != slotItem.item.id) continue
            val damaged = Item(current).also { it.attr[ItemAttribute.BROKEN] = 1 }
            if (slotItem.source == DeathContainerSource.INVENTORY) {
                container[slotItem.slot] = damaged
                continue
            }
            container[slotItem.slot] = null
            unequipped = true
            val placed =
                victim.inventory.addPreservingAttr(damaged, assureFullInsertion = true).hasSucceeded() ||
                    victim.bank.addPreservingAttr(damaged, assureFullInsertion = true).hasSucceeded()
            if (!placed) {
                victim.world.spawn(GroundItem(damaged, victim.tile, victim))
            }
        }
        if (unequipped) victim.refreshBonuses()
        if (broken.isNotEmpty()) {
            victim.message("Some of your untradeable items were broken. Perdu at the Grand Exchange can repair them.")
        }
    }

    fun isBroken(item: Item): Boolean = (item.attr[ItemAttribute.BROKEN] ?: 0) > 0

    /**
     * Repair cost for an attribute-broken untradeable. OSRS prices vary per item and are not sourced
     * here (niet geverifieerd): the item's store value, with a floor so repairs are never free.
     */
    fun repairCost(
        definitions: DefinitionSet,
        itemId: Int,
    ): Long = maxOf(MIN_REPAIR_COST, definitions.get(ItemDef::class.java, itemId).cost.toLong())

    const val MIN_REPAIR_COST = 1_000L
}

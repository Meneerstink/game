package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
}

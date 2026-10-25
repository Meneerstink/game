package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/**
 * K01 deel 5 (N327 N369 N371, owner 2026-10-25 V2/V11/V12): "ALLE ornament kits in het spel onruilbaar (niet GE, niet ruilen), uit
 * alle 3 winkels"; "het item MET kit erop ook onruilbaar; uit elkaar halen geeft de (onruilbare) kit + het gewone item"; "elke
 * ornament kit staat in een van de 3 winkels en komt NERGENS anders vandaan". The tradeable flag itself is items.yml data
 * (`KitTradeabilityTests` fails on any kit or kitted item marked tradeable); this object names the set once for the death rules,
 * the store text and the tests.
 *
 * A kit is every ornament, colour, upgrade kit, paint and mix the stores sell ([StoreCatalogue.Kind.KIT]) and every kit of the
 * ornament tables ([OsrsOrnamentKits]). Not kits: the Nightmare staff orbs (boss drops, the tradeable orb is the staff's other half)
 * and the Zalcano shard (a skilling drop) - they keep their own tradeability.
 *
 * Death (HOOFDLIJST K01 point 4, DOOD-OVERZICHT): a kit is never lost. On an unprotected PvP death a carried kit goes to the
 * victim's gravestone, and a kitted item gives the killer the plain item while its kit goes to the victim's gravestone (the OSRS
 * Bounty Hunter kit rule, now for every kit). Consumed kits (paints, mixes, upgrade kits) are part of the item and are not returned.
 */
object OrnamentKitRules {
    /** Attachments that are not kits ([OrnamentKitRules] class doc). */
    val NOT_KITS: Set<Int> = setOf(Items.HARMONISED_ORB, Items.VOLATILE_ORB, Items.ELDRITCH_ORB, Items.ZALCANO_SHARD)

    /** Every ornament kit in the game. */
    val KITS: Set<Int> by lazy {
        (
            StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.KIT }.map { it.purchaseItem } +
                OsrsOrnamentKits.ALL.map { it.kit } + OsrsOrnamentKits.CONSUMED.map { it.kit }
        ).toSet() - NOT_KITS
    }

    /** Every item with a kit on it. */
    val KITTED: Set<Int> by lazy {
        (
            StoreCatalogue.ENTRIES.filter { it.kind == StoreCatalogue.Kind.KIT && it.purchaseItem in KITS }.flatMap { it.resultItems } +
                OsrsOrnamentKits.ALL.filter { it.kit in KITS }.map { it.ornamented } +
                OsrsOrnamentKits.CONSUMED.filter { it.kit in KITS }.map { it.ornamented }
        ).toSet()
    }

    fun isKit(itemId: Int): Boolean = itemId in KITS

    /** The returned-kit ornaments whose kit goes to the victim's gravestone on a PvP death (the plain item to the killer). */
    val KIT_TO_GRAVE: Set<Int> by lazy { OsrsOrnamentKits.ALL.filter { it.kit in KITS }.map { it.ornamented }.toSet() }
}

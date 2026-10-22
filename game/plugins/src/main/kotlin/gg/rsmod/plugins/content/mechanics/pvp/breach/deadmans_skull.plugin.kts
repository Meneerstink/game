package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StoreUi

/**
 * OSRS Wiki "Deadman's skull" (item 33065, imported as [Items.DEADMANS_SKULL]): options Shop, Unlocks, Breach Check, Destroy.
 * Owner 2026-10-22 (N172/N175): the OSRS Swap option (prayer book / spellbook) is removed - from the item definition too
 * (cache: ItemDefEditTool --remove=37), so the menu no longer shows it.
 *
 * - Breach Check (owner 2026-09-23: "The Breach Check right-click option displays the time to the next breach"): the open breach's
 *   location, else the time until the next one ([DeadmanBreach.statusLine]).
 * - Shop / Unlocks: the OSRS skull shop sells sigils and quest lamps; this server's Deadman rewards live in the Deadman Store
 *   (sigils are not part of this server), so both open it. ADAPTED.
 * - Destroy: the item's own destroy message; another skull is handed out by the Deadman Store npc (the OSRS Emblem Trader).
 */

on_item_option(item = Items.DEADMANS_SKULL, option = "breach check") {
    player.message(DeadmanBreach.statusLine())
}

on_item_option(item = Items.DEADMANS_SKULL, option = "shop") {
    StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
}

on_item_option(item = Items.DEADMANS_SKULL, option = "unlocks") {
    StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
}

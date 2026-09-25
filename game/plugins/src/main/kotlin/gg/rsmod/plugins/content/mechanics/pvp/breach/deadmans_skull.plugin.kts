package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.entity.zoneTile

import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StoreUi

/**
 * OSRS Wiki "Deadman's skull" (item 33065, imported as [Items.DEADMANS_SKULL]): options Shop, Unlocks, Swap, Breach Check, Destroy.
 *
 * - Breach Check (owner 2026-09-23: "The Breach Check right-click option displays the time to the next breach"): the open breach's
 *   location, else the time until the next one ([DeadmanBreach.statusLine]).
 * - Swap: "swapping between different spellbooks and prayer books ... Swapping prayers can be done anywhere and while in combat.
 *   However, swapping spellbooks can only be done within a safezone and out of combat." The prayer books here are the normal book
 *   and the Ancient Curses (the owner's kept extra), each with its existing unlock rule.
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

on_item_option(item = Items.DEADMANS_SKULL, option = "swap") {
    player.queue {
        when (options("Prayers", "Spellbook", title = "What would you like to swap?")) {
            FIRST_OPTION -> {
                val curses = AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT
                AncientCurses.switchBook(player, if (curses) AncientCurses.PrayerBook.NORMAL else AncientCurses.PrayerBook.ANCIENT)
            }
            SECOND_OPTION -> {
                if (!GuardedZones.contains(player.zoneTile()) || player.timers.has(ACTIVE_COMBAT_TIMER)) {
                    player.message("You can only swap your spellbook in a safe zone while out of combat.")
                    return@queue
                }
                when (options("Standard", "Ancient Magicks", "Lunar", title = "Choose a spellbook")) {
                    FIRST_OPTION -> Spellbooks.select(player, Spellbook.STANDARD)
                    SECOND_OPTION -> Spellbooks.select(player, Spellbook.ANCIENT)
                    THIRD_OPTION -> Spellbooks.select(player, Spellbook.LUNAR)
                }
            }
        }
    }
}

package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound

/**
 * N190 (owner 2026-10-22): "it needs to be a rune pouch note for the same price as osrs, then u use it on a banker or grand exchange
 * clerk to get a runepouch". OSRS Wiki "Rune pouch note" (item 24587, imported as [Items.RUNE_POUCH_NOTE]): a tradeable note that a
 * banker exchanges for one rune pouch.
 */
object RunePouchNote {
    const val EXCHANGE_SOUND = gg.rsmod.plugins.api.cfg.Sfx.BANK_STAMP

    const val READ = "This note can be exchanged for a rune pouch by any banker or Grand Exchange clerk."

    /** One note becomes one rune pouch; false (and nothing changes) without a note or without a free slot for the pouch. */
    fun exchange(player: Player): Boolean {
        val inventory = player.inventory
        if (!inventory.contains(Items.RUNE_POUCH_NOTE)) return false
        // The last note of a stack frees its own slot for the pouch.
        if (inventory.freeSlotCount == 0 && inventory.getItemCount(Items.RUNE_POUCH_NOTE) > 1) {
            player.message("You need a free inventory space for the rune pouch.")
            return false
        }
        if (!inventory.remove(Items.RUNE_POUCH_NOTE, 1).hasSucceeded()) return false
        inventory.add(Items.RUNE_POUCH, 1)
        // N218 (owner 2026-10-23: "exchanging rune pouch note maakt nu geen geluid"): the bank's own stamp (Jagex bank_stamp) on
        // the exchange; the line is OSRS's (Transcript:Rune pouch note).
        player.playSound(EXCHANGE_SOUND)
        player.message("You exchange the promissory note.")
        return true
    }
}

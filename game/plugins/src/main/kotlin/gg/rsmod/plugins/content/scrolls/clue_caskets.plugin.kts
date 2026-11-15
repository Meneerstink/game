package gg.rsmod.plugins.content.scrolls

import gg.rsmod.plugins.content.areas.watson.MimicCaskets
import gg.rsmod.plugins.content.areas.watson.StrangeCasket

/** All five clue caskets use one reward transaction. Item 405 is the unrelated fishing casket. */
ClueScrollTier.values().filter { it.casketId >= 0 }.forEach { tier ->
    on_item_option(tier.casketId, "open") {
        val source = player.getInteractingItem()
        val slot = player.getInteractingItemSlot()
        player.queue {
            if (!ClueCasketLocation.permitted(player, tier)) {
                player.message("You cannot open this on Entrana, the monks are watching you too closely.")
                return@queue
            }
            if (MimicCaskets.supported(tier)) {
                if (player.attr[MimicCaskets.ASKED] != true && player.attr[StrangeCasket.MIMIC_CHALLENGES] != true) {
                    messageBox(StrangeCasket.OPEN_BEFORE_FIGHT)
                    val wantsChallenge = options("Yes, I'd like to get Mimic challenges.", "No, I don't think I want that.") == 1
                    player.attr[MimicCaskets.ASKED] = true
                    // Wiki Mimic: the first opening directs to the Strange casket to opt in.
                    // Only its sourced dialogue enables challenges; this prompt must not do so.
                    if (wantsChallenge) {
                        messageBox(StrangeCasket.OPEN_BEFORE_FIGHT)
                        return@queue
                    }
                }
                if (MimicCaskets.held(player)) {
                    if (options("Yes", "No", title = "You already have a Mimic, and cannot be given another one. Do you wish to open the casket anyway?") != 1) return@queue
                } else if (MimicCaskets.selected(player, tier)) {
                    if (MimicCaskets.convert(player, slot, source, tier)) player.message("The chest turned out to be the Mimic!")
                    else player.message("You don't have enough inventory space to hold that item.")
                    return@queue
                }
            }
            if (player.inventory[slot] !== source) return@queue
            val plan = ClueCasketRewards.planForPlayer(player, tier) ?: run {
                player.message("This reward casket is temporarily unavailable.")
                return@queue
            }
            val award = ClueCasketRewards.award(player, slot, source, plan) ?: return@queue
            val text = award.rewards.joinToString(", ") { "${it.amount} x ${it.getName(player.world.definitions)}" }
            itemMessageBox("You open the casket. Inside you find $text.", item = award.rewards.first().id)
        }
    }
}

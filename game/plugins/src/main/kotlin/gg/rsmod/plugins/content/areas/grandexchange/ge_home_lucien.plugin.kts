package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/** Home-service Lucien uses cache variant 273: same identity and BAS, Talk-to retained, no Attack option. */
on_npc_option(npc = Npcs.LUCIEN_273, option = "talk-to") {
    player.queue {
        chatNpc("I can record your While Guthix Sleeps victory and unlock the Tormented demons and demonbane weapons.")
        when (options("Complete While Guthix Sleeps.", "Goodbye.")) {
            1 -> {
                chatPlayer("Please complete While Guthix Sleeps for me.")
                UnlockNpcRewards.completeWhileGuthixSleeps(player)
            }
        }
    }
}

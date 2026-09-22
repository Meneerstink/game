package gg.rsmod.plugins.content.areas.gnomestronghold

import gg.rsmod.plugins.content.quests.finishedQuest
import gg.rsmod.plugins.content.quests.impl.RuneMysteries

/**
 * Brimstail's "Teleport" (2026-09-22 npc census: advertised in the cache, never handled). RS Wiki "Brimstail": he "is able
 * to provide teleports to the Rune Essence mine". The Rune Mysteries gate is the same one Aubury and Sedridor apply to
 * every essence teleport.
 */
on_npc_option(npc = Npcs.BRIMSTAIL, option = "teleport") {
    if (!player.finishedQuest(RuneMysteries)) {
        player.message("You must've completed Rune Mysteries to teleport to the essence mines.")
        return@on_npc_option
    }
    essenceTeleport(player, targetTile = Tile(2911, 4832))
}

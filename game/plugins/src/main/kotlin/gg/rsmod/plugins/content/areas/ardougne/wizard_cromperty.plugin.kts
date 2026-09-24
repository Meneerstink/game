package gg.rsmod.plugins.content.areas.ardougne

import gg.rsmod.plugins.content.quests.finishedQuest
import gg.rsmod.plugins.content.quests.impl.RuneMysteries

/*
 * Wizard Cromperty (East Ardougne, 2328 via transform npc 844) offers "Teleport" to the rune essence mine like Aubury, Sedridor,
 * Brimstail and Wizard Distentor; the option was not bound. Same rule and route as those (essenceTeleport, Rune Mysteries).
 */
on_npc_option(npc = Npcs.WIZARD_CROMPERTY, option = "teleport") {
    if (!player.finishedQuest(RuneMysteries)) {
        player.message("You must've completed Rune Mysteries to teleport to the essence mines.")
        return@on_npc_option
    }
    essenceTeleport(player, targetTile = Tile(2920, 4821))
}

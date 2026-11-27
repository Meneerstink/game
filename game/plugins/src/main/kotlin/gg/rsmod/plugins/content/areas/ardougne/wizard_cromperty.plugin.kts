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

// DIARIES-OSRS Ardougne rewards (OSRS Wiki "Ardougne Diary"): "Wizard Cromperty offers you 100 / 150 / 250 noted pure essence for free
// every day" after the medium / hard / elite claim. Bound on Talk-to only where the cache has that option (a missing one is a boot error).
if (world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, Npcs.WIZARD_CROMPERTY).options.any { it.equals("Talk-to", ignoreCase = true) }) {
    on_npc_option(npc = Npcs.WIZARD_CROMPERTY, option = "talk-to") {
        player.queue {
            val amount = gg.rsmod.plugins.content.diaries.DiaryItems.crompertyEssence(player)
            if (amount == 0) {
                chatNpc("Hello there! I'm rather busy with my experiments at the moment.")
                return@queue
            }
            if (gg.rsmod.plugins.content.diaries.DiaryItems.crompertyClaimedToday(player)) {
                chatNpc("I've already given you your essence for today. Come back tomorrow!")
                return@queue
            }
            val noted = Items.PURE_ESSENCE + 1
            if (player.inventory.freeSlotCount == 0 && !player.inventory.contains(noted)) {
                chatNpc("I have some pure essence for you, but you don't have room for it.")
                return@queue
            }
            gg.rsmod.plugins.content.diaries.DiaryItems.markCrompertyToday(player)
            player.inventory.add(noted, amount)
            chatNpc("Ah, an Ardougne cloak! Here, have $amount pure essence for your trouble.")
        }
    }
}

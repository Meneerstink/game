package gg.rsmod.plugins.content.areas.gutanoth

import gg.rsmod.game.fs.def.NpcDef

/*
 * O22-ZONDER-BRON (SIGILS-QUESTS-REST, 24-11): the two Ogre traders in Gu'Tanoth that will not trade.
 * Sources: OSRS Wiki Transcript:Ogre trader ("Talk or trade (food stall)", "Talk (empty stall)", "Trade (empty stall)"),
 * "Ogre trader" (versions: 2513,3034 and 2525,3043) and "Counter (Gu'Tanoth)" (the rock cake counter at 2513,3036 - so the trader
 * at 2513,3034, our 875, stands at the food stall; 876 at 2525,3043 at the empty one).
 */

val FOOD_STALL = Npcs.OGRE_TRADER_875
val EMPTY_STALL = Npcs.OGRE_TRADER_876

fun bound(npc: Int, option: String, lines: String) {
    // Bound only where the cache has the option (a missing option is a boot error).
    if (world.definitions.get(NpcDef::class.java, npc).options.any { it.equals(option, ignoreCase = true) }) {
        on_npc_option(npc = npc, option = option) { player.queue { chatNpc(lines) } }
    }
}

bound(FOOD_STALL, "talk-to", "Arr, small thing wants my food, does it? I'll teach you to deal with ogres!")
bound(FOOD_STALL, "trade", "Arr, small thing wants my food, does it? I'll teach you to deal with ogres!")
bound(EMPTY_STALL, "talk-to", "Grrr, little animal; I shall destroy you!")
bound(EMPTY_STALL, "trade", "Trade with me? Who do you think you are?")

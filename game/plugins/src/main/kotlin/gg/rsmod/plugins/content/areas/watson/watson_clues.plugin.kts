package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.plugins.content.scrolls.ClueScrollTier

// Wiki Watson map pin, cache house square 6455; one service NPC, no second world copy.
spawn_npc(npc = Npcs.WATSON, x = 1646, z = 3574, height = 0, walkRadius = 4)
on_npc_option(Npcs.WATSON, "talk-to") { WatsonDialogue.open(player) }
on_npc_option(Npcs.WATSON, "check") { WatsonDialogue.check(player) }
ClueScrollTier.values().filter { it.scrollId >= 0 }.forEach { tier ->
    on_item_on_npc(item = tier.scrollId, npc = Npcs.WATSON) {
        player.queue { WatsonDialogue.chooseOffer(this, tier) }
    }
}

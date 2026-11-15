package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.plugins.content.scrolls.ClueScrollTier

// Wiki Watson map pin, cache house square 6455; one service NPC, no second world copy.
spawn_npc(npc = Npcs.WATSON, x = 1646, z = 3574, height = 0, walkRadius = 4)
val watsonVariants = WatsonAppearance.layout?.let { listOf(it.root, it.before, it.after) } ?: listOf(Npcs.WATSON)
watsonVariants.forEach { npc ->
    on_npc_option(npc, "talk-to") { WatsonDialogue.open(player) }
    if (if_npc_has_option(npc, "check")) on_npc_option(npc, "check") { WatsonDialogue.check(player) }
    ClueScrollTier.values().filter { it.scrollId >= 0 }.forEach { tier ->
        on_item_on_npc(item = tier.scrollId, npc = npc) {
            player.queue { WatsonDialogue.chooseOffer(this, tier) }
        }
    }
}
on_world_init { WatsonAppearance.validate(world) }
on_login { WatsonAppearance.sync(player) }

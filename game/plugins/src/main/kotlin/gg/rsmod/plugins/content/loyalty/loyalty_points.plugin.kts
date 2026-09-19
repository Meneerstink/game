package gg.rsmod.plugins.content.loyalty

import gg.rsmod.game.model.attr.LOYALTY_POINTS
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.store.StoreUi

/**
 * @author Alycia <https://github.com/alycii>
 *
 * Night run 2026-09-19: Xuan's former "Loyalty Rewards" stock moved into the central 78 Store catalogue (Loyalty Shop,
 * "Holiday cosmetics" at the same prices), so there is exactly one loyalty shop. Xuan opens that tab.
 */

on_npc_option(npc = Npcs.XUAN, option = "talk-to") {
    player.queue {
        chatNpc("Good day, my friend! Good day!")
        chatNpc(
            *"It is my privilege to offer you access to an exclusive stock of the finest and most exotic wares."
                .splitForDialogue(),
        )
        when (options("Show me what you have.", "How many Loyalty Points do I have?")) {
            FIRST_OPTION -> StoreUi.open(player, StoreCatalogue.Shop.LOYALTY)
            SECOND_OPTION -> {
                chatNpc("You have ${(player.attr[LOYALTY_POINTS] ?: 0).format()} points, my friend!")
            }
        }
    }
}

on_npc_option(npc = Npcs.XUAN, option = "open-shop") {
    StoreUi.open(player, StoreCatalogue.Shop.LOYALTY)
}

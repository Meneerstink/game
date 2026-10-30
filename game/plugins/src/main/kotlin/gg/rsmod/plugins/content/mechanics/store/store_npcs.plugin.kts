package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.pvp.emblem.EmblemDialogue.cashOut

/**
 * 78 Store npcs at the Grand Exchange ([StoreNpcs]; spawned from grand_exchange_hub.plugin.kts after Skully).
 * Xuan keeps his own Loyalty handlers (loyalty_points.plugin.kts).
 */

on_npc_option(npc = StoreNpcs.DONATOR_STORE, option = "talk-to") {
    player.queue {
        chatNpc("Welcome to the Donator Store. Gear, kits and prestige items, all paid with Donator Points.", wrap = true)
        when (options("Show me the Donator Store.", "Never mind.")) {
            FIRST_OPTION -> StoreUi.open(player, StoreCatalogue.Shop.DONATOR)
        }
    }
}

on_npc_option(npc = StoreNpcs.DONATOR_STORE, option = "trade") {
    StoreUi.open(player, StoreCatalogue.Shop.DONATOR)
}

on_npc_option(npc = StoreNpcs.DEADMAN_STORE, option = "talk-to") {
    player.queue {
        chatNpc("Deadman Points buy Deadman rewards here. Earn them the hard way.", wrap = true)
        // Deadman emblems (owner 2026-09-25) are cashed in here, through the same Deadman Points currency.
        val emblemOption = if (DeadmanEmblem.holdings(player).isEmpty()) "" else "I'd like to cash in my emblem."
        // N424 (owner 2026-10-29): no looting bag here any more - the bag is out of the game.
        // options() drops blank entries, so the answer is matched by its text, not its position.
        // Owner 2026-10-24 (N348): no "I'd like a Deadman's skull" here (new players get the skull in their starter kit).
        val shown = listOf("Show me the Deadman Store.", emblemOption, "Never mind.").filter { it.isNotEmpty() }
        when (shown.getOrNull(options(*shown.toTypedArray()) - 1)) {
            emblemOption -> cashOut(StoreNpcs.DEADMAN_STORE)
            shown[0] -> StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
        }
    }
}

on_npc_option(npc = StoreNpcs.DEADMAN_STORE, option = "rewards") {
    StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
}

// Owner 2026-09-30: no "Skull" option on this npc (cache option slot 5 cleared with NpcOptionsTool; PK skulls still come from
// attacking and the other skull sources - PvpSkull is unchanged).

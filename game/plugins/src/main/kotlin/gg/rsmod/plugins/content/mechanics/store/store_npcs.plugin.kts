package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull

/**
 * 78 Store npcs at the Grand Exchange ([StoreNpcs]; spawned from grand_exchange_hub.plugin.kts after Skully).
 * Xuan keeps his own Loyalty handlers (loyalty_points.plugin.kts).
 */

on_npc_option(npc = StoreNpcs.DONATOR_STORE, option = "talk-to") {
    player.queue {
        chatNpc("Welcome to the Donator Store. Everything here is cosmetic and paid with Donator Points.", wrap = true)
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
        when (options("Show me the Deadman Store.", "Never mind.")) {
            FIRST_OPTION -> StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
        }
    }
}

on_npc_option(npc = StoreNpcs.DEADMAN_STORE, option = "rewards") {
    StoreUi.open(player, StoreCatalogue.Shop.DEADMAN)
}

/** The OSRS Emblem Trader's "Skull" option: a PK skull on request, after a confirmation (inside a guarded zone the
 * guards react to it exactly as to any other skull). */
on_npc_option(npc = StoreNpcs.DEADMAN_STORE, option = "skull") {
    player.queue {
        if (PvpSkull.isSkulled(player)) {
            chatNpc("You're already skulled.")
            return@queue
        }
        when (options("Give me a PK skull. (The guards here will attack!)", "No thanks.", title = "Get a PK skull?")) {
            FIRST_OPTION -> PvpSkull.applyTestSkull(player)
        }
    }
}

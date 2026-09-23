package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.plugins.content.mechanics.store.StoreNpcs

/*
 * Breach Points (owner 2026-09-23, [BreachPoints]): the balance command and the Archaic emblem (tier 5) trade-in at the Deadman
 * Store npc (the OSRS Emblem Trader clone - OSRS Wiki: emblems "can be traded in for store credit ... by speaking to the Emblem
 * Trader").
 */

on_command("breachpoints") {
    player.message(
        "Breach Points: ${"%,d".format(BreachPoints.balance(player))}. Earned from breach damage: " +
            "${"%,d".format(BreachPoints.earned(player))} / ${"%,d".format(BreachPoints.DAMAGE_CAP)}.",
    )
}

if (BreachIds.ARCHAIC_EMBLEM_TIER_5 > 0) {
    on_item_on_npc(item = BreachIds.ARCHAIC_EMBLEM_TIER_5, npc = StoreNpcs.DEADMAN_STORE) {
        player.queue {
            val value = BreachPoints.EMBLEM_TIER_5_VALUE
            chatNpc("I'll give you ${"%,d".format(value)} Breach Points for that emblem. Deal?", wrap = true)
            if (options("Yes, trade it in.", "No, I'll keep it.") != 1) return@queue
            // Removed first, credited only if the removal really happened: no double trade-in on a double click.
            if (player.inventory.remove(item = BreachIds.ARCHAIC_EMBLEM_TIER_5, amount = 1).hasSucceeded()) {
                BreachPoints.addBalance(player, value)
                player.message("You trade in the emblem for ${"%,d".format(value)} Breach Points (total ${"%,d".format(BreachPoints.balance(player))}).")
            }
        }
    }
}

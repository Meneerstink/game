package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.plugins.content.mechanics.death.BrokenItemRepair
import gg.rsmod.plugins.content.mechanics.pvp.SkullyRoster
import gg.rsmod.plugins.content.mechanics.trouver.Trouver
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry

/* GE service hub. The bank-side Skully roster (Skully / Skully Jr / Sr / Max / Bob + Loot Chest at
 * each bank) lives in mechanics/pvp/SkullyRoster: owner 2026-09-16/17 - never inside a booth or
 * behind a counter, always on a tile reachable from the customer side, no combat level. */
on_world_init {
    println(SkullyRoster.spawnAll(world))
    // The 78 Store npcs are posted in the Royal Hall by data/world_edits.json (GeHomeHall).
}

// GE services requested by the owner. Since 2026-09-22 every one of these service npcs (Perdu, Bob, Pikkupstix, Kuradal,
// Max, Mandrith, Sir Tiffy, Party Pete, Ava, Evil Dave, the Wise Old Man, the tool leprechaun, the drunken dwarf, Ping,
// Pong, Azzanadra, the Archaeologist, the Oneiromancer, King Narnode, Aleck, Lucien and the penguin) stands in the home
// hall instead of on the open exchange floor - see ge_home_hall.plugin.kts. Existing global handlers provide their
// real functions wherever they stand. (Ava and Evil Dave used to share one tile here, 3175,3493.)

// A cache-backed obelisk definition with the existing Infuse-pouch/Renew-points handlers.
spawn_obj(obj = 50205, x = 3164, z = 3497, type = 10, rot = 0)

// Owner instruction (`cRYSTAL.rtf`, 2026-09-16): "put the singing bowl in grand exchange" for convenient access, instead
// of only Prifddinas (not built here - Phase-5-style scope). The real cache object/options are used (see
// `crystal_singing_bowl.plugin.kts`), not an invented tradeable item.
spawn_obj(obj = Objs.SINGING_BOWL, x = 3163, z = 3494, type = 10, rot = 0)

// Deadman guard definitions and stationed spawns live in mechanics/pvp/city_guards.plugin.kts
// (owner instruction 2026-09-16: guards stand on the owner-pinned posts, not at every bank cluster).

// Perdu (upstream OSRS npc 7456) is a real import (RCV-012 "ferox" batch, tx-20260914-103151, local
// npc id 14394) - not a placeholder. She fronts both her real OSRS services: repairing every
// PvpDeathBreakables broken item at its sourced cost, and the Trouver lock/unlock engine below.
val PERDU_REPAIR = "Can you repair my items?"
val PERDU_LOST = "Have you got any of my lost things?"
val PERDU_NOTHING = "Nothing, thanks."

on_npc_option(npc = Npcs.PERDU, option = "talk-to") {
    player.queue {
        // OSRS Wiki "Breach (scenery)": "The player can find the location of the active breach by talking to Perdu".
        chatNpc(gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.statusLine(), wrap = true)
        // Every PvP-damaged item: imported broken/mangled ids and attribute-broken untradeables (owner 2026-09-26).
        val hasBroken = player.inventory.rawItems.filterNotNull().any(gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection::isDamaged)
        gg.rsmod.plugins.content.mechanics.death.PerduLostProperty.observe(player)
        val hasLost = gg.rsmod.plugins.content.mechanics.death.PerduReclaim.has(player)
        if (!hasBroken && !hasLost) {
            chatNpc("I can repair and protect eligible equipment, and I return things you've lost - for a price.", wrap = true)
            return@queue
        }
        // N508 (owner 2026-11-01, bug 20261101-000701): a real dialogue - the player chooses what to ask. Before, his character said
        // "Can you repair this for me?" on his own as soon as a broken item was in the backpack.
        val choices = listOfNotNull(PERDU_REPAIR.takeIf { hasBroken }, PERDU_LOST.takeIf { hasLost }, PERDU_NOTHING)
        when (choices[options(*choices.toTypedArray()) - 1]) {
            PERDU_REPAIR -> {
                chatPlayer(PERDU_REPAIR)
                BrokenItemRepair.repair(this, playerLine = null)
            }
            PERDU_LOST -> {
                chatPlayer(PERDU_LOST)
                // N414: lost pets come back free, in the dialogue; N382: everything with a price is in his Lost Property window.
                gg.rsmod.plugins.content.mechanics.death.PerduReclaim.returnPets(this)
                if (gg.rsmod.plugins.content.mechanics.death.PerduReclaim.paidOffers(player).isNotEmpty()) {
                    chatNpc("I've got some of your things. Have a look.", wrap = true)
                    gg.rsmod.plugins.content.mechanics.death.PerduShop.open(player)
                }
            }
            else -> chatPlayer(PERDU_NOTHING)
        }
    }
}

// N382 (owner 2026-10-24): Perdu's Trade is the OSRS Lost Property shop window - the player's own lost items at their price.
on_npc_option(npc = Npcs.PERDU, option = "trade") {
    if (gg.rsmod.plugins.content.mechanics.death.PerduShop.open(player)) return@on_npc_option
    player.queue {
        // Nothing with a price: the free pets, else why there is nothing.
        if (!gg.rsmod.plugins.content.mechanics.death.PerduReclaim.returnPets(this)) gg.rsmod.plugins.content.mechanics.death.PerduReclaim.nothingToReturn(this)
    }
}

// Perdu's Trouver unlock and her use-item repair are registered in mechanics/trouver/trouver.plugin.kts, after the registry is filled.

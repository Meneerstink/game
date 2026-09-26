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
    // After Skully, so the 78 Store npcs never take his or his chest's tile.
    println(gg.rsmod.plugins.content.mechanics.store.StoreNpcs.spawnAll(world))
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
on_npc_option(npc = Npcs.PERDU, option = "talk-to") {
    player.queue {
        // OSRS Wiki "Breach (scenery)": "The player can find the location of the active breach by talking to Perdu".
        chatNpc(gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.statusLine(), wrap = true)
        // Every PvP-damaged item: imported broken/mangled ids and attribute-broken untradeables (owner 2026-09-26).
        val hasBroken = player.inventory.rawItems.filterNotNull().any(gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection::isDamaged)
        if (!hasBroken) {
            chatNpc("I can repair and protect eligible equipment. Bring me a broken item and I'll fix it for a fee.", wrap = true)
            return@queue
        }
        BrokenItemRepair.repair(this)
    }
}

// Perdu now hosts the generic Trouver engine instead of the old command-only unlock path.
TrouverRegistry.all().forEach { lockable ->
    on_item_on_npc(item = lockable.lockedItemId, npc = Npcs.PERDU) {
        // A locked item that broke on a PvP death (attribute-broken) is repaired first, never unlocked in its broken state.
        if (gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.isDamaged(player.getInteractingItem())) {
            player.queue { BrokenItemRepair.repair(this) }
            return@on_item_on_npc
        }
        when (Trouver.unlock(player, player.getInteractingItem())) {
            Trouver.UnlockResult.Success -> {}
            Trouver.UnlockResult.NotLocked -> player.message("That item isn't locked.")
            Trouver.UnlockResult.ItemNotHeld -> player.message("You don't have that item.")
            Trouver.UnlockResult.InventoryFull -> {}
        }
    }
}

// OSRS: a broken or mangled item is repaired by using it on Perdu.
(gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.ALL.map { it.brokenId } +
    TrouverRegistry.all().flatMap { listOfNotNull(it.brokenItemId, it.mangledItemId) }).distinct().forEach { damaged ->
    on_item_on_npc(item = damaged, npc = Npcs.PERDU) { player.queue { BrokenItemRepair.repair(this) } }
}

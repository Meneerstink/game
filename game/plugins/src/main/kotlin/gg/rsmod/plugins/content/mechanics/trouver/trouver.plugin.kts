package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.plugins.api.ext.getCommandArgs
import gg.rsmod.plugins.api.ext.message

/**
 * Wires the generic [Trouver] locking engine into the world: registers this server's currently
 * curated set of lockable item pairs and an item-on-item interaction to lock. Unlocking is hosted
 * by the real, imported Perdu npc at the Grand Exchange (`grand_exchange_hub.plugin.kts`); the
 * `::trouverunlock` command below remains only as a backup path - see [Trouver]'s class doc.
 *
 * Registry population: only one demonstration pair is registered so far (Fire cape <-> Fire cape
 * (l), items 6570/22324) to prove the engine end-to-end - see `RSPS_IMPORT_MANIFEST.yml`. Curating
 * this server's full production list of which untradeables deserve Trouver protection is a
 * separate, later product-curation task, not a technical blocker to the engine itself.
 */
TrouverLockables.registerAll()

TrouverRegistry.all().forEach { lockable ->
    on_item_on_item(item1 = Items.TROUVER_PARCHMENT, item2 = lockable.baseItemId) {
        val item = player.inventory.items.filterNotNull().firstOrNull { it.id == lockable.baseItemId } ?: return@on_item_on_item
        when (Trouver.lock(player, item)) {
            Trouver.LockResult.Success -> {}
            Trouver.LockResult.NotLockable -> {} // unreachable - only registered pairs are ever wired here
            Trouver.LockResult.ItemNotHeld -> player.message("You don't have that item.")
            Trouver.LockResult.MissingParchment -> player.message("You need a Trouver parchment to do that.")
            Trouver.LockResult.InsufficientFunds -> player.message("You need ${Trouver.LOCK_FEE} coins to do that.")
        }
    }
}

/*
 * Perdu (Grand Exchange) hosts the Trouver engine: using a locked item on her unlocks it, and using a broken or mangled item
 * on her repairs it (OSRS). Registered here, right after the registry above is filled - a script elsewhere could read the
 * registry before this one has run.
 */
TrouverRegistry.all().forEach { lockable ->
    on_item_on_npc(item = lockable.lockedItemId, npc = Npcs.PERDU) {
        // A locked item that broke on a PvP death (attribute-broken) is repaired first, never unlocked in its broken state.
        if (gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.isDamaged(player.getInteractingItem())) {
            player.queue { gg.rsmod.plugins.content.mechanics.death.BrokenItemRepair.repair(this) }
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
    on_item_on_npc(item = damaged, npc = Npcs.PERDU) { player.queue { gg.rsmod.plugins.content.mechanics.death.BrokenItemRepair.repair(this) } }
}
on_command("trouverunlock") {
    val args = player.getCommandArgs()
    val itemId = args.getOrNull(0)?.toIntOrNull()
    if (itemId == null) {
        player.message("Usage: ::trouverunlock <locked item id>")
        return@on_command
    }
    val item = player.inventory.items.filterNotNull().firstOrNull { it.id == itemId }
    if (item == null) {
        player.message("You don't have that item.")
        return@on_command
    }
    when (Trouver.unlock(player, item)) {
        Trouver.UnlockResult.Success -> {}
        Trouver.UnlockResult.NotLocked -> player.message("That item isn't locked.")
        Trouver.UnlockResult.ItemNotHeld -> player.message("You don't have that item.")
        Trouver.UnlockResult.InventoryFull -> {} // Trouver.unlock already messaged the player
    }
}

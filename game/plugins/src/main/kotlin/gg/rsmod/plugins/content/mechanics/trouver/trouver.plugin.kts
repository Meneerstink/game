package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.plugins.api.ext.getCommandArgs
import gg.rsmod.plugins.api.ext.message

/**
 * Wires the generic [Trouver] locking engine into the world: registers this server's currently
 * curated set of lockable item pairs, an item-on-item interaction to lock, and a `::trouverunlock`
 * stopgap command to unlock. See [Trouver]'s class doc for why a command fronts unlocking rather
 * than an NPC - Perdu is confirmed genuinely absent from this ~2011/rev-667 cache.
 *
 * Registry population: only one demonstration pair is registered so far (Fire cape <-> Fire cape
 * (l), items 6570/22324) to prove the engine end-to-end - see `RSPS_IMPORT_MANIFEST.yml`. Curating
 * this server's full production list of which untradeables deserve Trouver protection is a
 * separate, later product-curation task, not a technical blocker to the engine itself.
 */
TrouverRegistry.register(TrouverLockable(baseItemId = Items.FIRE_CAPE, lockedItemId = Items.FIRE_CAPE_LOCKED_22324))
// OSRS-IMPORT capesrings: Infernal cape page - a Trouver parchment plus 500,000 coins locks the cape into Infernal cape (l).
// Its "mangled"/"(l) (broken)" variants are not imported yet, so the engine's documented keep-whole fallback applies.
TrouverRegistry.register(TrouverLockable(baseItemId = Items.INFERNAL_CAPE, lockedItemId = Items.INFERNAL_CAPE_L))

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

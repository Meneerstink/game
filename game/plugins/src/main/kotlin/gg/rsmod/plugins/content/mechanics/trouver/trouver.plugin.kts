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
 * N251 (owner 2026-10-24 "precies net als osrs"): OSRS Wiki "Trouver parchment" - "Giving certain untradeable items and a trouver
 * parchment to Perdu will lock the item to the player" (500,000 coins). Using a lockable item on Perdu asks first, then locks it.
 */
TrouverRegistry.all().forEach { lockable ->
    on_item_on_npc(item = lockable.baseItemId, npc = Npcs.PERDU) {
        val slot = player.getInteractingItemSlot()
        player.queue {
            val name = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, lockable.baseItemId).name
            if (player.inventory.getItemCount(Items.TROUVER_PARCHMENT) < 1) {
                chatNpc("I can lock your $name so it is never destroyed on death. Bring me a Trouver parchment and ${"%,d".format(Trouver.LOCK_FEE)} coins.", npc = Npcs.PERDU, wrap = true)
                return@queue
            }
            if (options("Yes, lock it (${"%,d".format(Trouver.LOCK_FEE)} coins + a parchment).", "No, thanks.", title = "Lock your $name?") != 1) return@queue
            val item = player.inventory[slot]?.takeIf { it.id == lockable.baseItemId }
                ?: player.inventory.items.filterNotNull().firstOrNull { it.id == lockable.baseItemId }
            if (item == null) {
                player.message("You don't have that item.")
                return@queue
            }
            when (Trouver.lock(player, item)) {
                Trouver.LockResult.Success -> chatNpc("There you go - your $name is locked to you now.", npc = Npcs.PERDU, wrap = true)
                Trouver.LockResult.NotLockable -> {}
                Trouver.LockResult.ItemNotHeld -> player.message("You don't have that item.")
                Trouver.LockResult.MissingParchment -> player.message("You need a Trouver parchment to do that.")
                Trouver.LockResult.InsufficientFunds -> chatNpc("That'll be ${"%,d".format(Trouver.LOCK_FEE)} coins - you don't have enough on you.", npc = Npcs.PERDU, wrap = true)
            }
        }
    }
}

// N251 (owner 2026-10-24: "als je een trouver parchment op perdu gebruikt: nothing interesting happens"): the parchment on Perdu
// offers to lock the first lockable item in the inventory, else says which items he can lock.
on_item_on_npc(item = Items.TROUVER_PARCHMENT, npc = Npcs.PERDU) {
    val lockable = player.inventory.items.filterNotNull().firstOrNull { TrouverRegistry.lockableFor(it.id) != null }
    player.queue {
        if (lockable == null) {
            chatNpc("Bring me an untradeable item I can lock - a fire cape, an infernal cape, a rune pouch, a quiver - and use it on me with the parchment and ${"%,d".format(Trouver.LOCK_FEE)} coins.", npc = Npcs.PERDU, wrap = true)
            return@queue
        }
        val name = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, lockable.id).name
        if (options("Yes, lock my $name (${"%,d".format(Trouver.LOCK_FEE)} coins).", "No, thanks.", title = "Lock your $name?") != 1) return@queue
        when (Trouver.lock(player, lockable)) {
            Trouver.LockResult.Success -> chatNpc("There you go - your $name is locked to you now.", npc = Npcs.PERDU, wrap = true)
            Trouver.LockResult.InsufficientFunds -> chatNpc("That'll be ${"%,d".format(Trouver.LOCK_FEE)} coins - you don't have enough on you.", npc = Npcs.PERDU, wrap = true)
            else -> player.message("You can't lock that right now.")
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

/*
 * N60 (owner 2026-10-22, VRAGEN 55: "trouver parchments stapelen nu als feathers - dat hoort niet; niet stapelbaar, max 28 in een
 * inventory"): the cache item lost its stackable flag. A stack carried from before keeps one parchment in its slot; the rest goes to
 * the bank (which stacks everything), so nothing is lost and no slot holds an impossible stack.
 */
on_login {
    Trouver.splitCarriedStack(player)
}

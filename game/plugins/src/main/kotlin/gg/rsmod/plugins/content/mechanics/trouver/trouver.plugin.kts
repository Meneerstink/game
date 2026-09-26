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
TrouverRegistry.register(TrouverLockable(baseItemId = Items.FIRE_CAPE, lockedItemId = Items.FIRE_CAPE_LOCKED_22324))
// OSRS-IMPORT capesrings: Infernal cape page - a Trouver parchment plus 500,000 coins locks the cape into Infernal cape (l).
// Its "mangled"/"(l) (broken)" variants are not imported yet, so the engine's documented keep-whole fallback applies.
TrouverRegistry.register(TrouverLockable(baseItemId = Items.INFERNAL_CAPE, lockedItemId = Items.INFERNAL_CAPE_L))
// OSRS-IMPORT assembler: the Ava's assembler infobox lists "Ava's assembler (l)" (24222) as its locked variant.
TrouverRegistry.register(TrouverLockable(baseItemId = Items.AVAS_ASSEMBLER, lockedItemId = Items.AVAS_ASSEMBLER_L))
// OSRS-IMPORT sceptres: "pay 500,000 coins plus a Trouver parchment to lock the item at Perdu"; below level 20 a locked sceptre becomes
// its broken form (OSRS Wiki "Ancient sceptre"), above level 20 its mangled form (500,000 coins to the PKer).
TrouverRegistry.register(TrouverLockable(baseItemId = Items.ANCIENT_SCEPTRE, lockedItemId = Items.ANCIENT_SCEPTRE_L, brokenItemId = Items.ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ANCIENT_SCEPTRE_L_MANGLED))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.BLOOD_ANCIENT_SCEPTRE, lockedItemId = Items.BLOOD_ANCIENT_SCEPTRE_L, brokenItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_MANGLED))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.ICE_ANCIENT_SCEPTRE, lockedItemId = Items.ICE_ANCIENT_SCEPTRE_L, brokenItemId = Items.ICE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ICE_ANCIENT_SCEPTRE_L_MANGLED))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.SMOKE_ANCIENT_SCEPTRE, lockedItemId = Items.SMOKE_ANCIENT_SCEPTRE_L, brokenItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_MANGLED))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.SHADOW_ANCIENT_SCEPTRE, lockedItemId = Items.SHADOW_ANCIENT_SCEPTRE_L, brokenItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_MANGLED))
// OSRS-IMPORT capes: each infobox lists a Trouver-locked "(l)" version (imbued god capes and max capes, assembler max capes, Masori
// assembler); Dizana's max cape (l) also has "(l) (broken)" ("Locked Dizana's max cape now breaks on death below level 20").
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_SARADOMIN_CAPE, lockedItemId = Items.IMBUED_SARADOMIN_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_GUTHIX_CAPE, lockedItemId = Items.IMBUED_GUTHIX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_ZAMORAK_CAPE, lockedItemId = Items.IMBUED_ZAMORAK_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_SARADOMIN_MAX_CAPE, lockedItemId = Items.IMBUED_SARADOMIN_MAX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_GUTHIX_MAX_CAPE, lockedItemId = Items.IMBUED_GUTHIX_MAX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_ZAMORAK_MAX_CAPE, lockedItemId = Items.IMBUED_ZAMORAK_MAX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.ASSEMBLER_MAX_CAPE, lockedItemId = Items.ASSEMBLER_MAX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.MASORI_ASSEMBLER, lockedItemId = Items.MASORI_ASSEMBLER_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.MASORI_ASSEMBLER_MAX_CAPE, lockedItemId = Items.MASORI_ASSEMBLER_MAX_CAPE_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.DIZANAS_MAX_CAPE, lockedItemId = Items.DIZANAS_MAX_CAPE_L, brokenItemId = Items.DIZANAS_MAX_CAPE_L_BROKEN, mangledItemId = Items.DIZANAS_MAX_CAPE_L_MANGLED))
// OSRS-IMPORT quiver (item page "Dizana's quiver", fetched 2026-09-16): "can be locked by bringing it, along with a
// Trouver parchment and 500,000 coins, to Perdu." Charges and any stored ammo survive locking (Trouver.lock/unlock
// now copy item attributes) exactly like relogging or banking the same item would. No broken/mangled quiver variant
// is imported (SOURCE_GAP, see QuiverDeathRules), so only the keep-whole fallback applies here.
TrouverRegistry.register(TrouverLockable(baseItemId = Items.DIZANAS_QUIVER, lockedItemId = Items.DIZANAS_QUIVER_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.DIZANAS_QUIVER_UNCHARGED, lockedItemId = Items.DIZANAS_QUIVER_L_UNCHARGED))
// Owner 2026-09-26 (death rework): the rune pouch is lockable (OSRS "Rune pouch (l)" 24416 / "Divine rune pouch (l)" 27509, cache tx-20260926-042642).
// A locked pouch keeps the empty pouch on a PvP death; its runes always go to the killer (UntradeableDeathProtection).
TrouverRegistry.register(TrouverLockable(baseItemId = Items.RUNE_POUCH, lockedItemId = Items.RUNE_POUCH_L))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.DIVINE_RUNE_POUCH, lockedItemId = Items.DIVINE_RUNE_POUCH_L))

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

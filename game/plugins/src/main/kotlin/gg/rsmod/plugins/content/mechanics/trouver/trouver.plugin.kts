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
// OSRS-IMPORT assembler: the Ava's assembler infobox lists "Ava's assembler (l)" (24222) as its locked variant.
TrouverRegistry.register(TrouverLockable(baseItemId = Items.AVAS_ASSEMBLER, lockedItemId = Items.AVAS_ASSEMBLER_L))
// OSRS-IMPORT sceptres: "pay 500,000 coins plus a Trouver parchment to lock the item at Perdu"; below level 20 a locked sceptre becomes
// its broken form (OSRS Wiki "Ancient sceptre"). The mangled forms (above level 20, 500,000 coins to the PKer) are imported but this
// engine holds one broken id per pair (SOURCE_GAP recorded).
TrouverRegistry.register(TrouverLockable(baseItemId = Items.ANCIENT_SCEPTRE, lockedItemId = Items.ANCIENT_SCEPTRE_L, brokenItemId = Items.ANCIENT_SCEPTRE_L_BROKEN))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.BLOOD_ANCIENT_SCEPTRE, lockedItemId = Items.BLOOD_ANCIENT_SCEPTRE_L, brokenItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_BROKEN))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.ICE_ANCIENT_SCEPTRE, lockedItemId = Items.ICE_ANCIENT_SCEPTRE_L, brokenItemId = Items.ICE_ANCIENT_SCEPTRE_L_BROKEN))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.SMOKE_ANCIENT_SCEPTRE, lockedItemId = Items.SMOKE_ANCIENT_SCEPTRE_L, brokenItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_BROKEN))
TrouverRegistry.register(TrouverLockable(baseItemId = Items.SHADOW_ANCIENT_SCEPTRE, lockedItemId = Items.SHADOW_ANCIENT_SCEPTRE_L, brokenItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_BROKEN))
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
TrouverRegistry.register(TrouverLockable(baseItemId = Items.DIZANAS_MAX_CAPE, lockedItemId = Items.DIZANAS_MAX_CAPE_L, brokenItemId = Items.DIZANAS_MAX_CAPE_L_BROKEN))

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

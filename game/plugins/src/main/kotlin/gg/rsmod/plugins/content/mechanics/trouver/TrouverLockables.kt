package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.plugins.api.cfg.Items

/**
 * This server's Trouver-lockable item pairs (moved out of `trouver.plugin.kts` 2026-10-04 so the death rules, their tests and the
 * death-item audit all see the exact list the live server registers). [registerAll] is idempotent.
 */
object TrouverLockables {
    /** Every lockable pair (base, locked and the imported broken/mangled ids). */
    val ALL: List<TrouverLockable> =
        listOf(
            TrouverLockable(baseItemId = Items.FIRE_CAPE, lockedItemId = Items.FIRE_CAPE_LOCKED_22324),
            // OSRS-IMPORT capesrings: Infernal cape page - a Trouver parchment plus 500,000 coins locks the cape into Infernal cape (l).
            // Its "mangled"/"(l) (broken)" variants are not imported yet, so the engine's documented keep-whole fallback applies.
            TrouverLockable(baseItemId = Items.INFERNAL_CAPE, lockedItemId = Items.INFERNAL_CAPE_L),
            // OSRS-IMPORT assembler: the Ava's assembler infobox lists "Ava's assembler (l)" (24222) as its locked variant.
            TrouverLockable(baseItemId = Items.AVAS_ASSEMBLER, lockedItemId = Items.AVAS_ASSEMBLER_L),
            // OSRS-IMPORT sceptres: "pay 500,000 coins plus a Trouver parchment to lock the item at Perdu"; below level 20 a locked sceptre becomes
            // its broken form (OSRS Wiki "Ancient sceptre"), above level 20 its mangled form (500,000 coins to the PKer).
            TrouverLockable(baseItemId = Items.ANCIENT_SCEPTRE, lockedItemId = Items.ANCIENT_SCEPTRE_L, brokenItemId = Items.ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.BLOOD_ANCIENT_SCEPTRE, lockedItemId = Items.BLOOD_ANCIENT_SCEPTRE_L, brokenItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.ICE_ANCIENT_SCEPTRE, lockedItemId = Items.ICE_ANCIENT_SCEPTRE_L, brokenItemId = Items.ICE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ICE_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.SMOKE_ANCIENT_SCEPTRE, lockedItemId = Items.SMOKE_ANCIENT_SCEPTRE_L, brokenItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.SHADOW_ANCIENT_SCEPTRE, lockedItemId = Items.SHADOW_ANCIENT_SCEPTRE_L, brokenItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_MANGLED),
            // OSRS-IMPORT capes: each infobox lists a Trouver-locked "(l)" version (imbued god capes and max capes, assembler max capes, Masori
            // assembler); Dizana's max cape (l) also has "(l) (broken)" ("Locked Dizana's max cape now breaks on death below level 20").
            TrouverLockable(baseItemId = Items.IMBUED_SARADOMIN_CAPE, lockedItemId = Items.IMBUED_SARADOMIN_CAPE_L),
            TrouverLockable(baseItemId = Items.IMBUED_GUTHIX_CAPE, lockedItemId = Items.IMBUED_GUTHIX_CAPE_L),
            TrouverLockable(baseItemId = Items.IMBUED_ZAMORAK_CAPE, lockedItemId = Items.IMBUED_ZAMORAK_CAPE_L),
            TrouverLockable(baseItemId = Items.IMBUED_SARADOMIN_MAX_CAPE, lockedItemId = Items.IMBUED_SARADOMIN_MAX_CAPE_L),
            TrouverLockable(baseItemId = Items.IMBUED_GUTHIX_MAX_CAPE, lockedItemId = Items.IMBUED_GUTHIX_MAX_CAPE_L),
            TrouverLockable(baseItemId = Items.IMBUED_ZAMORAK_MAX_CAPE, lockedItemId = Items.IMBUED_ZAMORAK_MAX_CAPE_L),
            TrouverLockable(baseItemId = Items.ASSEMBLER_MAX_CAPE, lockedItemId = Items.ASSEMBLER_MAX_CAPE_L),
            TrouverLockable(baseItemId = Items.MASORI_ASSEMBLER, lockedItemId = Items.MASORI_ASSEMBLER_L),
            TrouverLockable(baseItemId = Items.MASORI_ASSEMBLER_MAX_CAPE, lockedItemId = Items.MASORI_ASSEMBLER_MAX_CAPE_L),
            TrouverLockable(baseItemId = Items.DIZANAS_MAX_CAPE, lockedItemId = Items.DIZANAS_MAX_CAPE_L, brokenItemId = Items.DIZANAS_MAX_CAPE_L_BROKEN, mangledItemId = Items.DIZANAS_MAX_CAPE_L_MANGLED),
            // OSRS-IMPORT quiver (item page "Dizana's quiver", fetched 2026-09-16): "can be locked by bringing it, along with a
            // Trouver parchment and 500,000 coins, to Perdu." Charges and any stored ammo survive locking (Trouver.lock/unlock
            // now copy item attributes) exactly like relogging or banking the same item would. No broken/mangled quiver variant
            // is imported (SOURCE_GAP, see QuiverDeathRules), so only the keep-whole fallback applies here.
            TrouverLockable(baseItemId = Items.DIZANAS_QUIVER, lockedItemId = Items.DIZANAS_QUIVER_L),
            TrouverLockable(baseItemId = Items.DIZANAS_QUIVER_UNCHARGED, lockedItemId = Items.DIZANAS_QUIVER_L_UNCHARGED),
            // Owner 2026-09-26 (death rework): the rune pouch is lockable (OSRS "Rune pouch (l)" 24416 / "Divine rune pouch (l)" 27509, cache tx-20260926-042642).
            // A locked pouch keeps the empty pouch on a PvP death; its runes always go to the killer (UntradeableDeathProtection).
            TrouverLockable(baseItemId = Items.RUNE_POUCH, lockedItemId = Items.RUNE_POUCH_L),
            TrouverLockable(baseItemId = Items.DIVINE_RUNE_POUCH, lockedItemId = Items.DIVINE_RUNE_POUCH_L),
            // OSRS Wiki "(l)" list (owner 2026-10-04 death audit): "Blessed Dizana's quiver (l)" can be locked like the plain quiver.
            TrouverLockable(baseItemId = Items.BLESSED_DIZANAS_QUIVER, lockedItemId = Items.BLESSED_DIZANAS_QUIVER_L),
        )

    /** Every id that is part of a lockable pair, in any state. */
    val IDS: Set<Int> = ALL.flatMap { listOfNotNull(it.baseItemId, it.lockedItemId, it.brokenItemId, it.mangledItemId) }.toSet()

    fun registerAll() {
        if (TrouverRegistry.lockableFor(Items.FIRE_CAPE) != null) return
        ALL.forEach(TrouverRegistry::register)
    }
}

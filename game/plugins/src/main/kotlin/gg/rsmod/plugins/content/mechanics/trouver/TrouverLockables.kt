package gg.rsmod.plugins.content.mechanics.trouver

import gg.rsmod.plugins.api.cfg.Items

/**
 * This server's Trouver-lockable item pairs (moved out of `trouver.plugin.kts` 2026-10-04 so the death rules, their tests and the
 * death-item audit all see the exact list the live server registers). [registerAll] is idempotent.
 */
object TrouverLockables {
    /**
     * Every lockable pair (base, locked and the broken/mangled ids). N416 (2026-10-25): every locked item but the rune pouches has its own
     * visible "(l) (broken)" / "(l) (mangled)" item (cache tx-20261025-004727) - before, the older ones broke as a hidden mark.
     */
    val ALL: List<TrouverLockable> =
        listOf(
            locked(Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324, Items.FIRE_CAPE_L_BROKEN, Items.FIRE_CAPE_L_MANGLED),
            // OSRS-IMPORT capesrings: Infernal cape page - a Trouver parchment plus 500,000 coins locks the cape into Infernal cape (l).
            // K01 2026-10-25: the OSRS "Infernal cape (l) (broken)/(mangled)" forms instead of the hidden broken mark.
            TrouverLockable(baseItemId = Items.INFERNAL_CAPE, lockedItemId = Items.INFERNAL_CAPE_L, brokenItemId = Items.INFERNAL_CAPE_L_BROKEN, mangledItemId = Items.INFERNAL_CAPE_L_MANGLED),
            // OSRS-IMPORT assembler: the Ava's assembler infobox lists "Ava's assembler (l)" (24222) as its locked variant.
            locked(Items.AVAS_ASSEMBLER, Items.AVAS_ASSEMBLER_L, Items.AVAS_ASSEMBLER_L_BROKEN, Items.AVAS_ASSEMBLER_L_MANGLED),
            // OSRS-IMPORT sceptres: "pay 500,000 coins plus a Trouver parchment to lock the item at Perdu"; below level 20 a locked sceptre becomes
            // its broken form (OSRS Wiki "Ancient sceptre"), above level 20 its mangled form (500,000 coins to the PKer).
            TrouverLockable(baseItemId = Items.ANCIENT_SCEPTRE, lockedItemId = Items.ANCIENT_SCEPTRE_L, brokenItemId = Items.ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.BLOOD_ANCIENT_SCEPTRE, lockedItemId = Items.BLOOD_ANCIENT_SCEPTRE_L, brokenItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.BLOOD_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.ICE_ANCIENT_SCEPTRE, lockedItemId = Items.ICE_ANCIENT_SCEPTRE_L, brokenItemId = Items.ICE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.ICE_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.SMOKE_ANCIENT_SCEPTRE, lockedItemId = Items.SMOKE_ANCIENT_SCEPTRE_L, brokenItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SMOKE_ANCIENT_SCEPTRE_L_MANGLED),
            TrouverLockable(baseItemId = Items.SHADOW_ANCIENT_SCEPTRE, lockedItemId = Items.SHADOW_ANCIENT_SCEPTRE_L, brokenItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_BROKEN, mangledItemId = Items.SHADOW_ANCIENT_SCEPTRE_L_MANGLED),
            // OSRS-IMPORT capes: each infobox lists a Trouver-locked "(l)" version (imbued god capes and max capes, assembler max capes, Masori
            // assembler); Dizana's max cape (l) also has "(l) (broken)" ("Locked Dizana's max cape now breaks on death below level 20").
            locked(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_L, Items.IMBUED_SARADOMIN_CAPE_L_BROKEN, Items.IMBUED_SARADOMIN_CAPE_L_MANGLED),
            locked(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_L, Items.IMBUED_GUTHIX_CAPE_L_BROKEN, Items.IMBUED_GUTHIX_CAPE_L_MANGLED),
            locked(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_L, Items.IMBUED_ZAMORAK_CAPE_L_BROKEN, Items.IMBUED_ZAMORAK_CAPE_L_MANGLED),
            locked(Items.IMBUED_SARADOMIN_MAX_CAPE, Items.IMBUED_SARADOMIN_MAX_CAPE_L, Items.IMBUED_SARADOMIN_MAX_CAPE_L_BROKEN, Items.IMBUED_SARADOMIN_MAX_CAPE_L_MANGLED),
            locked(Items.IMBUED_GUTHIX_MAX_CAPE, Items.IMBUED_GUTHIX_MAX_CAPE_L, Items.IMBUED_GUTHIX_MAX_CAPE_L_BROKEN, Items.IMBUED_GUTHIX_MAX_CAPE_L_MANGLED),
            locked(Items.IMBUED_ZAMORAK_MAX_CAPE, Items.IMBUED_ZAMORAK_MAX_CAPE_L, Items.IMBUED_ZAMORAK_MAX_CAPE_L_BROKEN, Items.IMBUED_ZAMORAK_MAX_CAPE_L_MANGLED),
            locked(Items.ASSEMBLER_MAX_CAPE, Items.ASSEMBLER_MAX_CAPE_L, Items.ASSEMBLER_MAX_CAPE_L_BROKEN, Items.ASSEMBLER_MAX_CAPE_L_MANGLED),
            locked(Items.MASORI_ASSEMBLER, Items.MASORI_ASSEMBLER_L, Items.MASORI_ASSEMBLER_L_BROKEN, Items.MASORI_ASSEMBLER_L_MANGLED),
            locked(Items.MASORI_ASSEMBLER_MAX_CAPE, Items.MASORI_ASSEMBLER_MAX_CAPE_L, Items.MASORI_ASSEMBLER_MAX_CAPE_L_BROKEN, Items.MASORI_ASSEMBLER_MAX_CAPE_L_MANGLED),
            TrouverLockable(baseItemId = Items.DIZANAS_MAX_CAPE, lockedItemId = Items.DIZANAS_MAX_CAPE_L, brokenItemId = Items.DIZANAS_MAX_CAPE_L_BROKEN, mangledItemId = Items.DIZANAS_MAX_CAPE_L_MANGLED),
            // OSRS-IMPORT quiver (item page "Dizana's quiver", fetched 2026-09-16): "can be locked by bringing it, along with a
            // Trouver parchment and 500,000 coins, to Perdu." Charges and any stored ammo survive locking (Trouver.lock/unlock
            // now copy item attributes) exactly like relogging or banking the same item would. N416: its broken/mangled forms are
            // clones of the Dizana's quiver (broken) with the (l) name.
            locked(Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_L, Items.DIZANAS_QUIVER_L_BROKEN, Items.DIZANAS_QUIVER_L_MANGLED),
            locked(Items.DIZANAS_QUIVER_UNCHARGED, Items.DIZANAS_QUIVER_L_UNCHARGED, Items.DIZANAS_QUIVER_L_UNCHARGED_BROKEN, Items.DIZANAS_QUIVER_L_UNCHARGED_MANGLED),
            // Owner 2026-09-26 (death rework): the rune pouch is lockable (OSRS "Rune pouch (l)" 24416 / "Divine rune pouch (l)" 27509, cache tx-20260926-042642).
            // A locked pouch keeps the empty pouch on a PvP death; its runes always go to the killer (UntradeableDeathProtection).
            TrouverLockable(baseItemId = Items.RUNE_POUCH, lockedItemId = Items.RUNE_POUCH_L),
            TrouverLockable(baseItemId = Items.DIVINE_RUNE_POUCH, lockedItemId = Items.DIVINE_RUNE_POUCH_L),
            // OSRS Wiki "(l)" list (owner 2026-10-04 death audit): "Blessed Dizana's quiver (l)" can be locked like the plain quiver.
            locked(Items.BLESSED_DIZANAS_QUIVER, Items.BLESSED_DIZANAS_QUIVER_L, Items.BLESSED_DIZANAS_QUIVER_L_BROKEN, Items.BLESSED_DIZANAS_QUIVER_L_MANGLED),
            // K01 2026-10-25 (N408, OSRS Wiki "(l)": Void Knight equipment, Fighter torso, Infernal max cape; "Trouver parchment"
            // 18 May 2022: Rune/Dragon defender (t); owner 2026-10-25: every defender from mithril up, Avernic included). The (l)
            // items are clones of this cache's own items with the OSRS names (cache tx-20261024-224158), each with its broken
            // (level 20 and below) and mangled (above level 20) form, repaired by Perdu like every other locked item.
            locked(Items.MITHRIL_DEFENDER, Items.MITHRIL_DEFENDER_L, Items.MITHRIL_DEFENDER_L_BROKEN, Items.MITHRIL_DEFENDER_L_MANGLED),
            locked(Items.ADAMANT_DEFENDER, Items.ADAMANT_DEFENDER_L, Items.ADAMANT_DEFENDER_L_BROKEN, Items.ADAMANT_DEFENDER_L_MANGLED),
            locked(Items.RUNE_DEFENDER, Items.RUNE_DEFENDER_L, Items.RUNE_DEFENDER_L_BROKEN, Items.RUNE_DEFENDER_L_MANGLED),
            locked(Items.DRAGON_DEFENDER, Items.DRAGON_DEFENDER_L, Items.DRAGON_DEFENDER_L_BROKEN, Items.DRAGON_DEFENDER_L_MANGLED),
            locked(Items.AVERNIC_DEFENDER, Items.AVERNIC_DEFENDER_L, Items.AVERNIC_DEFENDER_L_BROKEN, Items.AVERNIC_DEFENDER_L_MANGLED),
            locked(Items.RUNE_DEFENDER_T, Items.RUNE_DEFENDER_LT, Items.RUNE_DEFENDER_LT_BROKEN, Items.RUNE_DEFENDER_LT_MANGLED),
            locked(Items.DRAGON_DEFENDER_T, Items.DRAGON_DEFENDER_LT, Items.DRAGON_DEFENDER_LT_BROKEN, Items.DRAGON_DEFENDER_LT_MANGLED),
            locked(Items.FIGHTER_TORSO, Items.FIGHTER_TORSO_L, Items.FIGHTER_TORSO_L_BROKEN, Items.FIGHTER_TORSO_L_MANGLED),
            locked(Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_TOP_L, Items.VOID_KNIGHT_TOP_L_BROKEN, Items.VOID_KNIGHT_TOP_L_MANGLED),
            locked(Items.ELITE_VOID_KNIGHT_TOP, Items.ELITE_VOID_TOP_L, Items.ELITE_VOID_TOP_L_BROKEN, Items.ELITE_VOID_TOP_L_MANGLED),
            locked(Items.VOID_KNIGHT_ROBE, Items.VOID_KNIGHT_ROBE_L, Items.VOID_KNIGHT_ROBE_L_BROKEN, Items.VOID_KNIGHT_ROBE_L_MANGLED),
            locked(Items.ELITE_VOID_KNIGHT_ROBE, Items.ELITE_VOID_ROBE_L, Items.ELITE_VOID_ROBE_L_BROKEN, Items.ELITE_VOID_ROBE_L_MANGLED),
            locked(Items.VOID_KNIGHT_GLOVES, Items.VOID_KNIGHT_GLOVES_L, Items.VOID_KNIGHT_GLOVES_L_BROKEN, Items.VOID_KNIGHT_GLOVES_L_MANGLED),
            locked(Items.VOID_MAGE_HELM, Items.VOID_MAGE_HELM_L, Items.VOID_MAGE_HELM_L_BROKEN, Items.VOID_MAGE_HELM_L_MANGLED),
            locked(Items.VOID_RANGER_HELM, Items.VOID_RANGER_HELM_L, Items.VOID_RANGER_HELM_L_BROKEN, Items.VOID_RANGER_HELM_L_MANGLED),
            locked(Items.VOID_MELEE_HELM, Items.VOID_MELEE_HELM_L, Items.VOID_MELEE_HELM_L_BROKEN, Items.VOID_MELEE_HELM_L_MANGLED),
            locked(Items.INFERNAL_MAX_CAPE, Items.INFERNAL_MAX_CAPE_L, Items.INFERNAL_MAX_CAPE_L_BROKEN, Items.INFERNAL_MAX_CAPE_L_MANGLED),
        )

    /** The 667 duplicates of a lockable item; each locks as (and unlocks into) the main item. */
    val ALIASES: Map<Int, Int> =
        mapOf(
            Items.VOID_KNIGHT_TOP_10611 to Items.VOID_KNIGHT_TOP,
            Items.VOID_MAGE_HELM_11674 to Items.VOID_MAGE_HELM,
            Items.VOID_RANGER_HELM_11675 to Items.VOID_RANGER_HELM,
            Items.VOID_MELEE_HELM_11676 to Items.VOID_MELEE_HELM,
            Items.ELITE_VOID_KNIGHT_TOP_19787 to Items.ELITE_VOID_KNIGHT_TOP,
            Items.ELITE_VOID_KNIGHT_TOP_19789 to Items.ELITE_VOID_KNIGHT_TOP,
            Items.ELITE_VOID_KNIGHT_ROBE_19788 to Items.ELITE_VOID_KNIGHT_ROBE,
            Items.ELITE_VOID_KNIGHT_ROBE_19790 to Items.ELITE_VOID_KNIGHT_ROBE,
        )

    /** Owner 2026-10-25: defenders below mithril are not lockable; Perdu says why. */
    val LOW_DEFENDERS: Set<Int> = setOf(Items.BRONZE_DEFENDER, Items.IRON_DEFENDER, Items.STEEL_DEFENDER, Items.BLACK_DEFENDER)

    private fun locked(
        base: Int,
        locked: Int,
        broken: Int,
        mangled: Int,
    ) = TrouverLockable(baseItemId = base, lockedItemId = locked, brokenItemId = broken, mangledItemId = mangled)

    /** Every id that is part of a lockable pair, in any state. */
    val IDS: Set<Int> = ALL.flatMap { listOfNotNull(it.baseItemId, it.lockedItemId, it.brokenItemId, it.mangledItemId) }.toSet() + ALIASES.keys

    fun registerAll() {
        if (TrouverRegistry.lockableFor(Items.FIRE_CAPE) != null) return
        ALL.forEach(TrouverRegistry::register)
        ALIASES.forEach { (alias, base) -> TrouverRegistry.registerAlias(alias, base) }
    }

    /**
     * N408 (owner 2026-10-24: "zet t voor elk item goed melding in chatbox perdu"): Perdu's answer when [itemId] cannot be locked,
     * or null when it can. [name] is the item's name, [tradeable] its items.yml flag.
     */
    fun refusal(
        itemId: Int,
        name: String,
        tradeable: Boolean,
    ): String? =
        when {
            TrouverRegistry.lockableFor(itemId) != null -> null
            TrouverRegistry.isLocked(itemId) -> "Your $name is already locked. Use it on me if you want it unlocked."
            TrouverRegistry.isLockedAnyState(itemId) -> "That's damaged - use it on me and I'll repair it first."
            itemId in LOW_DEFENDERS -> "I only lock defenders from mithril up. ${withArticle(name.lowercase())} isn't worth a Trouver parchment."
            itemId == Items.TROUVER_PARCHMENT -> "Use the item you want locked on me, with the parchment and ${"%,d".format(Trouver.LOCK_FEE)} coins in your pack."
            tradeable -> "I can't lock that. Only untradeable items can be locked - you can still trade your ${name.lowercase()}."
            else -> "I can't lock that. $LOCKABLE_SUMMARY"
        }

    /** "An iron defender", "A bronze defender". */
    private fun withArticle(name: String): String = (if (name.isNotEmpty() && name[0].lowercaseChar() in "aeiou") "An " else "A ") + name

    const val LOCKABLE_SUMMARY =
        "I lock defenders from mithril up, Void Knight equipment, fighter torsos, fire and infernal capes, " +
            "imbued god capes, max capes, assemblers, Dizana's quivers, ancient sceptres and rune pouches."
}

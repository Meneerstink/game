package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * Cosmetic OSRS ornament and colour kits imported by OSRS-IMPORT. One table drives the combine
 * (`CombinationData`), the "Dismantle" action (returns base item + kit, OSRS Wiki item pages) and the
 * PvP death conversion ("Items Kept on Death": an ornamented tradeable item is dropped to the PKer as
 * the non-ornamented item together with its tradeable ornament kit).
 *
 * [Ornament.pvpConvert] is false for kits that are not tradeable (the Bounty Hunter elder chaos and Dagon'hai kits): "Items Kept
 * on Death" gives no rule for a tradeable item carrying an untradeable kit (SOURCE_GAP), so those pieces keep the default death
 * handling.
 */
object OsrsOrnamentKits {
    data class Ornament(
        val ornamented: Int,
        val base: Int,
        val kit: Int,
        val pvpConvert: Boolean = true,
    )

    val ALL =
        listOf(
            Ornament(Items.OCCULT_NECKLACE_OR, Items.OCCULT_NECKLACE, Items.OCCULT_ORNAMENT_KIT),
            Ornament(Items.NECKLACE_OF_ANGUISH_OR, Items.NECKLACE_OF_ANGUISH, Items.ANGUISH_ORNAMENT_KIT),
            Ornament(Items.AMULET_OF_TORTURE_OR, Items.AMULET_OF_TORTURE, Items.TORTURE_ORNAMENT_KIT),
            Ornament(Items.TORMENTED_BRACELET_OR, Items.TORMENTED_BRACELET, Items.TORMENTED_ORNAMENT_KIT),
            // magearmour: "It can be attached to infinity hat, top and bottoms"; "The kit can be detached safely, receiving both
            // items" (OSRS Wiki light/dark infinity colour kit, both kits tradeable).
            Ornament(Items.LIGHT_INFINITY_HAT, Items.INFINITY_HAT, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.LIGHT_INFINITY_TOP, Items.INFINITY_TOP, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.LIGHT_INFINITY_BOTTOMS, Items.INFINITY_BOTTOMS, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_HAT, Items.INFINITY_HAT, Items.DARK_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_TOP, Items.INFINITY_TOP, Items.DARK_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_BOTTOMS, Items.INFINITY_BOTTOMS, Items.DARK_INFINITY_COLOUR_KIT),
            // "combined with any piece of the ancestral robes set"; "can be detached ... returning both items" (tradeable kit).
            Ornament(Items.TWISTED_ANCESTRAL_HAT, Items.ANCESTRAL_HAT, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            Ornament(Items.TWISTED_ANCESTRAL_ROBE_TOP, Items.ANCESTRAL_ROBE_TOP, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            Ornament(Items.TWISTED_ANCESTRAL_ROBE_BOTTOM, Items.ANCESTRAL_ROBE_BOTTOM, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            // "It can be dismantled at any time, returning both parts"; kit untradeable (Bounty Hunter).
            Ornament(Items.ELDER_CHAOS_TOP_OR, Items.ELDER_CHAOS_TOP, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.ELDER_CHAOS_ROBE_OR, Items.ELDER_CHAOS_ROBE, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.ELDER_CHAOS_HOOD_OR, Items.ELDER_CHAOS_HOOD, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // "the kit could be detached from the item"; kit untradeable (Bounty Hunter). Base robes are the 667 Dagon'hai robes.
            Ornament(Items.DAGONHAI_HAT_OR, Items.DAGONHAI_HAT, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.DAGONHAI_ROBE_TOP_OR, Items.DAGONHAI_ROBE_TOP, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // Heavy ballista (or): "It can be dismantled at any time, returning both parts"; untradeable Bounty Hunter kit (no PvP rule).
            Ornament(Items.HEAVY_BALLISTA_OR, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_ORNAMENT_KIT, pvpConvert = false),
            // Blazing blowpipe (empty): tradeable Trailblazer reloaded kit on the emptied Toxic blowpipe; "reverted anytime".
            Ornament(Items.BLAZING_BLOWPIPE_EMPTY, Items.TOXIC_BLOWPIPE_EMPTY, Items.BLOWPIPE_ORNAMENT_KIT, pvpConvert = true),
            // Nightmare staff orbs (OSRS Wiki orb staff pages): the untradeable staff "can be reverted to its tradeable components at any
            // time" (Dismantle) and "If lost on death in the Wilderness, the killer will receive the staff and the orb" - the same base +
            // attachment behaviour as a tradeable ornament kit.
            Ornament(Items.HARMONISED_NIGHTMARE_STAFF, Items.NIGHTMARE_STAFF, Items.HARMONISED_ORB, pvpConvert = true),
            Ornament(Items.VOLATILE_NIGHTMARE_STAFF, Items.NIGHTMARE_STAFF, Items.VOLATILE_ORB, pvpConvert = true),
            Ornament(Items.ELDRITCH_NIGHTMARE_STAFF, Items.NIGHTMARE_STAFF, Items.ELDRITCH_ORB, pvpConvert = true),
            Ornament(Items.DAGONHAI_ROBE_BOTTOM_OR, Items.DAGONHAI_ROBE_BOTTOM, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // magegeara: "Elidinis' ward (f)" + "Menaphite ornament kit" (both untradeable); "Dismantle" separates them; on a PvP death
            // "the Menaphite ornament kit will be placed in their gravestone" (no base + kit drop to the PKer).
            Ornament(Items.ELIDINIS_WARD_OR, Items.ELIDINIS_WARD_F, Items.MENAPHITE_ORNAMENT_KIT, pvpConvert = false),
            // casket-ornaments (OSRS Wiki kit and ornamented item pages): tradeable elite / master kits; the ornamented item is untradeable
            // and "can be dismantled anytime, returning the tradeable [base] and ornament kit". The legs/skirt kit fits both leg pieces.
            Ornament(Items.ARMADYL_GODSWORD_OR, Items.ARMADYL_GODSWORD, Items.ARMADYL_GODSWORD_ORNAMENT_KIT),
            Ornament(Items.BANDOS_GODSWORD_OR, Items.BANDOS_GODSWORD, Items.BANDOS_GODSWORD_ORNAMENT_KIT),
            Ornament(Items.SARADOMIN_GODSWORD_OR, Items.SARADOMIN_GODSWORD, Items.SARADOMIN_GODSWORD_ORNAMENT_KIT),
            Ornament(Items.ZAMORAK_GODSWORD_OR, Items.ZAMORAK_GODSWORD, Items.ZAMORAK_GODSWORD_ORNAMENT_KIT),
            Ornament(Items.DRAGON_CHAINBODY_G, Items.DRAGON_CHAINBODY, Items.DRAGON_CHAINBODY_ORNAMENT_KIT),
            Ornament(Items.DRAGON_PLATELEGS_G, Items.DRAGON_PLATELEGS, Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT),
            Ornament(Items.DRAGON_PLATESKIRT_G, Items.DRAGON_PLATESKIRT, Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT),
            Ornament(Items.DRAGON_FULL_HELM_G, Items.DRAGON_FULL_HELM, Items.DRAGON_FULL_HELM_ORNAMENT_KIT),
            Ornament(Items.DRAGON_SQ_SHIELD_G, Items.DRAGON_SQ_SHIELD, Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT),
            Ornament(Items.DRAGON_KITESHIELD_G, Items.DRAGON_KITESHIELD, Items.DRAGON_KITESHIELD_ORNAMENT_KIT),
            Ornament(Items.DRAGON_PLATEBODY_G, Items.DRAGON_PLATEBODY, Items.DRAGON_PLATEBODY_ORNAMENT_KIT),
            Ornament(Items.DRAGON_SCIMITAR_OR, Items.DRAGON_SCIMITAR, Items.DRAGON_SCIMITAR_ORNAMENT_KIT),
            // Dragon defender (t): on a PvP death "it will remain in the player's inventory, but will become broken" (repair at Perdu) -
            // the defender rule, not the base + kit drop. SOURCE_GAP / ADJACENT: no defender breaks on death here, so default handling.
            Ornament(Items.DRAGON_DEFENDER_T, Items.DRAGON_DEFENDER, Items.DRAGON_DEFENDER_ORNAMENT_KIT, pvpConvert = false),
            // OSRS import run 2026-09-17, batch "kits" (item pages): tradeable kits; "it can be dismantled anytime, returning the tradeable
            // [base] and ornament kit" (Dragon boots (g), Berserker necklace (or), Tzhaar-ket-om (t), Rune scimitar (guthix/saradomin/zamorak)),
            // "The trimmed defender can be dismantled anytime, returning the defender and the tradeable ornament kit" (Rune defender (t)).
            Ornament(Items.DRAGON_BOOTS_G, Items.DRAGON_BOOTS, Items.DRAGON_BOOTS_ORNAMENT_KIT),
            Ornament(Items.BERSERKER_NECKLACE_OR, Items.BERSERKER_NECKLACE, Items.BERSERKER_NECKLACE_ORNAMENT_KIT),
            Ornament(Items.RUNE_DEFENDER_T, Items.RUNE_DEFENDER, Items.RUNE_DEFENDER_ORNAMENT_KIT),
            Ornament(Items.TZHAAR_KET_OM_T, Items.TZHAARKETOM, Items.TZHAAR_KET_OM_ORNAMENT_KIT),
            Ornament(Items.RUNE_SCIMITAR_GUTHIX, Items.RUNE_SCIMITAR, Items.RUNE_SCIMITAR_ORNAMENT_KIT_GUTHIX),
            Ornament(Items.RUNE_SCIMITAR_SARADOMIN, Items.RUNE_SCIMITAR, Items.RUNE_SCIMITAR_ORNAMENT_KIT_SARADOMIN),
            Ornament(Items.RUNE_SCIMITAR_ZAMORAK, Items.RUNE_SCIMITAR, Items.RUNE_SCIMITAR_ORNAMENT_KIT_ZAMORAK),
        )

    /**
     * Kits that are used up (OSRS Wiki item pages, 2026-09-17): the ornamented item "can be reverted to the tradeable abyssal whip by using a
     * cleaning cloth on it. However, the frozen/volcanic whip mix will not be returned"; the (or) staves "can be reverted anytime, returning
     * the [staff]. However, the kit will not be returned" (Revert option). No PvP drop rule is stated for these untradeable items (SOURCE_GAP:
     * the whips' page only says they "will be lost upon death"), so they keep the default death handling.
     */
    data class ConsumedKit(
        val ornamented: Int,
        val base: Int,
        val kit: Int,
        /** True when the revert is a cleaning cloth used on the item; false for the Revert option. */
        val cleaningCloth: Boolean,
        /** True when reverting also returns the kit (Zalcano shard: "it can be reverted anytime, returning the dragon pickaxe and Zalcano shard"). */
        val returnsKit: Boolean = false,
    )

    val CONSUMED =
        listOf(
            ConsumedKit(Items.FROZEN_ABYSSAL_WHIP, Items.ABYSSAL_WHIP, Items.FROZEN_WHIP_MIX, cleaningCloth = true),
            ConsumedKit(Items.VOLCANIC_ABYSSAL_WHIP, Items.ABYSSAL_WHIP, Items.VOLCANIC_WHIP_MIX, cleaningCloth = true),
            ConsumedKit(Items.LAVA_BATTLESTAFF_OR, Items.LAVA_BATTLESTAFF, Items.LAVA_STAFF_UPGRADE_KIT, cleaningCloth = false),
            ConsumedKit(Items.STEAM_BATTLESTAFF_OR, Items.STEAM_BATTLESTAFF, Items.STEAM_STAFF_UPGRADE_KIT, cleaningCloth = false),
            ConsumedKit(Items.MYSTIC_STEAM_STAFF_OR, Items.MYSTIC_STEAM_STAFF, Items.STEAM_STAFF_UPGRADE_KIT, cleaningCloth = false),
            // Batch kits2: dark bow paint - "players must use a cleaning cloth on the bow to return to its regular state (the paint will be lost)";
            // Dragon pickaxe upgrade kit - "The upgrade kit will not be returned upon reverting"; Zalcano shard - Revert returns both parts.
            ConsumedKit(Items.DARK_BOW_GREEN, Items.DARK_BOW, Items.GREEN_DARK_BOW_PAINT, cleaningCloth = true),
            ConsumedKit(Items.DARK_BOW_BLUE, Items.DARK_BOW, Items.BLUE_DARK_BOW_PAINT, cleaningCloth = true),
            ConsumedKit(Items.DARK_BOW_YELLOW, Items.DARK_BOW, Items.YELLOW_DARK_BOW_PAINT, cleaningCloth = true),
            ConsumedKit(Items.DARK_BOW_WHITE, Items.DARK_BOW, Items.WHITE_DARK_BOW_PAINT, cleaningCloth = true),
            ConsumedKit(Items.DRAGON_PICKAXE_OR_UPGRADED, Items.DRAGON_PICKAXE, Items.DRAGON_PICKAXE_UPGRADE_KIT, cleaningCloth = false),
            ConsumedKit(Items.DRAGON_PICKAXE_OR, Items.DRAGON_PICKAXE, Items.ZALCANO_SHARD, cleaningCloth = false, returnsKit = true),
        )

    private val byOrnamented = ALL.associateBy { it.ornamented }

    fun forOrnamented(itemId: Int): Ornament? = byOrnamented[itemId]

    /** Ornaments whose PvP death drops the base item and the kit to the killer. */
    fun forPvpConversion(itemId: Int): Ornament? = byOrnamented[itemId]?.takeIf { it.pvpConvert }
}

package gg.rsmod.plugins.content.items.combine

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items

/**
 * Handles all the data relation to combining items and giving experience in the relative skill.
 * @author Kevin Senez <ksenez94@gmail.com>
 */
enum class CombinationData(
    val items: IntArray,
    val tool: CombinationTool = CombinationTool.NONE,
    val resultItem: Int,
    val skill: Int = Skills.CRAFTING,
    val levelRequired: Int = 1,
    val experience: Double,
    val message: String? = null,
) {
    STRANGE_SKULL(
        items = intArrayOf(Items.LEFT_SKULL_HALF, Items.RIGHT_SKULL_HALF),
        resultItem = Items.STRANGE_SKULL,
        levelRequired = 1,
        experience = 0.0,
    ),
    RUNED_SCEPTRE(
        items = intArrayOf(Items.BOTTOM_OF_SCEPTRE, Items.TOP_OF_SCEPTRE),
        resultItem = Items.RUNED_SCEPTRE,
        levelRequired = 1,
        experience = 0.0,
    ),
    SKULL_SCEPTRE(
        items = intArrayOf(Items.STRANGE_SKULL, Items.RUNED_SCEPTRE),
        resultItem = Items.SKULL_SCEPTRE,
        levelRequired = 1,
        experience = 0.0,
    ),
    BULLYEYE_LANTERN(
        items = intArrayOf(Items.BULLSEYE_LANTERN, Items.LANTERN_LENS),
        resultItem = Items.BULLSEYE_LANTERN_4546,
        levelRequired = 49,
        experience = 0.0,
    ),
    BULLYEYE_EMERALD_LANTERN(
        items = intArrayOf(Items.BULLSEYE_LANTERN_4546, Items.EMERALD_LENS),
        resultItem = Items.EMERALD_LANTERN,
        levelRequired = 49,
        experience = 0.0,
    ),

    OIL_LANTERN(
        items = intArrayOf(Items.OIL_LAMP, Items.OIL_LANTERN_FRAME),
        resultItem = Items.OIL_LANTERN_4537,
        levelRequired = 50,
        experience = 50.0,
    ),

    STUDDED_BODY(
        items = intArrayOf(Items.LEATHER_BODY, Items.STEEL_STUDS),
        resultItem = Items.STUDDED_BODY,
        levelRequired = 41,
        experience = 40.0,
    ),
    STUDDED_CHAPS(
        items = intArrayOf(Items.LEATHER_CHAPS, Items.STEEL_STUDS),
        resultItem = Items.STUDDED_CHAPS,
        levelRequired = 44,
        experience = 42.0,
    ),
    LEATHER_SPIKY_VAMBRACES(
        items = intArrayOf(Items.LEATHER_VAMBRACES, Items.KEBBIT_CLAWS),
        resultItem = Items.SPIKY_VAMBRACES,
        levelRequired = 32,
        experience = 6.0,
    ),
    GREEN_SPIKY_VAMBRACES(
        items = intArrayOf(Items.GREEN_DHIDE_VAMBRACES, Items.KEBBIT_CLAWS),
        resultItem = Items.GREEN_SPIKY_VAMBRACES,
        levelRequired = 32,
        experience = 6.0,
    ),
    BLUE_SPIKY_VAMBRACES(
        items = intArrayOf(Items.BLUE_DHIDE_VAMBRACES, Items.KEBBIT_CLAWS),
        resultItem = Items.BLUE_SPIKY_VAMBRACES,
        levelRequired = 32,
        experience = 6.0,
    ),
    RED_SPIKY_VAMBRACES(
        items = intArrayOf(Items.RED_DHIDE_VAMBRACES, Items.KEBBIT_CLAWS),
        resultItem = Items.RED_SPIKY_VAMBRACES,
        levelRequired = 32,
        experience = 6.0,
    ),
    BLACK_SPIKY_VAMBRACES(
        items = intArrayOf(Items.BLACK_DHIDE_VAMBRACES, Items.KEBBIT_CLAWS),
        resultItem = Items.BLACK_SPIKY_VAMBRACES,
        levelRequired = 32,
        experience = 6.0,
    ),
    CRAB_HELMET(
        items = intArrayOf(Items.FRESH_CRAB_SHELL),
        tool = CombinationTool.CHISEL,
        resultItem = Items.CRAB_HELMET,
        levelRequired = 15,
        experience = 32.5,
    ),
    CRAB_CLAW(
        items = intArrayOf(Items.FRESH_CRAB_CLAW),
        tool = CombinationTool.CHISEL,
        resultItem = Items.CRAB_CLAW,
        levelRequired = 15,
        experience = 32.5,
    ),
    SEAWEED_NET(
        items = intArrayOf(Items.UNFINISHED_NET, Items.BRONZE_WIRE),
        resultItem = Items.EMPTY_SEAWEED_NET,
        levelRequired = 52,
        experience = 83.0,
    ),
    ARMADYL_BATTLESTAFF(
        items = intArrayOf(Items.BATTLESTAFF, Items.ORB_OF_ARMADYL),
        resultItem = Items.ARMADYL_BATTLESTAFF,
        levelRequired = 77,
        experience = 150.0,
    ),
    GOLD_AMULET(
        items = intArrayOf(Items.GOLD_AMULET, Items.BALL_OF_WOOL),
        resultItem = Items.GOLD_AMULET_1692,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    SAPPHIRE_AMULET(
        items = intArrayOf(Items.SAPPHIRE_AMULET, Items.BALL_OF_WOOL),
        resultItem = Items.SAPPHIRE_AMULET_1694,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    EMERALD_AMULET(
        items = intArrayOf(Items.EMERALD_AMULET, Items.BALL_OF_WOOL),
        resultItem = Items.EMERALD_AMULET_1696,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    RUBY_AMULET(
        items = intArrayOf(Items.RUBY_AMULET, Items.BALL_OF_WOOL),
        resultItem = Items.RUBY_AMULET_1698,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    DIAMOND_AMULET(
        items = intArrayOf(Items.DIAMOND_AMULET, Items.BALL_OF_WOOL),
        resultItem = Items.DIAMOND_AMULET_1700,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    DRAGONSTONE_AMULET(
        items = intArrayOf(Items.DRAGONSTONE_AMMY, Items.BALL_OF_WOOL),
        resultItem = Items.DRAGONSTONE_AMMY_1702,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    ONYX_AMULET(
        items = intArrayOf(Items.ONYX_AMULET_6579, Items.BALL_OF_WOOL),
        resultItem = Items.ONYX_AMULET_6581,
        experience = 4.0,
        message = "You put some string on your amulet.",
    ),
    UNBLESSED_SYMBOL(
        items = intArrayOf(Items.UNSTRUNG_SYMBOL, Items.BALL_OF_WOOL),
        resultItem = Items.UNBLESSED_SYMBOL,
        experience = 4.0,
        message = "You put some string on your symbol.",
    ),
    UNPOWERED_SYMBOL(
        items = intArrayOf(Items.UNSTRUNG_EMBLEM, Items.BALL_OF_WOOL),
        resultItem = Items.UNPOWERED_SYMBOL,
        experience = 4.0,
        message = "You put some string on your emblem.",
    ),

    /*
     * Godsword assembly. The hilt is deliberately items[0] of each of the four entries: this enum
     * is indexed by that first item, so keying them on the shared Godsword blade would collapse all
     * four into one binding. No skill or level is involved - the requirement lives on the blade,
     * which has to be smithed from the three shards first.
     *
     * The inverse action lives in content/items/godsword.plugin.kts; the two must stay in step, or
     * "Dismantle" becomes a one-way item sink.
     */
    ARMADYL_GODSWORD(
        items = intArrayOf(Items.ARMADYL_HILT, Items.GODSWORD_BLADE),
        resultItem = Items.ARMADYL_GODSWORD,
        experience = 0.0,
        message = "You attach the hilt to the godsword blade.",
    ),
    BANDOS_GODSWORD(
        items = intArrayOf(Items.BANDOS_HILT, Items.GODSWORD_BLADE),
        resultItem = Items.BANDOS_GODSWORD,
        experience = 0.0,
        message = "You attach the hilt to the godsword blade.",
    ),
    SARADOMIN_GODSWORD(
        items = intArrayOf(Items.SARADOMIN_HILT, Items.GODSWORD_BLADE),
        resultItem = Items.SARADOMIN_GODSWORD,
        experience = 0.0,
        message = "You attach the hilt to the godsword blade.",
    ),
    ZAMORAK_GODSWORD(
        items = intArrayOf(Items.ZAMORAK_HILT, Items.GODSWORD_BLADE),
        resultItem = Items.ZAMORAK_GODSWORD,
        experience = 0.0,
        message = "You attach the hilt to the godsword blade.",
    ),
    // OSRS-IMPORT: OSRS Wiki "Ancient godsword" - made by combining the Godsword blade with the Ancient hilt.
    ANCIENT_GODSWORD(
        items = intArrayOf(Items.ANCIENT_HILT, Items.GODSWORD_BLADE),
        resultItem = Items.ANCIENT_GODSWORD,
        experience = 0.0,
        message = "You attach the hilt to the godsword blade.",
    ),

    /** OSRS-IMPORT pilot: Dragon defender + Avernic defender hilt (OSRS Wiki, no skill requirement). */
    AVERNIC_DEFENDER(
        items = intArrayOf(Items.AVERNIC_DEFENDER_HILT, Items.DRAGON_DEFENDER),
        resultItem = Items.AVERNIC_DEFENDER,
        experience = 0.0,
    ),

    /** OSRS-IMPORT pilot: cosmetic Occult ornament kit; "Dismantle" on the (or) returns both parts. */
    OCCULT_NECKLACE_OR(
        items = intArrayOf(Items.OCCULT_ORNAMENT_KIT, Items.OCCULT_NECKLACE),
        resultItem = Items.OCCULT_NECKLACE_OR,
        experience = 0.0,
    ),
    NECKLACE_OF_ANGUISH_OR(
        items = intArrayOf(Items.ANGUISH_ORNAMENT_KIT, Items.NECKLACE_OF_ANGUISH),
        resultItem = Items.NECKLACE_OF_ANGUISH_OR,
        experience = 0.0,
    ),
    AMULET_OF_TORTURE_OR(
        items = intArrayOf(Items.TORTURE_ORNAMENT_KIT, Items.AMULET_OF_TORTURE),
        resultItem = Items.AMULET_OF_TORTURE_OR,
        experience = 0.0,
    ),
    TORMENTED_BRACELET_OR(
        items = intArrayOf(Items.TORMENTED_ORNAMENT_KIT, Items.TORMENTED_BRACELET),
        resultItem = Items.TORMENTED_BRACELET_OR,
        experience = 0.0,
    ),

    /** OSRS-IMPORT magearmour: light/dark infinity, twisted ancestral, elder chaos (or) and Dagon'hai (or) kits (OsrsOrnamentKits). */
    LIGHT_INFINITY_HAT(items = intArrayOf(Items.LIGHT_INFINITY_COLOUR_KIT, Items.INFINITY_HAT), resultItem = Items.LIGHT_INFINITY_HAT, experience = 0.0),
    LIGHT_INFINITY_TOP(items = intArrayOf(Items.LIGHT_INFINITY_COLOUR_KIT, Items.INFINITY_TOP), resultItem = Items.LIGHT_INFINITY_TOP, experience = 0.0),
    LIGHT_INFINITY_BOTTOMS(items = intArrayOf(Items.LIGHT_INFINITY_COLOUR_KIT, Items.INFINITY_BOTTOMS), resultItem = Items.LIGHT_INFINITY_BOTTOMS, experience = 0.0),
    DARK_INFINITY_HAT(items = intArrayOf(Items.DARK_INFINITY_COLOUR_KIT, Items.INFINITY_HAT), resultItem = Items.DARK_INFINITY_HAT, experience = 0.0),
    DARK_INFINITY_TOP(items = intArrayOf(Items.DARK_INFINITY_COLOUR_KIT, Items.INFINITY_TOP), resultItem = Items.DARK_INFINITY_TOP, experience = 0.0),
    DARK_INFINITY_BOTTOMS(items = intArrayOf(Items.DARK_INFINITY_COLOUR_KIT, Items.INFINITY_BOTTOMS), resultItem = Items.DARK_INFINITY_BOTTOMS, experience = 0.0),
    TWISTED_ANCESTRAL_HAT(items = intArrayOf(Items.TWISTED_ANCESTRAL_COLOUR_KIT, Items.ANCESTRAL_HAT), resultItem = Items.TWISTED_ANCESTRAL_HAT, experience = 0.0),
    TWISTED_ANCESTRAL_ROBE_TOP(items = intArrayOf(Items.TWISTED_ANCESTRAL_COLOUR_KIT, Items.ANCESTRAL_ROBE_TOP), resultItem = Items.TWISTED_ANCESTRAL_ROBE_TOP, experience = 0.0),
    TWISTED_ANCESTRAL_ROBE_BOTTOM(items = intArrayOf(Items.TWISTED_ANCESTRAL_COLOUR_KIT, Items.ANCESTRAL_ROBE_BOTTOM), resultItem = Items.TWISTED_ANCESTRAL_ROBE_BOTTOM, experience = 0.0),
    ELDER_CHAOS_TOP_OR(items = intArrayOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.ELDER_CHAOS_TOP), resultItem = Items.ELDER_CHAOS_TOP_OR, experience = 0.0),
    ELDER_CHAOS_ROBE_OR(items = intArrayOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.ELDER_CHAOS_ROBE), resultItem = Items.ELDER_CHAOS_ROBE_OR, experience = 0.0),
    ELDER_CHAOS_HOOD_OR(items = intArrayOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, Items.ELDER_CHAOS_HOOD), resultItem = Items.ELDER_CHAOS_HOOD_OR, experience = 0.0),
    DAGONHAI_HAT_OR(items = intArrayOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.DAGONHAI_HAT), resultItem = Items.DAGONHAI_HAT_OR, experience = 0.0),
    DAGONHAI_ROBE_TOP_OR(items = intArrayOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.DAGONHAI_ROBE_TOP), resultItem = Items.DAGONHAI_ROBE_TOP_OR, experience = 0.0),
    HEAVY_BALLISTA_OR(items = intArrayOf(Items.HEAVY_BALLISTA_ORNAMENT_KIT, Items.HEAVY_BALLISTA), resultItem = Items.HEAVY_BALLISTA_OR, experience = 0.0),
    BLAZING_BLOWPIPE_EMPTY(items = intArrayOf(Items.BLOWPIPE_ORNAMENT_KIT, Items.TOXIC_BLOWPIPE_EMPTY), resultItem = Items.BLAZING_BLOWPIPE_EMPTY, experience = 0.0),
    HARMONISED_NIGHTMARE_STAFF(items = intArrayOf(Items.HARMONISED_ORB, Items.NIGHTMARE_STAFF), resultItem = Items.HARMONISED_NIGHTMARE_STAFF, experience = 0.0),
    VOLATILE_NIGHTMARE_STAFF(items = intArrayOf(Items.VOLATILE_ORB, Items.NIGHTMARE_STAFF), resultItem = Items.VOLATILE_NIGHTMARE_STAFF, experience = 0.0),
    ELDRITCH_NIGHTMARE_STAFF(items = intArrayOf(Items.ELDRITCH_ORB, Items.NIGHTMARE_STAFF), resultItem = Items.ELDRITCH_NIGHTMARE_STAFF, experience = 0.0),
    DAGONHAI_ROBE_BOTTOM_OR(items = intArrayOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT, Items.DAGONHAI_ROBE_BOTTOM), resultItem = Items.DAGONHAI_ROBE_BOTTOM_OR, experience = 0.0),

    /**
     * OSRS-IMPORT magegeara. Kodai wand: "created by using a Kodai insignia ... on a master wand", "cannot be reverted" (no skill
     * stated). Ward upgrade kit: creates the ward (or), "It can be reverted, but the kit will not be returned". Menaphite ornament
     * kit on Elidinis' ward (f) (Dismantle in OsrsOrnamentKits).
     */
    KODAI_WAND(items = intArrayOf(Items.KODAI_INSIGNIA, Items.MASTER_WAND), resultItem = Items.KODAI_WAND, experience = 0.0),
    MALEDICTION_WARD_OR(items = intArrayOf(Items.WARD_UPGRADE_KIT, Items.MALEDICTION_WARD), resultItem = Items.MALEDICTION_WARD_OR, experience = 0.0),
    ODIUM_WARD_OR(items = intArrayOf(Items.WARD_UPGRADE_KIT, Items.ODIUM_WARD), resultItem = Items.ODIUM_WARD_OR, experience = 0.0),
    ELIDINIS_WARD_OR(items = intArrayOf(Items.MENAPHITE_ORNAMENT_KIT, Items.ELIDINIS_WARD_F), resultItem = Items.ELIDINIS_WARD_OR, experience = 0.0),

    /** OSRS-IMPORT casket-ornaments: elite / master ornament kits attached to their base item (OsrsOrnamentKits; no skill stated). */
    ARMADYL_GODSWORD_OR(items = intArrayOf(Items.ARMADYL_GODSWORD_ORNAMENT_KIT, Items.ARMADYL_GODSWORD), resultItem = Items.ARMADYL_GODSWORD_OR, experience = 0.0),
    BANDOS_GODSWORD_OR(items = intArrayOf(Items.BANDOS_GODSWORD_ORNAMENT_KIT, Items.BANDOS_GODSWORD), resultItem = Items.BANDOS_GODSWORD_OR, experience = 0.0),
    SARADOMIN_GODSWORD_OR(items = intArrayOf(Items.SARADOMIN_GODSWORD_ORNAMENT_KIT, Items.SARADOMIN_GODSWORD), resultItem = Items.SARADOMIN_GODSWORD_OR, experience = 0.0),
    ZAMORAK_GODSWORD_OR(items = intArrayOf(Items.ZAMORAK_GODSWORD_ORNAMENT_KIT, Items.ZAMORAK_GODSWORD), resultItem = Items.ZAMORAK_GODSWORD_OR, experience = 0.0),
    DRAGON_CHAINBODY_G(items = intArrayOf(Items.DRAGON_CHAINBODY_ORNAMENT_KIT, Items.DRAGON_CHAINBODY), resultItem = Items.DRAGON_CHAINBODY_G, experience = 0.0),
    DRAGON_PLATELEGS_G(items = intArrayOf(Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT, Items.DRAGON_PLATELEGS), resultItem = Items.DRAGON_PLATELEGS_G, experience = 0.0),
    DRAGON_PLATESKIRT_G(items = intArrayOf(Items.DRAGON_LEGS_SKIRT_ORNAMENT_KIT, Items.DRAGON_PLATESKIRT), resultItem = Items.DRAGON_PLATESKIRT_G, experience = 0.0),
    DRAGON_FULL_HELM_G(items = intArrayOf(Items.DRAGON_FULL_HELM_ORNAMENT_KIT, Items.DRAGON_FULL_HELM), resultItem = Items.DRAGON_FULL_HELM_G, experience = 0.0),
    DRAGON_SQ_SHIELD_G(items = intArrayOf(Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT, Items.DRAGON_SQ_SHIELD), resultItem = Items.DRAGON_SQ_SHIELD_G, experience = 0.0),
    DRAGON_KITESHIELD_G(items = intArrayOf(Items.DRAGON_KITESHIELD_ORNAMENT_KIT, Items.DRAGON_KITESHIELD), resultItem = Items.DRAGON_KITESHIELD_G, experience = 0.0),
    DRAGON_PLATEBODY_G(items = intArrayOf(Items.DRAGON_PLATEBODY_ORNAMENT_KIT, Items.DRAGON_PLATEBODY), resultItem = Items.DRAGON_PLATEBODY_G, experience = 0.0),
    DRAGON_DEFENDER_T(items = intArrayOf(Items.DRAGON_DEFENDER_ORNAMENT_KIT, Items.DRAGON_DEFENDER), resultItem = Items.DRAGON_DEFENDER_T, experience = 0.0),
    DRAGON_SCIMITAR_OR(items = intArrayOf(Items.DRAGON_SCIMITAR_ORNAMENT_KIT, Items.DRAGON_SCIMITAR), resultItem = Items.DRAGON_SCIMITAR_OR, experience = 0.0),

    // OSRS import run 2026-09-17, batch "kits" (OSRS Wiki kit / ornamented item pages; OsrsOrnamentKits).
    DRAGON_BOOTS_G(items = intArrayOf(Items.DRAGON_BOOTS_ORNAMENT_KIT, Items.DRAGON_BOOTS), resultItem = Items.DRAGON_BOOTS_G, experience = 0.0),
    BERSERKER_NECKLACE_OR(items = intArrayOf(Items.BERSERKER_NECKLACE_ORNAMENT_KIT, Items.BERSERKER_NECKLACE), resultItem = Items.BERSERKER_NECKLACE_OR, experience = 0.0),
    RUNE_DEFENDER_T(items = intArrayOf(Items.RUNE_DEFENDER_ORNAMENT_KIT, Items.RUNE_DEFENDER), resultItem = Items.RUNE_DEFENDER_T, experience = 0.0),
    TZHAAR_KET_OM_T(items = intArrayOf(Items.TZHAAR_KET_OM_ORNAMENT_KIT, Items.TZHAARKETOM), resultItem = Items.TZHAAR_KET_OM_T, experience = 0.0),
    RUNE_SCIMITAR_GUTHIX(items = intArrayOf(Items.RUNE_SCIMITAR_ORNAMENT_KIT_GUTHIX, Items.RUNE_SCIMITAR), resultItem = Items.RUNE_SCIMITAR_GUTHIX, experience = 0.0),
    RUNE_SCIMITAR_SARADOMIN(items = intArrayOf(Items.RUNE_SCIMITAR_ORNAMENT_KIT_SARADOMIN, Items.RUNE_SCIMITAR), resultItem = Items.RUNE_SCIMITAR_SARADOMIN, experience = 0.0),
    RUNE_SCIMITAR_ZAMORAK(items = intArrayOf(Items.RUNE_SCIMITAR_ORNAMENT_KIT_ZAMORAK, Items.RUNE_SCIMITAR), resultItem = Items.RUNE_SCIMITAR_ZAMORAK, experience = 0.0),
    FROZEN_ABYSSAL_WHIP(items = intArrayOf(Items.FROZEN_WHIP_MIX, Items.ABYSSAL_WHIP), resultItem = Items.FROZEN_ABYSSAL_WHIP, experience = 0.0),
    VOLCANIC_ABYSSAL_WHIP(items = intArrayOf(Items.VOLCANIC_WHIP_MIX, Items.ABYSSAL_WHIP), resultItem = Items.VOLCANIC_ABYSSAL_WHIP, experience = 0.0),
    LAVA_BATTLESTAFF_OR(items = intArrayOf(Items.LAVA_STAFF_UPGRADE_KIT, Items.LAVA_BATTLESTAFF), resultItem = Items.LAVA_BATTLESTAFF_OR, experience = 0.0),
    STEAM_BATTLESTAFF_OR(items = intArrayOf(Items.STEAM_STAFF_UPGRADE_KIT, Items.STEAM_BATTLESTAFF), resultItem = Items.STEAM_BATTLESTAFF_OR, experience = 0.0),
    MYSTIC_STEAM_STAFF_OR(items = intArrayOf(Items.STEAM_STAFF_UPGRADE_KIT, Items.MYSTIC_STEAM_STAFF), resultItem = Items.MYSTIC_STEAM_STAFF_OR, experience = 0.0),
    DARK_BOW_GREEN(items = intArrayOf(Items.GREEN_DARK_BOW_PAINT, Items.DARK_BOW), resultItem = Items.DARK_BOW_GREEN, experience = 0.0),
    DARK_BOW_BLUE(items = intArrayOf(Items.BLUE_DARK_BOW_PAINT, Items.DARK_BOW), resultItem = Items.DARK_BOW_BLUE, experience = 0.0),
    DARK_BOW_YELLOW(items = intArrayOf(Items.YELLOW_DARK_BOW_PAINT, Items.DARK_BOW), resultItem = Items.DARK_BOW_YELLOW, experience = 0.0),
    DARK_BOW_WHITE(items = intArrayOf(Items.WHITE_DARK_BOW_PAINT, Items.DARK_BOW), resultItem = Items.DARK_BOW_WHITE, experience = 0.0),
    DRAGON_PICKAXE_OR_UPGRADED(items = intArrayOf(Items.DRAGON_PICKAXE_UPGRADE_KIT, Items.DRAGON_PICKAXE), resultItem = Items.DRAGON_PICKAXE_OR_UPGRADED, experience = 0.0),
    DRAGON_PICKAXE_OR(items = intArrayOf(Items.ZALCANO_SHARD, Items.DRAGON_PICKAXE), resultItem = Items.DRAGON_PICKAXE_OR, experience = 0.0),

    /**
     * Revision-667 native ornament kits (night run 2026-09-19 ornament audit: the kits and ornamented items exist in the 667 cache but
     * no route attached them). The 667 ornamented items carry their own cache option "Split" (OsrsOrnamentKits, option "Split"), which
     * returns the item and the kit. The platelegs/skirt kits fit both leg pieces, as their names say.
     */
    AMULET_OF_FURY_OR_667(items = intArrayOf(Items.FURY_ORNAMENT_KIT, Items.AMULET_OF_FURY), resultItem = Items.AMULET_OF_FURY_OR, experience = 0.0),
    DRAGON_FULL_HELM_OR_667(items = intArrayOf(Items.DRAGON_FULL_HELM_ORNAMENT_KIT_OR, Items.DRAGON_FULL_HELM), resultItem = Items.DRAGON_FULL_HELM_OR, experience = 0.0),
    DRAGON_PLATEBODY_OR_667(items = intArrayOf(Items.DRAGON_PLATEBODY_ORNAMENT_KIT_OR, Items.DRAGON_PLATEBODY), resultItem = Items.DRAGON_PLATEBODY_OR, experience = 0.0),
    DRAGON_PLATELEGS_OR_667(items = intArrayOf(Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_OR, Items.DRAGON_PLATELEGS), resultItem = Items.DRAGON_PLATELEGS_OR, experience = 0.0),
    DRAGON_PLATESKIRT_OR_667(items = intArrayOf(Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_OR, Items.DRAGON_PLATESKIRT), resultItem = Items.DRAGON_PLATESKIRT_OR, experience = 0.0),
    DRAGON_SQ_SHIELD_OR_667(items = intArrayOf(Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_OR, Items.DRAGON_SQ_SHIELD), resultItem = Items.DRAGON_SQUARE_SHIELD_OR, experience = 0.0),
    DRAGON_FULL_HELM_SP_667(items = intArrayOf(Items.DRAGON_FULL_HELM_ORNAMENT_KIT_SP, Items.DRAGON_FULL_HELM), resultItem = Items.DRAGON_FULL_HELM_SP, experience = 0.0),
    DRAGON_PLATEBODY_SP_667(items = intArrayOf(Items.DRAGON_PLATEBODY_ORNAMENT_KIT_SP, Items.DRAGON_PLATEBODY), resultItem = Items.DRAGON_PLATEBODY_SP, experience = 0.0),
    DRAGON_PLATELEGS_SP_667(items = intArrayOf(Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_SP, Items.DRAGON_PLATELEGS), resultItem = Items.DRAGON_PLATELEGS_SP, experience = 0.0),
    DRAGON_PLATESKIRT_SP_667(items = intArrayOf(Items.DRAGON_PLATELEGSSKIRT_ORNAMENT_KIT_SP, Items.DRAGON_PLATESKIRT), resultItem = Items.DRAGON_PLATESKIRT_SP, experience = 0.0),
    DRAGON_SQ_SHIELD_SP_667(items = intArrayOf(Items.DRAGON_SQ_SHIELD_ORNAMENT_KIT_SP, Items.DRAGON_SQ_SHIELD), resultItem = Items.DRAGON_SQ_SHIELD_SP, experience = 0.0),

    /** OSRS Wiki "Necklace of rupture": Necklace of anguish + Etched elder venator fang, 84 Crafting, 500 experience. */
    NECKLACE_OF_RUPTURE(items = intArrayOf(Items.ETCHED_ELDER_VENATOR_FANG, Items.NECKLACE_OF_ANGUISH), resultItem = Items.NECKLACE_OF_RUPTURE, levelRequired = 84, experience = 500.0),

    /** OSRS Wiki "Seers icon" / "Archer icon": a chisel on the ring, 80 Crafting, 400 experience. */
    SEERS_ICON(items = intArrayOf(Items.SEERS_RING), tool = CombinationTool.CHISEL, resultItem = Items.SEERS_ICON, levelRequired = 80, experience = 400.0),
    ARCHER_ICON(items = intArrayOf(Items.ARCHERS_RING), tool = CombinationTool.CHISEL, resultItem = Items.ARCHER_ICON, levelRequired = 80, experience = 400.0),

    /** OSRS Wiki "Etched araxyte fang": 86 Crafting, 500 XP, non-reversible (the wiki's confirmation dialog is not ported). */
    AMULET_OF_RANCOUR(
        items = intArrayOf(Items.ETCHED_ARAXYTE_FANG, Items.AMULET_OF_TORTURE),
        resultItem = Items.AMULET_OF_RANCOUR,
        levelRequired = 86,
        experience = 500.0,
        message = "You successfully create an amulet of rancour.",
    ),
    // Heavy ballista is NOT a single 4-item combine (owner live-test 2026-09-16 caught this: it crafted instantly
    // instead of a real 3-step assembly). OSRS Wiki (fetched 2026-09-16): limbs+frame -> Incomplete heavy ballista
    // (30 XP) -> +spring -> Unstrung heavy ballista (30 XP) -> +monkey tail -> Heavy ballista (600 XP), 72 Fletching
    // throughout, 660 XP total. Rebuilt as a 3-step chain in heavy_ballista.plugin.kts once the two intermediate
    // items (Incomplete/Unstrung heavy ballista) are imported - see the "ballista" batch in OsrsItemImportTool.
    PIE_SHELL(
        items = intArrayOf(Items.PASTRY_DOUGH, Items.PIE_DISH),
        resultItem = Items.PIE_SHELL,
        skill = Skills.COOKING,
        experience = 0.0,
        message = "You put the pastry dough into the pie dish to make a pie shell.",
    ),
    ;

    companion object {
        val values = enumValues<CombinationData>()
    }
}

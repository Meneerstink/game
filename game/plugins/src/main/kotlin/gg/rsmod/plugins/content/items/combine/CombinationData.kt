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

    /** OSRS Wiki "Etched araxyte fang": 86 Crafting, 500 XP, non-reversible (the wiki's confirmation dialog is not ported). */
    AMULET_OF_RANCOUR(
        items = intArrayOf(Items.ETCHED_ARAXYTE_FANG, Items.AMULET_OF_TORTURE),
        resultItem = Items.AMULET_OF_RANCOUR,
        levelRequired = 86,
        experience = 500.0,
        message = "You successfully create an amulet of rancour.",
    ),
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
        val combinationDefinitions = values.associateBy { it.items[0] }
    }
}

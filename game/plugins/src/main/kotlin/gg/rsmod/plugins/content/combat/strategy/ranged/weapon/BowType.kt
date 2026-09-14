package gg.rsmod.plugins.content.combat.strategy.ranged.weapon

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.ADAMANT_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BROAD_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRONZE_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_ADAMANT_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_BLACK_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_BRONZE_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_IRON_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_MITHRIL_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_RUNE_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.BRUTAL_STEEL_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.DRAGON_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.IRON_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.MITHRIL_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.OGRE_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.RUNE_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.STEEL_ARROWS
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.TRAINING_ARROWS

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class BowType(
    val item: Int,
    val ammo: Array<Int>,
) {
    TRAINING_BOW(item = Items.TRAINING_BOW, ammo = TRAINING_ARROWS),

    SHORTBOW(item = Items.SHORTBOW, ammo = BRONZE_ARROWS + IRON_ARROWS),
    LONGBOW(item = Items.LONGBOW, ammo = BRONZE_ARROWS + IRON_ARROWS),

    OAK_SHORTBOW(item = Items.OAK_SHORTBOW, ammo = BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS),
    OAK_LONGBOW(item = Items.OAK_LONGBOW, ammo = BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS),

    WILLOW_SHORTBOW(item = Items.WILLOW_SHORTBOW, ammo = BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS),
    WILLOW_COMP_BOW(
        item = Items.WILLOW_COMPOSITE_BOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS,
    ),
    WILLOW_LONGBOW(item = Items.WILLOW_LONGBOW, ammo = BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS),

    MAPLE_SHORTBOW(
        item = Items.MAPLE_SHORTBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS,
    ),
    MAPLE_LONGBOW(
        item = Items.MAPLE_LONGBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS,
    ),

    OGRE_BOW(item = Items.OGRE_BOW, ammo = OGRE_ARROWS),
    COMP_OGRE_BOW(
        item = Items.COMP_OGRE_BOW,
        ammo =
            BRUTAL_BRONZE_ARROWS + BRUTAL_IRON_ARROWS + BRUTAL_STEEL_ARROWS + BRUTAL_BLACK_ARROWS +
                BRUTAL_MITHRIL_ARROWS +
                BRUTAL_ADAMANT_ARROWS +
                BRUTAL_RUNE_ARROWS,
    ),

    YEW_SHORTBOW(
        item = Items.YEW_SHORTBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS,
    ),
    YEW_LONGBOW(
        item = Items.YEW_LONGBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS,
    ),
    YEW_COMP_BOW(
        item = Items.YEW_COMPOSITE_BOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS,
    ),

    // OSRS-IMPORT ammo2: amethyst arrows fire from "any bow made with magic logs or stronger" (OSRS Wiki "Amethyst arrow").
    // Seercull is not listed there and keeps rune arrows (SOURCE_GAP).
    MAGIC_SHORTBOW(
        item = Items.MAGIC_SHORTBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + BROAD_ARROWS +
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    // OSRS-IMPORT Magic shortbow (i): the Magic shortbow with a higher ranged attack, same arrows.
    MAGIC_SHORTBOW_I(
        item = Items.MAGIC_SHORTBOW_I,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + BROAD_ARROWS +
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    MAGIC_LONGBOW(
        item = Items.MAGIC_LONGBOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + BROAD_ARROWS +
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    MAGIC_COMP_BOW(
        item = Items.MAGIC_COMPOSITE_BOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + BROAD_ARROWS +
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),

    SEERCULL(
        item = Items.SEERCULL,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + BROAD_ARROWS,
    ),

    DARK_BOW(
        item = Items.DARK_BOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    BLUE_DARK_BOW(
        item = Items.DARK_BOW_15701,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    GREEN_DARK_BOW(
        item = Items.DARK_BOW_15702,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    WHITE_DARK_BOW(
        item = Items.DARK_BOW_15703,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),
    YELLOW_DARK_BOW(
        item = Items.DARK_BOW_15704,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),

    // S4, 2026-09-03: OSRS Wiki "Twisted bow" - "can fire any type of arrow, including dragon
    // arrows" / comparison table "Uses arrows as ammunition up to and including dragon" - the
    // same bronze-to-dragon-plus-broad tier already used by the Dark bow family above.
    TWISTED_BOW(
        item = Items.TWISTED_BOW,
        ammo =
            BRONZE_ARROWS + IRON_ARROWS + STEEL_ARROWS + MITHRIL_ARROWS + ADAMANT_ARROWS + RUNE_ARROWS + DRAGON_ARROWS +
                BROAD_ARROWS + gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Arrows.AMETHYST_ARROWS,
    ),

    CRYSTAL_BOW_110(item = Items.CRYSTAL_BOW_110, ammo = emptyArray()),
    CRYSTAL_BOW_210(item = Items.CRYSTAL_BOW_210, ammo = emptyArray()),
    CRYSTAL_BOW_310(item = Items.CRYSTAL_BOW_310, ammo = emptyArray()),
    CRYSTAL_BOW_410(item = Items.CRYSTAL_BOW_410, ammo = emptyArray()),
    CRYSTAL_BOW_510(item = Items.CRYSTAL_BOW_510, ammo = emptyArray()),
    CRYSTAL_BOW_610(item = Items.CRYSTAL_BOW_610, ammo = emptyArray()),
    CRYSTAL_BOW_710(item = Items.CRYSTAL_BOW_710, ammo = emptyArray()),
    CRYSTAL_BOW_810(item = Items.CRYSTAL_BOW_810, ammo = emptyArray()),
    CRYSTAL_BOW_910(item = Items.CRYSTAL_BOW_910, ammo = emptyArray()),
    CRYSTAL_BOW_FULL(item = Items.CRYSTAL_BOW_FULL, ammo = emptyArray()),
    CRYSTAL_BOW_NEW(item = Items.NEW_CRYSTAL_BOW, ammo = emptyArray()),

    // OSRS-IMPORT Bow of Faerdhinen: "does not require any arrows to use, as it generates its own when fired".
    BOW_OF_FAERDHINEN(item = Items.BOW_OF_FAERDHINEN, ammo = emptyArray()),
    BOW_OF_FAERDHINEN_INACTIVE(item = Items.BOW_OF_FAERDHINEN_INACTIVE, ammo = emptyArray()),
    BOW_OF_FAERDHINEN_C(item = Items.BOW_OF_FAERDHINEN_C, ammo = emptyArray()),
    SLING(item = Items.SLING, ammo = emptyArray()),

    // OSRS-IMPORT bows: the crystal bow and the revenant bows make their own arrows; Venator bow ("can fire any type of arrow,
    // including dragon arrows") and Scorching bow ("arrows up to dragon arrows") use the Twisted bow tier.
    CRYSTAL_BOW_OSRS(item = Items.CRYSTAL_BOW_OSRS, ammo = emptyArray()),
    CRYSTAL_BOW_OSRS_INACTIVE(item = Items.CRYSTAL_BOW_OSRS_INACTIVE, ammo = emptyArray()),
    CRAWS_BOW_U(item = Items.CRAWS_BOW_U, ammo = emptyArray()),
    CRAWS_BOW(item = Items.CRAWS_BOW, ammo = emptyArray()),
    WEBWEAVER_BOW_U(item = Items.WEBWEAVER_BOW_U, ammo = emptyArray()),
    WEBWEAVER_BOW(item = Items.WEBWEAVER_BOW, ammo = emptyArray()),
    VENATOR_BOW(item = Items.VENATOR_BOW, ammo = TWISTED_BOW.ammo),
    VENATOR_BOW_UNCHARGED(item = Items.VENATOR_BOW_UNCHARGED, ammo = TWISTED_BOW.ammo),
    SCORCHING_BOW(item = Items.SCORCHING_BOW, ammo = TWISTED_BOW.ammo),

    // OSRS-IMPORT moons: "It uses Atlatl darts as ammunition" / "ammunition used exclusively by the eclipse atlatl".
    ECLIPSE_ATLATL(item = Items.ECLIPSE_ATLATL, ammo = arrayOf(Items.ATLATL_DART)),

    ;

    companion object {
        val values = enumValues<BowType>()
    }
}

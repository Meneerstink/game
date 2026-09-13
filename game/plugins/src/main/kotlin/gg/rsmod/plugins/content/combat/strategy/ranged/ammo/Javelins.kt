package gg.rsmod.plugins.content.combat.strategy.ranged.ammo

import gg.rsmod.plugins.api.cfg.Items

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Javelins {
    val BRONZE_JAVELINS = arrayOf(Items.BRONZE_JAVELIN, Items.BRONZE_JAVELIN_P, Items.BRONZE_JAVELIN_P_5642)
    val IRON_JAVELINS =
        arrayOf(Items.IRON_JAVELIN, Items.IRON_JAVELIN_P, Items.IRON_JAVELIN_P_5643, Items.IRON_JAVELIN_P_5649)
    val STEEL_JAVELINS =
        arrayOf(Items.STEEL_JAVELIN, Items.STEEL_JAVELIN_P, Items.STEEL_JAVELIN_P_5644, Items.STEEL_JAVELIN_P_5650)
    val MITHRIL_JAVELINS =
        arrayOf(
            Items.MITHRIL_JAVELIN,
            Items.MITHRIL_JAVELIN_P,
            Items.MITHRIL_JAVELIN_P_5645,
            Items.MITHRIL_JAVELIN_P_5651,
        )
    val ADAMANT_JAVELINS =
        arrayOf(
            Items.ADAMANT_JAVELIN,
            Items.ADAMANT_JAVELIN_P,
            Items.ADAMANT_JAVELIN_P_5646,
            Items.ADAMANT_JAVELIN_P_5652,
        )
    val RUNE_JAVELINS =
        arrayOf(Items.RUNE_JAVELIN, Items.RUNE_JAVELIN_P, Items.RUNE_JAVELIN_P_5647, Items.RUNE_JAVELIN_P_5653)

    /** The 667 thrown javelins (weapon slot). */
    val JAVELINS =
        BRONZE_JAVELINS + IRON_JAVELINS + STEEL_JAVELINS + MITHRIL_JAVELINS + ADAMANT_JAVELINS + RUNE_JAVELINS

    // OSRS-IMPORT ballista: OSRS javelins are ammunition (ammo slot) fired only from a ballista. Separate items from the
    // 667 thrown javelins of the same name; each tier is plain, (p), (p+) and (p++).
    val OSRS_BRONZE_JAVELINS = arrayOf(Items.OSRS_BRONZE_JAVELIN, Items.OSRS_BRONZE_JAVELIN_P, Items.OSRS_BRONZE_JAVELIN_P_PLUS, Items.OSRS_BRONZE_JAVELIN_P_PLUS_PLUS)
    val OSRS_IRON_JAVELINS = arrayOf(Items.OSRS_IRON_JAVELIN, Items.OSRS_IRON_JAVELIN_P, Items.OSRS_IRON_JAVELIN_P_PLUS, Items.OSRS_IRON_JAVELIN_P_PLUS_PLUS)
    val OSRS_STEEL_JAVELINS = arrayOf(Items.OSRS_STEEL_JAVELIN, Items.OSRS_STEEL_JAVELIN_P, Items.OSRS_STEEL_JAVELIN_P_PLUS, Items.OSRS_STEEL_JAVELIN_P_PLUS_PLUS)
    val OSRS_MITHRIL_JAVELINS = arrayOf(Items.OSRS_MITHRIL_JAVELIN, Items.OSRS_MITHRIL_JAVELIN_P, Items.OSRS_MITHRIL_JAVELIN_P_PLUS, Items.OSRS_MITHRIL_JAVELIN_P_PLUS_PLUS)
    val OSRS_ADAMANT_JAVELINS = arrayOf(Items.OSRS_ADAMANT_JAVELIN, Items.OSRS_ADAMANT_JAVELIN_P, Items.OSRS_ADAMANT_JAVELIN_P_PLUS, Items.OSRS_ADAMANT_JAVELIN_P_PLUS_PLUS)
    val OSRS_RUNE_JAVELINS = arrayOf(Items.OSRS_RUNE_JAVELIN, Items.OSRS_RUNE_JAVELIN_P, Items.OSRS_RUNE_JAVELIN_P_PLUS, Items.OSRS_RUNE_JAVELIN_P_PLUS_PLUS)
    val OSRS_AMETHYST_JAVELINS = arrayOf(Items.OSRS_AMETHYST_JAVELIN, Items.OSRS_AMETHYST_JAVELIN_P, Items.OSRS_AMETHYST_JAVELIN_P_PLUS, Items.OSRS_AMETHYST_JAVELIN_P_PLUS_PLUS)
    val OSRS_DRAGON_JAVELINS = arrayOf(Items.OSRS_DRAGON_JAVELIN, Items.OSRS_DRAGON_JAVELIN_P, Items.OSRS_DRAGON_JAVELIN_P_PLUS, Items.OSRS_DRAGON_JAVELIN_P_PLUS_PLUS)

    /** Everything a Heavy ballista fires (OSRS Wiki "Heavy ballista": javelins only). */
    val BALLISTA_JAVELINS: Array<Int>
        get() =
            OSRS_BRONZE_JAVELINS + OSRS_IRON_JAVELINS + OSRS_STEEL_JAVELINS + OSRS_MITHRIL_JAVELINS + OSRS_ADAMANT_JAVELINS +
                OSRS_RUNE_JAVELINS + OSRS_AMETHYST_JAVELINS + OSRS_DRAGON_JAVELINS
}

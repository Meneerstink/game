package gg.rsmod.plugins.content.combat.strategy.ranged.weapon

import gg.rsmod.plugins.api.cfg.Items

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Bows {
    val CRYSTAL_BOWS =
        arrayOf(
            Items.NEW_CRYSTAL_BOW,
            Items.CRYSTAL_BOW_FULL,
            Items.CRYSTAL_BOW_110,
            Items.CRYSTAL_BOW_210,
            Items.CRYSTAL_BOW_310,
            Items.CRYSTAL_BOW_410,
            Items.CRYSTAL_BOW_510,
            Items.CRYSTAL_BOW_610,
            Items.CRYSTAL_BOW_710,
            Items.CRYSTAL_BOW_810,
            Items.CRYSTAL_BOW_910,
            // OSRS-IMPORT Bow of Faerdhinen: attack range 10 (wiki infobox), built-in arrows like the crystal bow.
            Items.BOW_OF_FAERDHINEN,
            Items.BOW_OF_FAERDHINEN_INACTIVE,
            Items.BOW_OF_FAERDHINEN_C,
            Items.CRYSTAL_BOW_OSRS,
            Items.CRYSTAL_BOW_OSRS_INACTIVE,
        )

    val LONG_BOWS =
        arrayOf(
            Items.LONGBOW,
            Items.OAK_LONGBOW,
            Items.MAPLE_LONGBOW,
            Items.WILLOW_LONGBOW,
            Items.YEW_LONGBOW,
            Items.MAGIC_LONGBOW,
            Items.DARK_BOW,
        )
}

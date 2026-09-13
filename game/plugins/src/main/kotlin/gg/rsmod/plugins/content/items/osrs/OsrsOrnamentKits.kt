package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * Cosmetic OSRS ornament kits imported by OSRS-IMPORT. One table drives the combine
 * (`CombinationData`), the "Dismantle" action (returns base item + kit, OSRS Wiki item pages) and the
 * PvP death conversion ("Items Kept on Death": an ornamented tradeable item is dropped to the PKer as
 * the non-ornamented item together with its tradeable ornament kit).
 */
object OsrsOrnamentKits {
    data class Ornament(
        val ornamented: Int,
        val base: Int,
        val kit: Int,
    )

    val ALL =
        listOf(
            Ornament(Items.OCCULT_NECKLACE_OR, Items.OCCULT_NECKLACE, Items.OCCULT_ORNAMENT_KIT),
            Ornament(Items.NECKLACE_OF_ANGUISH_OR, Items.NECKLACE_OF_ANGUISH, Items.ANGUISH_ORNAMENT_KIT),
            Ornament(Items.AMULET_OF_TORTURE_OR, Items.AMULET_OF_TORTURE, Items.TORTURE_ORNAMENT_KIT),
            Ornament(Items.TORMENTED_BRACELET_OR, Items.TORMENTED_BRACELET, Items.TORMENTED_ORNAMENT_KIT),
        )

    private val byOrnamented = ALL.associateBy { it.ornamented }

    fun forOrnamented(itemId: Int): Ornament? = byOrnamented[itemId]
}

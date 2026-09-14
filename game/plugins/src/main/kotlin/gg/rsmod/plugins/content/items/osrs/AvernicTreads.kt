package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT Avernic treads (OSRS Wiki "Avernic treads", 2026-09-14):
 * - "can be upgraded by using a pair of primordial, eternal, and pegasian boots on it. These boots may be added in any order, and it's
 *   entirely possible to only add one or two pair of boots"; "Players must have 80 Magic and 60 Runecraft"; "4,000 demon tears per pair
 *   of boots"; primordial +2 melee strength, pegasian +1 ranged strength, eternal +1 % magic damage (cache params of each variant).
 * - "As soon as at least one upgrade has been added, the treads become untradeable."
 * - "This process is reversible, returning the base avernic treads and all pairs of boots used on it - however, any spent demon tears
 *   will be lost."
 * - "If the boots are lost on PvP death, the base avernic treads and any applied boots will be dropped for the killer and all used
 *   tears will be lost."
 * SOURCE_GAP: no experience is stated for adding boots (none given).
 */
object AvernicTreads {
    const val MAGIC_LEVEL = 80
    const val RUNECRAFT_LEVEL = 60
    const val TEARS_PER_PAIR = 4_000

    enum class Boots(val flag: Int, val item: Int) {
        PRIMORDIAL(1, Items.PRIMORDIAL_BOOTS),
        PEGASIAN(2, Items.PEGASIAN_BOOTS),
        ETERNAL(4, Items.ETERNAL_BOOTS),
    }

    private val byMask =
        mapOf(
            0 to Items.AVERNIC_TREADS,
            1 to Items.AVERNIC_TREADS_PR,
            2 to Items.AVERNIC_TREADS_PE,
            4 to Items.AVERNIC_TREADS_ET,
            3 to Items.AVERNIC_TREADS_PR_PE,
            5 to Items.AVERNIC_TREADS_PR_ET,
            6 to Items.AVERNIC_TREADS_PE_ET,
            7 to Items.AVERNIC_TREADS_MAX,
        )
    private val maskById = byMask.entries.associate { (mask, id) -> id to mask }

    val ALL: Set<Int> = maskById.keys
    val UPGRADED: Set<Int> = ALL - Items.AVERNIC_TREADS

    fun maskOf(treadsId: Int): Int? = maskById[treadsId]

    /** The treads after adding [boots], or null when that pair is already applied or [treadsId] are not treads. */
    fun upgraded(
        treadsId: Int,
        boots: Boots,
    ): Int? {
        val mask = maskOf(treadsId) ?: return null
        if (mask and boots.flag != 0) return null
        return byMask.getValue(mask or boots.flag)
    }

    /** Boots applied to [treadsId]. */
    fun appliedBoots(treadsId: Int): List<Int> {
        val mask = maskOf(treadsId) ?: return emptyList()
        return Boots.values().filter { mask and it.flag != 0 }.map { it.item }
    }
}

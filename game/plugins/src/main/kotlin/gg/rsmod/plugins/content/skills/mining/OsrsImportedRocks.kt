package gg.rsmod.plugins.content.skills.mining

import gg.rsmod.plugins.content.activity.toa.ToaIds

/**
 * N462 07-11 (owner 'dat hele gebied correct integreren net zoals osrs'): the OSRS rocks imported with the Necropolis map have new
 * local ids that [RockType.objectIds] (rev-667 ids) does not know, so they could not be mined or prospected (90-locmap-claude.tsv:
 * Clay 11362 -> 65339, Iron 11364 -> 65340, Coal 11366 -> 65341, Adamantite 11374 -> 65342; 'necropolis-west' adds the Wiki Necropolis
 * "Granite rocks, Sandstone rocks" ENAKH_GRANITE_ROCKS 11387 / ENAKH_SANDSTONE_ROCKS 11386).
 * Depletion (RuneLite gameval ObjectID, Jagex names): the *ROCK1 variants become ROCKS1 11390, the *ROCK2 variants ROCKS2 11391; the
 * Enakh sandstone / granite rocks never deplete (as [RockType.SANDSTONE] / [RockType.GRANITE], respawnDelay -1).
 * Local ids always via [ToaIds] (data/cfg/toa/ids.tsv) - never typed by hand.
 */
object OsrsImportedRocks {
    /** OSRS loc id -> rock type. */
    val TYPES: Map<Int, RockType> = mapOf(
        11362 to RockType.CLAY, 11363 to RockType.CLAY,
        11364 to RockType.IRON, 11365 to RockType.IRON,
        11366 to RockType.COAL, 11367 to RockType.COAL,
        11374 to RockType.ADAMANTITE, 11375 to RockType.ADAMANTITE,
        11386 to RockType.SANDSTONE, 11387 to RockType.GRANITE,
    )

    /** OSRS ROCKS1 / ROCKS2 for the depleting rocks, null for the Enakh rocks. */
    fun depletedOsrs(osrs: Int): Int? = when {
        osrs == 11386 || osrs == 11387 -> null
        (osrs - 11362) % 2 == 0 -> 11390
        else -> 11391
    }

    /** Local id -> rock type, for every imported rock present in this cache. */
    val byLocal: Map<Int, RockType> by lazy { TYPES.mapNotNull { (osrs, type) -> ToaIds.loc(osrs)?.let { it to type } }.toMap() }

    /** Local ids of the imported empty rocks (ROCKS1 / ROCKS2). */
    val depletedLocal: Set<Int> by lazy { listOf(11390, 11391).mapNotNull(ToaIds::loc).toSet() }
}

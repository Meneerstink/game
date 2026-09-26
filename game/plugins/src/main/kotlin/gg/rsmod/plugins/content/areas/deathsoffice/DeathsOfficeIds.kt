package gg.rsmod.plugins.content.areas.deathsoffice

/**
 * Cache ids written by the 2026-09-26 imports (both caches; OSRS_IMPORT_MASTER.yml):
 *  - `OsrsNpcImportTool deaths-office`, tx-20260926-011949: OSRS npcs 9855 Death -> 14479, 9856 Grave -> 14480,
 *    9857 Grave (angel) -> 14481; sequences 8748 -> 15821, 6853 -> 15822, 8749 -> 15823, 8750 -> 15824, 8751 -> 15825,
 *    8752 -> 15826, 7301 -> 15827.
 *  - `OsrsItemImportTool deaths-office-pictures`, tx-20260926-012153: OSRS items 24418 Gravestone -> 23876,
 *    24524 Gravestone -> 23877, 24523 Death's Coffer -> 23878 (display only).
 *  - `DeathsOfficeMapImportTool`, tx-20260926-012959: OSRS locs 39549 Portal -> 62795, 39550 Death's Coffer -> 62796,
 *    entrances 38426 -> 62848, 39546 -> 62849, 39547 -> 62850; map element 1108 (Death's Office icon) on every entrance.
 */
object DeathsOfficeIds {
    const val DEATH = 14479
    const val GRAVE = 14480
    const val GRAVE_ANGEL = 14481

    const val PORTAL = 62795
    const val COFFER = 62796

    const val ENTRANCE_LUMBRIDGE = 62848
    const val ENTRANCE_SEERS = 62849
    const val ENTRANCE_EDGEVILLE = 62850

    /** The Ferox Enclave "Death's domain" (OSRS 39637), imported with the Ferox map (FeroxObjects.DEATHS_DOMAIN). */
    const val ENTRANCE_FEROX = 62430

    /** "Gravestone (unobtainable item, 1)" and "Death's Coffer (unobtainable item)": the tutorial's pictures. */
    const val GRAVESTONE_PICTURE = 23876
    const val COFFER_PICTURE = 23878

    /**
     * The first walkable tile east of the portal (OSRS portal 3169,5726, 2x2): the imported square's tile flags and loc
     * footprints leave 3171,6110 free directly in front of it.
     */
    const val ARRIVAL_X = 3171
    const val ARRIVAL_Z = 6110
}

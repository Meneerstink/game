package gg.rsmod.game.tools.importer

/**
 * N529/N531: OSRS loc map scene (op68) -> 667 msi (op102) and OSRS map area (op82) -> 667 mapelement (op107), every pair
 * pixel-proven by [OsrsLocMapDataTool] (sprite diff <= 0.01, or the [FeroxMinimapTool] contact-sheet proof for areas 5, 33, 40).
 * [Rev667LocType.encodeFromModern] writes these so a new OSRS loc import keeps its minimap / world-map picture; anything not
 * listed stays dropped (and reported) - never a guessed id.
 */
object OsrsMapIconTable {
    val SCENE_TO_MSI = mapOf(1 to 1, 4 to 4, 5 to 5, 6 to 6, 7 to 7, 11 to 11, 12 to 12, 27 to 27, 46 to 46, 64 to 64, 65 to 65, 68 to 68, 70 to 70)

    val AREA_TO_MAP_ELEMENT =
        mapOf(
            5 to 560, 8 to 563, 9 to 564, 12 to 567, 21 to 575, 28 to 582, 33 to 587, 36 to 590, 37 to 591, 40 to 594,
            41 to 595, 42 to 596, 53 to 607, 58 to 612, 65 to 618,
            // N462 07-11 (square 13098 import): OSRS area 970 sprite 1504 = mapelement 612 (OsrsLocMapDataTool pixel proof diff 0,007).
            970 to 612,
        )

    /** SOURCE_BLOCKED scenes: 22 is an empty OSRS frame, the rest have no 667 picture (nearest diff >= 0.11). */
    val BLOCKED_SCENES = setOf(22, 81, 82, 101, 102, 103, 128, 129, 146, 147, 148, 149, 250, 251)

    /** SOURCE_BLOCKED areas (no 667 picture), the OSRS quest-start icons 792/831 (= our quest icon 1116; owner N227: no quest icons on the map - OsrsLocMapDataTool skips them) and 652 (Death's Office: DeathsOfficeMapImportTool puts 1108 on one loc per entrance). */
    val BLOCKED_AREAS = setOf(13, 66, 71, 91, 792, 831, OsrsLocMapDataTool.DEATHS_OFFICE_AREA)
}

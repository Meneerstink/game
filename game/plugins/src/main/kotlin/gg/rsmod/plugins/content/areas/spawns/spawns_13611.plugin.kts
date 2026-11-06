package gg.rsmod.plugins.content.areas.spawns

/*
 * N462 (2026-11-06, K-GEBIED G6): square 13611 now carries the OSRS terrain (Jaltevas side + Ruins of Ullek; OsrsRegionImportTool
 * 'necropolis'). Its OSRS spawns are the twelve crocodiles "Near the Ruins of Ullek" (OSRS Wiki Crocodile location map pins; ChatGPT-A
 * N462-notities\toa\40-necropolis-spawns.tsv). The 667 archaeologists Kerner/Abigail (plane 1) and the Scabaras locusts/lancers/rangers have
 * no OSRS counterpart on this square and no code uses them (N462 dossier 18:5x) - removed.
 */
listOf(
    3412 to 2774, 3416 to 2773, 3416 to 2777, 3417 to 2764, 3418 to 2782, 3418 to 2788,
    3421 to 2772, 3424 to 2761, 3424 to 2767, 3425 to 2783, 3428 to 2774, 3428 to 2790,
).forEach { (x, z) ->
    spawn_npc(
        npc = Npcs.CROCODILE_6779,
        x = x,
        z = z,
        height = 0,
        walkRadius = 5,
        direction = Direction.NORTH,
        static = false,
    ) // Crocodile
}

package gg.rsmod.plugins.content.areas.spawns

/*
 * N462 (2026-11-06, K-GEBIED G6): square 13355 now carries the OSRS Necropolis terrain (OsrsRegionImportTool 'necropolis'). Its OSRS spawns
 * are the four Desert Lizards "West of Ruins of Ullek" (OSRS Wiki Desert Lizard location map pins 3346,2800 / 3348,2803 / 3344,2804 /
 * 3352,2805; ChatGPT-A N462-notities\toa\40-necropolis-spawns.tsv). The 667 crocodiles (11, in what is now the Necropolis), lizards (9) and
 * vultures (3) of this square have no OSRS counterpart here (OSRS Wiki Necropolis: monsters = Desert wolf x2, quest-only guards) and are
 * gone; no code referenced them (N462 dossier 18:5x).
 */
listOf(3346 to 2800, 3348 to 2803, 3344 to 2804, 3352 to 2805).forEach { (x, z) ->
    spawn_npc(
        npc = Npcs.LIZARD,
        x = x,
        z = z,
        height = 0,
        walkRadius = 5,
        direction = Direction.NORTH,
        static = false,
    ) // Desert Lizard
}

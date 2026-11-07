package gg.rsmod.plugins.content.areas.spawns

spawn_npc(
    npc = Npcs.PLAGUE_FROG,
    x = 3281,
    z = 2750,
    height = 0,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Plague frog
spawn_npc(
    npc = Npcs.PLAGUE_FROG,
    x = 3277,
    z = 2750,
    height = 0,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Plague frog
// N462 07-11: the four rev-667 Priests 1988 at 3300-3308,2728-2732 stood in an empty, undrawn part of square 13098 (live check78
// 07-11 15:5x: white void, black minimap - screens\N462-toa\live-0711\10-priests-667.png). That part is now the OSRS Necropolis
// excavation camp (OsrsRegionImportTool 'necropolis-west'), where OSRS has no priests - removed, not moved.

// N462 (2026-11-06, K-GEBIED G6): OSRS Wiki Necropolis "Monsters: Desert wolf (x2)" - location map pins 3297,2713 and 3300,2711
// (ChatGPT-A N462-notities\toa\40-necropolis-spawns.tsv).
listOf(3297 to 2713, 3300 to 2711).forEach { (x, z) ->
    spawn_npc(npc = Npcs.DESERT_WOLF, x = x, z = z, height = 0, walkRadius = 5, direction = Direction.NORTH, static = false) // Desert Wolf
}

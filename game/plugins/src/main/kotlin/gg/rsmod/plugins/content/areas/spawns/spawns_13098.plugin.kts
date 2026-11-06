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
spawn_npc(
    npc = Npcs.PRIEST_1988,
    x = 3300,
    z = 2730,
    height = 0,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Priest
spawn_npc(
    npc = Npcs.PRIEST_1988,
    x = 3307,
    z = 2728,
    height = 0,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Priest
spawn_npc(
    npc = Npcs.PRIEST_1988,
    x = 3307,
    z = 2732,
    height = 0,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Priest
spawn_npc(
    npc = Npcs.PRIEST_1988,
    x = 3308,
    z = 2730,
    height = 1,
    walkRadius = 5,
    direction = Direction.NORTH,
    static = false,
) // Priest

// N462 (2026-11-06, K-GEBIED G6): OSRS Wiki Necropolis "Monsters: Desert wolf (x2)" - location map pins 3297,2713 and 3300,2711
// (ChatGPT-A N462-notities\toa\40-necropolis-spawns.tsv).
listOf(3297 to 2713, 3300 to 2711).forEach { (x, z) ->
    spawn_npc(npc = Npcs.DESERT_WOLF, x = x, z = z, height = 0, walkRadius = 5, direction = Direction.NORTH, static = false) // Desert Wolf
}

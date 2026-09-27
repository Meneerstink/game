package gg.rsmod.plugins.content.mechanics.npcwalk

/**
 * osrs-docs "Entity Collision": "Excluded NPCs (Dagannoth Kings, Godwars generals, pets, etc.)" neither set nor check the npc and
 * player collision flags - they walk through players and other npcs. Since players block an npc over its whole destination
 * footprint (MovementQueue, breach conformance 2026-09-27), the excluded bosses must be marked or players standing around them
 * in their lairs would pin them. Pets/familiars are excluded by their follower code already. The Deadman breach copies of these
 * bosses are marked by DeadmanBreach.spawnMonster (BreachMonsters.COLLISION_EXCLUDED).
 */
val COLLISION_EXCLUDED_BOSSES =
    setOf(
        2881, 2882, 2883, // Dagannoth Supreme, Prime, Rex
        6260, // General Graardor
        6247, // Commander Zilyana
        6203, // K'ril Tsutsaroth
        6222, // Kree'arra
    )

on_global_npc_spawn {
    if (npc.id in COLLISION_EXCLUDED_BOSSES) npc.ignoresEntityCollision = true
}

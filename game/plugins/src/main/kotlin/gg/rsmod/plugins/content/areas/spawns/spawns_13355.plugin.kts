package gg.rsmod.plugins.content.areas.spawns

/*
 * N462 / K-GEBIED G6: the old 667 crocodiles, lizards and vultures were removed when this square became OSRS terrain.
 * The four OSRS Desert Lizard map pins west of Ullek are (3346,2800), (3348,2803), (3344,2804), (3352,2805), plane 0.
 * Source: OSRS Wiki Desert Lizard oldid=15389340; N462-notities/toa/40-necropolis-spawns.tsv.
 *
 * Do not substitute Lizard 2803 (level 42, size 3) for Desert Lizard (level 24, size 2). The similarly named 2804..2806
 * also use modified 667 model data, not the OSRS meshes. Await Claude's OSRS NPC import and per-tile colour evidence
 * (459 green / 460 yellow / 461 orange; 12003 duplicates 459). Direction and walk radius are not supplied by the map pins.
 * The import handoff and cache comparisons are in team/dossiers/N462.txt; no guessed spawns until those gaps are resolved.
 */

// N462 07-11 (Claude, OsrsNpcImportTool 'necropolis'): the four OSRS Desert Lizards west of Ullek at their Wiki map pins
// (Desert Lizard oldid=15389340). Local 15200/15201/15202 = OSRS 459 green / 460 yellow / 461 orange. ONZEKER: the pins do not say
// which colour stands where (one of each + green again); walkRadius 5, default facing.
spawn_npc(npc = 15200, x = 3346, z = 2800, height = 0, walkRadius = 5)
spawn_npc(npc = 15201, x = 3348, z = 2803, height = 0, walkRadius = 5)
spawn_npc(npc = 15202, x = 3344, z = 2804, height = 0, walkRadius = 5)
spawn_npc(npc = 15200, x = 3352, z = 2805, height = 0, walkRadius = 5)

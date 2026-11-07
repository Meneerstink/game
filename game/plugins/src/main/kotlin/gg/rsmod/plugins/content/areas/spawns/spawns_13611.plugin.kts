package gg.rsmod.plugins.content.areas.spawns

/*
 * N462 / K-GEBIED G6: the old 667 archaeologists Kerner/Abigail and Scabaras locusts/lancers/rangers were removed.
 * Twelve OSRS Crocodiles belong near Ullek, at the plane-0 map pins recorded in N462-notities/toa/40-necropolis-spawns.tsv
 * (OSRS Wiki Crocodile oldid=15374665). Await Claude's import before restoring those spawns.
 *
 * Crocodile 6779 is the level-89 667 Ullek monster, not OSRS level 63. Local 1993 has level 63 but shares 6779's remodeled
 * 667 model 31023, rather than OSRS model 6454. Neither satisfies the task's same-name AND same-model identity requirement.
 * OSRS 4184/11581/11582 have identical cache definitions; 11582 is the land variant (Wiki infobox). No raid crocodile alias.
 * Direction and walk radius are unproven: the Wiki map pins do not justify the previous NORTH / radius 5 settings.
 * Exact tiles and import evidence are in team/dossiers/N462.txt; keep this square free of substitutes pending that handoff.
 */

// N462 07-11 (Claude, OsrsNpcImportTool 'necropolis'): the twelve OSRS Crocodiles near the Ruins of Ullek at their Wiki map pins
// (Crocodile oldid=15374665 via N462-notities/toa/40-necropolis-spawns.tsv). Local 15199 = OSRS 4184 (== 11581 == 11582, cache2686).
// ONZEKER: map pins give no facing/walk radius - walkRadius 5 as our other desert spawns, default facing.
spawn_npc(npc = 15199, x = 3412, z = 2774, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3416, z = 2773, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3416, z = 2777, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3417, z = 2764, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3418, z = 2782, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3418, z = 2788, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3421, z = 2772, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3424, z = 2761, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3424, z = 2767, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3425, z = 2783, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3428, z = 2774, height = 0, walkRadius = 5)
spawn_npc(npc = 15199, x = 3428, z = 2790, height = 0, walkRadius = 5)

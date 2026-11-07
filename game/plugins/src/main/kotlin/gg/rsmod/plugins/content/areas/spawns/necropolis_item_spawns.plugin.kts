package gg.rsmod.plugins.content.areas.spawns

/*
 * N462 (owner 2026-11-07 'waterskin spawns en meer'): the OSRS Necropolis ground spawns - OSRS Wiki Necropolis (action=raw, Item spawns
 * table, {{map|x,y|...|mtype=pin}} per item): Waterskin(4) x3, Chisel x5, Bronze pickaxe x6, Plank x3, Spade x4, all plane 0.
 * Respawn: the Wiki gives none; 90 cycles like every other ground spawn here (ground_item_spawns.plugin.kts).
 */
listOf(3302 to 2739, 3297 to 2737, 3296 to 2731).forEach { (x, z) ->
    spawn_item(item = Items.WATERSKIN_4, amount = 1, x = x, z = z, respawnCycles = 90)
}
listOf(3332 to 2703, 3320 to 2730, 3325 to 2740, 3320 to 2708, 3335 to 2750).forEach { (x, z) ->
    spawn_item(item = Items.CHISEL, amount = 1, x = x, z = z, respawnCycles = 90)
}
listOf(3315 to 2704, 3312 to 2726, 3295 to 2736, 3339 to 2760, 3332 to 2719, 3339 to 2712).forEach { (x, z) ->
    spawn_item(item = Items.BRONZE_PICKAXE, amount = 1, x = x, z = z, respawnCycles = 90)
}
listOf(3323 to 2733, 3312 to 2724, 3352 to 2747).forEach { (x, z) ->
    spawn_item(item = Items.PLANK, amount = 1, x = x, z = z, respawnCycles = 90)
}
listOf(3355 to 2758, 3338 to 2751, 3334 to 2719, 3333 to 2702).forEach { (x, z) ->
    spawn_item(item = Items.SPADE, amount = 1, x = x, z = z, respawnCycles = 90)
}

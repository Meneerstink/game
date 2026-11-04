package gg.rsmod.plugins.content.areas.wilderness

// No old entry/exit handlers. Migrate old positions in memory at login, never by editing save files.
on_world_init { RetiredRevenantDungeon.removeAccess(world) }
on_login { RetiredRevenantDungeon.evacuate(player) }

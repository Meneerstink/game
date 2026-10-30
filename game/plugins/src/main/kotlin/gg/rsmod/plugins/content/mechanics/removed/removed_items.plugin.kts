package gg.rsmod.plugins.content.mechanics.removed

// Items taken out of the game never survive a login (RemovedItems, incl. every Dungeoneering item - DungeoneeringItems). First (owner 2026-10-11, OSRS Deadman Mode): degraded,
// corrupt, used and broken Deadman gear / Torva become the normal item (DeadmanGear) - before the purge, which would delete
// the now unobtainable corrupt ids instead.
// N424: an old save may still hold a looting bag's container - registered so it loads, emptied into the bank at login.
register_container_key(gg.rsmod.plugins.content.mechanics.pvp.LootingBag.KEY)

on_login {
    gg.rsmod.plugins.content.items.armor.DeadmanGear.migrate(player)
    // N424: before the purge takes the bag item itself.
    gg.rsmod.plugins.content.mechanics.pvp.LootingBag.retire(player)
    RemovedItems.purge(player)
    // DGN (owner 2026-10-11): Dungeoneering is gone - old XP back to level 1, old floor progress cleared.
    RemovedSkills.migrate(player)
}

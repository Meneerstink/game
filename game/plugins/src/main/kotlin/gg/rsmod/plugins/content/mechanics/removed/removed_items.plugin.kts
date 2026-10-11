package gg.rsmod.plugins.content.mechanics.removed

// Items taken out of the game never survive a login (RemovedItems). First (owner 2026-10-11, OSRS Deadman Mode): degraded,
// corrupt, used and broken Deadman gear / Torva become the normal item (DeadmanGear) - before the purge, which would delete
// the now unobtainable corrupt ids instead.
on_login {
    gg.rsmod.plugins.content.items.armor.DeadmanGear.migrate(player)
    RemovedItems.purge(player)
}

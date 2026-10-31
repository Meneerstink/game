package gg.rsmod.plugins.content.mechanics.removed

// Login: non-corrupt (deg)/used/broken Deadman gear and Torva become normal first.
// N457: corrupt gear is never converted; RemovedItems.purge deletes it without replacement.
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

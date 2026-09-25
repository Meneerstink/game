package gg.rsmod.plugins.content.mechanics.removed

// Items taken out of the game never survive a login (RemovedItems).
on_login {
    RemovedItems.purge(player)
}

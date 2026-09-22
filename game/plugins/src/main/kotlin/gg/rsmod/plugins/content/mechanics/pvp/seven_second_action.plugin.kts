package gg.rsmod.plugins.content.mechanics.pvp

on_timer(SevenSecondAction.COUNTDOWN_TIMER) {
    SevenSecondAction.complete(player)
}

// Clean logout and X-log both pass through Player.handleLogout(). Never retain a pending
// transport/portal/teleport/logout callback after the player leaves the world.
on_logout {
    SevenSecondAction.cancel(player)
}

// COUNTDOWN_TIMER resets on death. Clear the deferred callback as well, so a death cannot leave
// a transport, portal, teleport or logout action attached to the respawned player.
on_player_pre_death {
    SevenSecondAction.cancel(player)
}

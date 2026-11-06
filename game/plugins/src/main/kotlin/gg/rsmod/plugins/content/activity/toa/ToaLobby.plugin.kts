package gg.rsmod.plugins.content.activity.toa

// The native OSRS party interfaces are not imported yet (card / 30-interfaces.txt).
// Existing chatbox widgets expose the same actions until ToaUi is connected to those imported interfaces.
on_obj_option(ToaLocs.GROUPING_OBELISK, "Inspect") {
    player.queue {
        val lobby = ToaLobby.of(world)
        // Labels: 30-cs2-manifest (toa_partylist_setpartybutton, toa_partydetails_bottom_modify/addmember).
        when (options("Make Party", "Apply", "Quit Party", "Kick", "Cancel", title = "Grouping Obelisk")) {
            1 -> lobby.create(player.uid)
            2 -> inputPlayer()?.let { leader ->
                if (ToaMap.inLobby(player.tile) && ToaMap.inLobby(leader.tile)) lobby.join(player.uid, leader.uid)
            }
            3 -> lobby.leave(player.uid)
            4 -> inputPlayer()?.let { lobby.kick(player.uid, it.uid) }
        }
    }
}

on_obj_option(ToaLocs.TOMB, "Enter") {
    if (ToaMap.inLobby(player.tile) && ToaLobby.canEnter(player)) ToaLobby.of(world).start(player)
}

on_logout {
    ToaLobby.of(world).logout(player)
}

on_login { ToaLobby.of(world).login(player) }

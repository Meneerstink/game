package gg.rsmod.plugins.content.activity.toa

// The native OSRS party interfaces are not imported yet (card / 30-interfaces.txt).
// Existing chatbox widgets expose the same actions until ToaUi is connected to those imported interfaces.
on_obj_option(ToaLocs.GROUPING_OBELISK, "Inspect") {
    player.queue {
        val lobby = ToaLobby.of(world)
        // Labels: 30-cs2-manifest (toa_partylist_setpartybutton, toa_partydetails_bottom_modify/addmember).
        when (options("Make Party", "Apply", "Quit Party", "Kick", "Raid Level", "Cancel", title = "Grouping Obelisk")) {
            1 -> lobby.create(player.uid)
            2 -> inputPlayer()?.let { leader ->
                if (ToaMap.inLobby(player.tile) && ToaMap.inLobby(leader.tile)) lobby.join(player.uid, leader.uid)
            }
            3 -> lobby.leave(player.uid)
            4 -> inputPlayer()?.let { lobby.kick(player.uid, it.uid) }
            // Owner decision A (11-06 21:4x): the leader picks one of the three OSRS modes instead of 44 invocations (ToaMode).
            5 -> {
                val party = lobby.party(player.uid)
                if (party == null || party.leader != player.uid || party.started) return@queue
                val modes = gg.rsmod.plugins.content.activity.toa.core.ToaMode.values()
                val pick = options(*modes.map { "${it.label} (raid level ${it.raidLevel})" }.toTypedArray(), title = "Raid Level")
                val mode = modes.getOrNull(pick - 1) ?: return@queue
                if (lobby.party(player.uid) === party && party.leader == player.uid && !party.started) {
                    // No chat line: OSRS shows the raid level in the party window / HUD (no invented message).
                    party.settings = ToaRaidSettings(raidLevel = mode.raidLevel)
                }
            }
        }
    }
}

on_obj_option(ToaLocs.TOMB, "Enter") {
    if (ToaMap.inLobby(player.tile) && ToaLobby.canEnter(player)) ToaLobby.of(world).requestStart(player)
}

on_logout {
    ToaLobby.of(world).logout(player)
}

on_login { ToaLobby.of(world).login(player) }

// The original multiloc parent must retain its imported transforms for the personal niche appearance.
ToaIds.loc(46082)?.let { id ->
    on_obj_option(id, "Claim") { if (ToaRewards.hasUnclaimed(player)) ToaUi.showRewards(player) }
}

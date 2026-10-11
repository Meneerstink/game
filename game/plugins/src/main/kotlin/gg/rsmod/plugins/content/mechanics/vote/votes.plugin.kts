package gg.rsmod.plugins.content.mechanics.vote

import gg.rsmod.game.service.vote.VoteLedger
import gg.rsmod.game.service.vote.VoteSites
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.player

/*
 * Owner 2026-10-10 voting system (VoteService receives the toplists' callbacks, VoteLedger keeps them): a vote reward reaches the
 * player at once when he is online, otherwise at his next login; ::vote lists the toplists with his own vote link.
 */

on_world_init {
    VoteLedger.onVote = { name -> world.getPlayerForName(name)?.let { VoteRewards.deliver(world, it) } }
}

on_login {
    VoteRewards.deliver(world, player)
}

on_command("vote") {
    val sites = VoteSites.load().filter { it.hasLink }
    if (sites.isEmpty()) {
        player.message("Voting is not open yet.")
        return@on_command
    }
    val ledger = VoteLedger.instance
    player.message("<col=ff981f>Vote for 78Legacy and get 1,000,000 coins per site every 12 hours:</col>")
    sites.forEach { site ->
        val wait = ledger?.waitMillis(site.id, player.username) ?: 0
        val status = if (wait > 0) "<col=ff0000>again in ${wait / 3_600_000}h ${wait / 60_000 % 60}m</col>" else "<col=00ff00>ready</col>"
        player.message("${site.name} ($status): ${site.link(player.username)}")
    }
    player.message("The coins arrive in your bank by themselves a moment after you voted.")
}

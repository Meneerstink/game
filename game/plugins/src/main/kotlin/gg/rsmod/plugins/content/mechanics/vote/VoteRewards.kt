package gg.rsmod.plugins.content.mechanics.vote

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.vote.VoteLedger
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.util.Misc

/**
 * Owner 2026-10-10 voting system: every vote [VoteLedger] accepted is worth [REWARD_COINS] coins, put in the bank (the inventory
 * when the bank has no room) the moment the player is online - right away, or at his next login - with a server-wide message
 * that invites everyone else to `::vote`.
 */
object VoteRewards {
    const val REWARD_COINS = 1_000_000

    fun deliver(
        world: World,
        player: Player,
    ) {
        val ledger = VoteLedger.instance ?: return
        val votes = ledger.takePending(player.username)
        if (votes <= 0) return
        var given = 0
        for (i in 0 until votes) {
            val ok =
                player.bank.add(Items.COINS_995, REWARD_COINS, assureFullInsertion = true).hasSucceeded() ||
                    player.inventory.add(Items.COINS_995, REWARD_COINS, assureFullInsertion = true).hasSucceeded()
            if (!ok) break
            given++
        }
        ledger.giveBack(player.username, votes - given)
        if (given == 0) {
            player.message("<col=ff0000>You have $votes vote reward(s) waiting, but your bank and inventory have no room for the coins.</col>")
            return
        }
        val coins = String.format(java.util.Locale.US, "%,d", given.toLong() * REWARD_COINS)
        player.message("<col=00ff00>Thank you for voting! $coins coins have been added to your bank.</col>")
        if (given < votes) player.message("<col=ff0000>${votes - given} more reward(s) wait until you have room for the coins.</col>")
        val name = Misc.formatForDisplay(player.username)
        world.players.entries.filterNotNull().forEach {
            it.message("[<col=d45b5b>Vote</col>]: <col=0000ff>$name just voted and received $coins coins! Type ::vote to get yours.</col>")
        }
    }
}

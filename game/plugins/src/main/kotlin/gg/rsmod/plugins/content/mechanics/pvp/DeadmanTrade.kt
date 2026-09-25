package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/**
 * Deadman rule that reacts to a completed trade (Audit D-07): what one side handed the other no longer counts as the
 * receiver's risk if the giver kills him ([ValidPkKill.noteGift]). Called once per completed trade from
 * `TradeSession.complete()`, after both sides' offers are final and before `finalise()` clears the trade containers.
 */
object DeadmanTrade {
    fun onTradeCompleted(
        first: Player,
        firstReceived: List<Item>,
        second: Player,
        secondReceived: List<Item>,
    ) {
        val world = first.world
        ValidPkKill.noteGift(second.username, first.username, gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.riskValue(world, firstReceived))
        ValidPkKill.noteGift(first.username, second.username, gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem.riskValue(world, secondReceived))
    }
}

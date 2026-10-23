package gg.rsmod.game.message.handler

import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpPlayerExtraMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.attr.INTERACTING_PLAYER_ATTR
import gg.rsmod.game.model.entity.Client
import java.lang.ref.WeakReference

/**
 * Player menu slots 1 and 5-10 (owner 2026-09-18 P0: the server offers "Challenge" in slot 1, but that click had no route;
 * "Req Assist" is gone, A21). Same flow as OpPlayer2-4Handler.
 */
class OpPlayerExtraHandler : MessageHandler<OpPlayerExtraMessage> {
    override fun handle(client: Client, world: World, message: OpPlayerExtraMessage) {
        val option = message.option
        val optionIndex = option - 1
        if (!client.lock.canPlayerInteract()) return
        val other = world.players[message.index] ?: return
        if (optionIndex !in client.options.indices || client.options[optionIndex] == null || other == client) return
        log(client, "Player option: name=%s, opt=%d", other.username, option)
        client.closeInterfaceModal()
        client.fullInterruption(movement = true, interactions = true, queue = true)
        client.attr[INTERACTING_PLAYER_ATTR] = WeakReference(other)
        client.attr[INTERACTING_OPT_ATTR] = option
        client.executePlugin(PawnPathAction.walkPlugin)
    }
}
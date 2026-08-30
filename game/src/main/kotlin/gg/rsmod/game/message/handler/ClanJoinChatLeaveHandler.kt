package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/**
 * Sent when the client leaves whatever channel is shown in the Friends Chat/Clan Chat tab
 * (e.g. clicking "Leave" on that tab). This server's clan chat
 * ([gg.rsmod.plugins.content.mechanics.clan.Clans]) is a `::cc` broadcast command rather than a
 * real channel with server-tracked membership (interface 1110's channel packet protocol is
 * net-layer work not implemented yet - see that object's kdoc), so there is no per-player
 * channel state to tear down here; this only needs to stop crashing when the client sends it.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class ClanJoinChatLeaveHandler : MessageHandler<ClanJoinChatLeaveChatMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ClanJoinChatLeaveChatMessage,
    ) {
        // No-op until a real channel protocol exists to leave.
    }
}

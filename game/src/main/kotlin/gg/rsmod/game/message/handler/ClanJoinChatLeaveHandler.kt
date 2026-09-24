package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.util.Misc

/**
 * Joins or leaves the channel shown in the Friends Chat tab.
 *
 * The client sends this both when a channel name is typed into the join prompt and when `Leave` is
 * clicked; an empty name means leave. Nothing happened here before, so the join prompt accepted a
 * name and then appeared to do nothing - the client waits for
 * [gg.rsmod.game.message.impl.UpdateFriendChatChannelFullMessage] before it shows a channel, and
 * that was never sent.
 *
 * A channel is named after the player who owns it, so the name typed in is a player name. Joining
 * the channel of somebody who has never played here would leave a member list nobody can moderate,
 * so the owner has to be a real character.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class ClanJoinChatLeaveHandler : MessageHandler<ClanJoinChatLeaveChatMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ClanJoinChatLeaveChatMessage,
    ) {
        val player = client as? Player ?: return

        if (message.name.isBlank()) {
            if (world.friendsChat.channelOf(player) != null) {
                world.friendsChat.leave(player)
                player.writeMessage("You have left the channel.")
            }
            return
        }

        world.friendsChat.joinWithMessages(player, Misc.formatForDisplay(message.name))
    }
}

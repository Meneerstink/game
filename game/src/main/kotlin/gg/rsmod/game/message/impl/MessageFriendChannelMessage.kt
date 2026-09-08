package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.World

/**
 * One line of friends-chat text, on its way to a single member of the channel.
 *
 * @param sender the sender's display name, as the recipient should see it.
 * @param channel the channel's display name; the client shows it beside the line.
 * @param rank the sender's rank, which only picks the crown drawn in front of the name.
 * @param id a unique id for this line. The client remembers the last hundred it has seen and
 * silently drops a repeat, so two different lines must never share one.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class MessageFriendChannelMessage(
    val world: World,
    val sender: String,
    val channel: String,
    val rank: Int,
    val id: Long,
    val text: String,
) : Message

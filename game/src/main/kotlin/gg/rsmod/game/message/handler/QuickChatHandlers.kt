package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.MessageQuickChatPrivateMessage
import gg.rsmod.game.message.impl.MessageQuickChatPublicMessage
import gg.rsmod.game.message.impl.QuickChatPrivateEchoOutMessage
import gg.rsmod.game.message.impl.QuickChatPrivateOutMessage
import gg.rsmod.game.message.impl.QuickChatPublicOutMessage
import gg.rsmod.game.message.impl.SetPrivateChatFilterMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.social.PrivateMessagePolicy
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.GamePacketBuilder
import gg.rsmod.util.Misc
import kotlin.math.min

/** Builds a packet body with [GamePacketBuilder] and returns its bytes. */
internal inline fun packetBody(build: GamePacketBuilder.() -> Unit): ByteArray {
    val buf = GamePacketBuilder()
    buf.build()
    val data = ByteArray(buf.byteBuf.readableBytes())
    buf.byteBuf.readBytes(data)
    return data
}

/**
 * Quick-chat phrases said aloud (channel 0) or into the friends-chat channel (channel 1). Public phrases go to everybody
 * within the same 30-tile radius as typed public chat, through MESSAGE_PUBLIC with the 0x8000 quick-chat flag, exactly the
 * framing the client's MESSAGE_PUBLIC reader expects. Clan channels (2/3) have no server clan system yet and are ignored.
 */
class MessageQuickChatPublicHandler : MessageHandler<MessageQuickChatPublicMessage> {
    override fun handle(client: Client, world: World, message: MessageQuickChatPublicMessage) {
        if (message.payload.size < 2) return
        when (message.channel) {
            0 -> {
                val body =
                    packetBody {
                        put(DataType.SHORT, client.index)
                        put(DataType.SHORT, QUICKCHAT_FLAG)
                        put(DataType.BYTE, client.privilege.icon)
                        putBytes(message.payload)
                    }
                world.players.forEach {
                    if (it.tile.isWithinRadius(client.tile, 30)) it.write(QuickChatPublicOutMessage(body))
                }
            }
            1 -> if (!world.friendsChat.talkQuickChat(world, client, message.payload)) client.writeMessage("You are not in a friends chat channel.")
            2 -> if (world.socialHooks.clanQuickChat?.invoke(client, message.payload) != true) client.writeMessage("You are not in a clan channel.")
        }
    }

    private companion object {
        const val QUICKCHAT_FLAG = 0x8000
    }
}

/** A quick-chat phrase to a friend: same delivery rules as typed private messages (PrivateMessagePolicy). */
class MessageQuickChatPrivateHandler : MessageHandler<MessageQuickChatPrivateMessage> {
    override fun handle(client: Client, world: World, message: MessageQuickChatPrivateMessage) {
        if (message.payload.size < 2) return
        val fromPlayer = client as Player
        val toPlayer = world.getPlayerForName(message.username)
        val senderName = Misc.formatForDisplay(fromPlayer.username)
        PrivateMessagePolicy.deliveryRefusal(
            targetOnline = toPlayer != null,
            targetIgnoresSender = toPlayer != null && toPlayer != fromPlayer &&
                toPlayer.ignoredPlayers.any { Misc.formatForDisplay(it).equals(senderName, ignoreCase = true) },
            senderIsAdmin = fromPlayer.privilege.powers.contains(Privilege.ADMIN_POWER),
        )?.let {
            client.writeMessage(it)
            return
        }
        toPlayer!!

        // A quick-chat private message is still a private message. Keep the sender's
        // presence/friend-list transition identical to the typed-message route.
        val newStatus = PrivateMessagePolicy.senderPrivateStatusAfterSending(fromPlayer.privateFilterSetting)
        if (newStatus != fromPlayer.privateFilterSetting) {
            fromPlayer.privateFilterSetting = newStatus
            fromPlayer.write(SetPrivateChatFilterMessage(newStatus.settingId))
            fromPlayer.updateOthersFriendLists()
        }

        val id = world.getNextMessageCount()
        toPlayer.write(
            QuickChatPrivateOutMessage(
                packetBody {
                    put(DataType.BYTE, 0)
                    putString(senderName)
                    put(DataType.SHORT, id and 0xFFFF)
                    put(DataType.TRI_BYTE, PRIVATE_ID_LOW)
                    put(DataType.BYTE, min(fromPlayer.privilege.id, 2))
                    putBytes(message.payload)
                },
            ),
        )
        fromPlayer.write(
            QuickChatPrivateEchoOutMessage(
                packetBody {
                    putString(Misc.formatForDisplay(toPlayer.username))
                    putBytes(message.payload)
                },
            ),
        )
    }

    private companion object {
        /** Same low id word typed private messages use (Player.receivePrivateMessage), so ids never collide with 0. */
        const val PRIVATE_ID_LOW = 15
    }
}

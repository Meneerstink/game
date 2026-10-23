package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ChatSetModeMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Client

/** The channel mode of the client's chat input (N239), read by [MessagePublicHandler] for the line that follows. */
val CHAT_MODE_ATTR = AttributeKey<Int>()

class ChatSetModeHandler : MessageHandler<ChatSetModeMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ChatSetModeMessage,
    ) {
        client.attr[CHAT_MODE_ATTR] = message.mode.coerceIn(0, 3)
    }
}

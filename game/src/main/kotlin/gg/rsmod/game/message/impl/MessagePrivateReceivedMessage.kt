package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.priv.Privilege

data class MessagePrivateReceivedMessage(
    val username: String,
    val privilege: Privilege,
    /** N264 / N265: from [gg.rsmod.game.model.social.ChatMessageIds], unique among every chat line the client de-duplicates. */
    val messageId: Long,
    val message: String,
    val world: World,
) : Message

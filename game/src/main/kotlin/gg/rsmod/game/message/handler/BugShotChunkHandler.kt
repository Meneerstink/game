package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.BugShotChunkMessage
import gg.rsmod.game.model.BugShotUploads
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/**
 * T2: GameSystem.receiveMessage hands every [BugShotChunkMessage] to [BugShotUploads] on the network thread and never queues
 * it; this handler only exists because every registered packet needs one (MessageDecoderSet) and routes to the same place.
 */
class BugShotChunkHandler : MessageHandler<BugShotChunkMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: BugShotChunkMessage,
    ) {
        BugShotUploads.receive(client.username, message.stem, message.total, message.offset, message.data)
    }
}

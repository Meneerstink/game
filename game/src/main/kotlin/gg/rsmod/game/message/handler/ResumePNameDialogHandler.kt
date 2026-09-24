package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ResumePNameDialogMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.queue.QueueTask

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ResumePNameDialogHandler : MessageHandler<ResumePNameDialogMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ResumePNameDialogMessage,
    ) {
        /*
         * Opcode 7 is the 667 client's reply to clientscript 110, the long-text input (Novite InputPacketHandler
         * ENTER_LONG_STRING_PACKET = 7), not a player-name lookup. Resolving it to a player dropped every typed text
         * (owner 2026-09-24: devmode Rename always reset the name). The text goes back as typed; callers that want a
         * player look it up themselves (QueueTask.inputPlayer).
         */
        log(client, "Long text input dialog: input=%s", message.name)
        client.queues.submitReturnValue(message.name)
    }
}

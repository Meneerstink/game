package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ResumePauseButtonHandler : MessageHandler<ResumePauseButtonMessage> {
    companion object {
        /**
         * Plugins that consume a pause button outside the dialogue queue (true = handled). The bank PIN keypad: the 667 client
         * sends its FOURTH digit as RESUME_PAUSEBUTTON on 13:(digit + 6), not as a 759 click (Novite InterfacePacketHandler
         * `finishPin(componentId - 6)`; owner bug 2026-10-04 031751 "at the fourth digit nothing happens"). The book interface
         * 960's Prev/Next (72/73) arrive the same way (N11 2026-10-31). A list, so one plugin never replaces another's.
         */
        val hooks = java.util.concurrent.CopyOnWriteArrayList<(gg.rsmod.game.model.entity.Player, Int, Int) -> Boolean>()
    }

    override fun handle(
        client: Client,
        world: World,
        message: ResumePauseButtonMessage,
    ) {
        log(
            client,
            "Continue dialog: component=[%d:%d], button=%d",
            message.interfaceId,
            message.component,
            message.button,
        )
        if (hooks.any { it(client, message.interfaceId, message.component) }) return
        client.queues.submitReturnValue(message)
    }
}

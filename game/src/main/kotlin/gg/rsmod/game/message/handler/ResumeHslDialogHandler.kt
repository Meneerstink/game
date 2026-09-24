package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ResumeHslDialogMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/** The HSL colour picker's Accept: handed to whoever opened the picker (the clan motif designer). */
class ResumeHslDialogHandler : MessageHandler<ResumeHslDialogMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ResumeHslDialogMessage,
    ) {
        log(client, "HSL colour chosen: %d", message.hsl)
        world.socialHooks.hslColourChosen?.invoke(client, message.hsl)
    }
}

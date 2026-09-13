package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ResumeObjDialogMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.OBJ_DIALOG_ITEM_ATTR
import gg.rsmod.game.model.entity.Client

class ResumeObjDialogHandler : MessageHandler<ResumeObjDialogMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ResumeObjDialogMessage,
    ) {
        log(client, "Object dialog input: item=%d", message.item)
        client.attr[OBJ_DIALOG_ITEM_ATTR] = message.item
        world.plugins.executeObjDialog(client)
    }
}

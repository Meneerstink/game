package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpNpc6Message
import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpNpc6Handler : MessageHandler<OpNpc6Message> {
    override fun handle(
        client: Client,
        world: World,
        message: OpNpc6Message,
    ) {
        val npc = world.npcs[message.index] ?: return

        if (!client.lock.canNpcInteract()) {
            return
        }

        log(client, "Npc option 6: index=%d, npc=%s", message.index, npc)

        val override = npc.attr[gg.rsmod.game.model.attr.EXAMINE_OVERRIDE_ATTR]
        if (override != null) client.writeMessage(override) else world.sendExamine(client, npc.id, ExamineEntityType.NPC)
        world.npcExamineExtra?.invoke(npc)?.let { client.writeMessage(it) }
    }
}

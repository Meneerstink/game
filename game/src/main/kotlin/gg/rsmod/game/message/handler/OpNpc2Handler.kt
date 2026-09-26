package gg.rsmod.game.message.handler

import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpNpc2Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.priv.Privilege
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
class OpNpc2Handler : MessageHandler<OpNpc2Message> {
    override fun handle(
        client: Client,
        world: World,
        message: OpNpc2Message,
    ) {
        val npc = world.npcs[message.index] ?: return

        // The client sends op 2 for whatever the npc's second option is. Only "Attack" (or no option) attacks; any
        // other second option ("Quest choices" on the Quest Tutor, cache npcs with "Trade" there) is an interaction
        // like op 3 and goes to the plugin bound to option 2.
        if (!routesToAttack(npc.def.options.getOrNull(1))) {
            if (!client.lock.canNpcInteract()) return
            log(client, "Npc option 2 (interaction): index=%d, movement=%d, npc=%s", message.index, message.movementType, npc)
            if (message.movementType == 1 && world.privileges.isEligible(client.privilege, Privilege.ADMIN_POWER)) {
                client.moveTo(world.findRandomTileAround(npc.tile, 1) ?: npc.tile)
            }
            client.closeInterfaceModal()
            client.fullInterruption(movement = true, interactions = true, queue = true)
            client.attr[INTERACTING_OPT_ATTR] = 2
            client.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
            client.executePlugin(PawnPathAction.walkPlugin)
            return
        }

        if (!client.lock.canAttack()) {
            return
        }

        log(client, "Npc option 2: index=%d, movement=%d, npc=%s", message.index, message.movementType, npc)

        if (message.movementType == 1 && world.privileges.isEligible(client.privilege, Privilege.ADMIN_POWER)) {
            client.moveTo(world.findRandomTileAround(npc.tile, 1) ?: npc.tile)
        }

        client.closeInterfaceModal()
        client.fullInterruption(movement = true, interactions = true, queue = true)

        client.attack(npc)
        npc.attr[LAST_HIT_ATTR] = WeakReference(client as Pawn)
    }

    companion object {
        /** True when the npc's second option is an attack: "Attack" or none (a combat npc without a listed option). */
        fun routesToAttack(option: String?): Boolean = option.isNullOrBlank() || option.equals("Attack", ignoreCase = true)
    }
}

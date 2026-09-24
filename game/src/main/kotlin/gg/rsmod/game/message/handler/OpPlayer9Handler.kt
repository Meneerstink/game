package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.OpPlayer9Message
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/** A clicked clan-invite chat line: the clan plugin opens the invitation (no walking - the inviter may be anywhere nearby). */
class OpPlayer9Handler : MessageHandler<OpPlayer9Message> {
    override fun handle(
        client: Client,
        world: World,
        message: OpPlayer9Message,
    ) {
        val other = world.players[message.index] ?: return
        if (other === client) return
        log(client, "Player option 9: name=%s", other.username)
        world.socialHooks.clanInviteClicked?.invoke(client, other)
    }
}

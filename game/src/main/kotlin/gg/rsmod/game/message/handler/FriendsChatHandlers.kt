package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ClanChannelKickMessage
import gg.rsmod.game.message.impl.FriendSetRankMessage
import gg.rsmod.game.message.impl.FriendsChatKickMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client

/** Friends Chat tab "Kick/ban": the kicker's rank and the one-hour ban are FriendsChat's rules. */
class FriendsChatKickHandler : MessageHandler<FriendsChatKickMessage> {
    override fun handle(client: Client, world: World, message: FriendsChatKickMessage) {
        if (message.name.isBlank()) return
        world.friendsChat.kick(client, message.name)
    }
}

/** Friends Chat Setup: a friend's rank changed; the friend list shows it at once. */
class FriendSetRankHandler : MessageHandler<FriendSetRankMessage> {
    override fun handle(client: Client, world: World, message: FriendSetRankMessage) {
        world.friendsChat.setFriendRank(client, message.name, message.rank)
        client.updateFriendList()
    }
}

/** Clan-channel kick of a guest; the clan system lives in the plugins ([gg.rsmod.game.model.social.SocialHooks]). */
class ClanChannelKickHandler : MessageHandler<ClanChannelKickMessage> {
    override fun handle(client: Client, world: World, message: ClanChannelKickMessage) {
        world.socialHooks.clanKick?.invoke(client, message.affined, message.name)
    }
}

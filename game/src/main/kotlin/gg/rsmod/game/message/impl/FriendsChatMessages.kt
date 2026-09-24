package gg.rsmod.game.message.impl

import gg.rsmod.game.message.Message

/** ClientProt CLAN_KICKUSER (32): the Friends Chat tab's Kick/ban - the name of the member to kick. */
data class FriendsChatKickMessage(val name: String) : Message

/** ClientProt FRIEND_SETRANK (41): Friends Chat Setup gives a friend a rank (FriendsList.setRank: pjstr name, p1_alt2 rank). */
data class FriendSetRankMessage(val name: String, val rank: Int) : Message

/** ClientProt CLANCHANNEL_KICKUSER (60): kick a guest from a clan channel (Static525.kick: p1 affined, p2 slot, pjstr name). */
data class ClanChannelKickMessage(val affined: Boolean, val slot: Int, val name: String) : Message

/** ClientProt AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL (44): permanently ban a guest of the own clan channel (Static180.ban: p2 slot, pjstr name). */
data class ClanBanFromChannelMessage(val slot: Int, val name: String) : Message

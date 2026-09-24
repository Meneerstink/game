package gg.rsmod.plugins.content.inter.friends

import gg.rsmod.game.model.social.FriendsChat
import gg.rsmod.game.model.social.FriendsChatRank

/*
 * Friends Chat Setup (owner 2026-09-24: "friendchat volledig werkend maken"). The Friends Chat tab (1109) "Open Settings" (33) opens
 * the rev-667 setup interface 1108: the channel prefix (22: Set prefix / Disable), who can enter (23), talk (24) and kick (25) and the
 * LootShare rank (26). The five TEXT components carry their menus in the cache; the server writes the current value into each. The
 * friend list inside it (layer 13, CS2 1895) sets ranks with ClientProt FRIEND_SETRANK, handled in the game module (FriendsChat).
 */
val SETUP = 1108
val TAB = 1109
val PREFIX = 22
val ENTER = 23
val TALK = 24
val KICK = 25
val LOOT = 26

/** IF_BUTTON1..9 opcodes (ClientProt) -> op index. */
val OP_INDEX = mapOf(61 to 1, 64 to 2, 4 to 3, 52 to 4, 81 to 5, 91 to 6, 18 to 7, 10 to 8, 20 to 9)

fun refresh(player: Player) {
    val s = world.friendsChat.settingsOf(player.username)
    player.setComponentText(SETUP, PREFIX, s?.prefix ?: "Chat disabled")
    player.setComponentText(SETUP, ENTER, FriendsChatRank.label(s?.enterRank ?: FriendsChatRank.GUEST))
    player.setComponentText(SETUP, TALK, FriendsChatRank.label(s?.talkRank ?: FriendsChatRank.GUEST))
    player.setComponentText(SETUP, KICK, FriendsChatRank.label(s?.kickRank ?: FriendsChatRank.OWNER))
    val loot = s?.lootShareRank ?: FriendsChatRank.GUEST
    player.setComponentText(SETUP, LOOT, if (loot == FriendsChatRank.GUEST) "No-one" else FriendsChatRank.label(loot))
}

on_button(interfaceId = TAB, component = 33) {
    player.openInterface(SETUP, InterfaceDestination.MAIN_SCREEN)
    val p = player
    // The setup interface's onLoad scripts run when the IfOpenSub arrives; the values are written a cycle later.
    world.queue {
        wait(1)
        if (p.isOnline) refresh(p)
    }
}

on_button(interfaceId = SETUP, component = 3) {
    player.closeInterface(SETUP)
}

on_button(interfaceId = SETUP, component = PREFIX) {
    when (OP_INDEX[player.getInteractingOpcode()]) {
        1 -> player.queue {
            val typed = inputString("Enter chat prefix:")
            val prefix = FriendsChat.validPrefix(typed)
            if (prefix == null) {
                player.message("A channel name is 1 to 12 letters, numbers or spaces.")
                return@queue
            }
            world.friendsChat.setPrefix(player, prefix)
            refresh(player)
        }
        2 -> {
            world.friendsChat.setPrefix(player, null)
            refresh(player)
        }
    }
}

// Enter / talk: op1 Anyone (-1), op2 Any friends (0), op3..op8 Recruit+..General+ (1..6), op9 Only me (7).
on_button(interfaceId = SETUP, component = ENTER) {
    val op = OP_INDEX[player.getInteractingOpcode()] ?: return@on_button
    world.friendsChat.setEnterRank(player, op - 2)
    refresh(player)
}

on_button(interfaceId = SETUP, component = TALK) {
    val op = OP_INDEX[player.getInteractingOpcode()] ?: return@on_button
    world.friendsChat.setTalkRank(player, op - 2)
    refresh(player)
}

// Kick: only op4 Corporal+ (2) .. op9 Only me (7) exist in the cache menu.
on_button(interfaceId = SETUP, component = KICK) {
    val op = OP_INDEX[player.getInteractingOpcode()] ?: return@on_button
    if (op < 4) return@on_button
    world.friendsChat.setKickRank(player, op - 2)
    refresh(player)
}

// LootShare: op1 No-one (-1), op2 Any friends (0), op3..op8 Recruit+..General+.
on_button(interfaceId = SETUP, component = LOOT) {
    val op = OP_INDEX[player.getInteractingOpcode()] ?: return@on_button
    world.friendsChat.setLootShareRank(player, op - 2)
    refresh(player)
}

on_button(interfaceId = SETUP, component = 33) {
    player.message("CoinShare is not available on this server.")
}

on_button(interfaceId = TAB, component = 19) {
    player.message("LootShare is not available on this server.")
}

// The channel settings keep a snapshot of the owner's friends, so ranks and "Any friends" work while the owner is offline.
on_login {
    world.friendsChat.syncFriends(player)
}

on_add_friend {
    world.friendsChat.syncFriends(player)
}

on_delete_friend {
    world.friendsChat.syncFriends(player)
}

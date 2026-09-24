package gg.rsmod.plugins.content.mechanics.clan

/*
 * Clan Chat (owner 2026-09-24 "clanchat ... volledig werkend maken"): the clan channel lives in [Clans]; the net layer reaches it
 * through the game module's SocialHooks ("//" own clan, "///" guest clan, clan quick chat, guest kick). The Clan Chat tab (1110)
 * buttons: Clan Details (76), Clan Settings (80), Join Clan Channel (85 / 95), Leave Clan (115).
 */
world.socialHooks.clanTalk = { player, text -> Clans.talk(player, text, guest = false) }
world.socialHooks.clanGuestTalk = { player, text -> Clans.talk(player, text, guest = true) }
world.socialHooks.clanQuickChat = { player, payload -> Clans.talkQuickChat(player, payload) }
world.socialHooks.clanKick = { player, affined, name -> Clans.kickGuest(player, affined, name) }

val CLAN_TAB = 1110

on_login {
    Clans.connect(player)
}

on_logout {
    Clans.onLogout(player)
}

on_button(interfaceId = CLAN_TAB, component = 76) {
    Clans.details(player).forEach { player.message(it) }
}

listOf(85, 95).forEach { component ->
    on_button(interfaceId = CLAN_TAB, component = component) {
        if (Clans.clanOf(player) != null) {
            Clans.connect(player)
            player.message("Now talking in your clan channel. To talk, start each line of chat with //.")
            return@on_button
        }
        player.queue {
            val name = inputString("Enter the name of the clan whose channel you want to join as a guest:")
            if (name.isNotBlank()) Clans.listen(player, name)
        }
    }
}

on_button(interfaceId = CLAN_TAB, component = 115) {
    if (Clans.clanOf(player) == null) {
        player.message("You're not in a clan.")
        return@on_button
    }
    player.queue {
        if (options("Yes, leave my clan.", "No, stay.", title = "Leave your clan?") == 1) Clans.leave(player)
    }
}

on_button(interfaceId = CLAN_TAB, component = 80) {
    if (Clans.clanOf(player) == null) {
        player.message("You're not in a clan. Found one with the command: clan create <name>")
        return@on_button
    }
    player.queue {
        when (options("Who can talk in the channel", "Who can kick guests", "Allow or refuse guests", "Nothing", title = "Clan Settings")) {
            1 -> rankChoice(this, "Who can talk?")?.let { Clans.setTalkRank(player, it) }
            2 -> rankChoice(this, "Who can kick guests?")?.let { Clans.setKickRank(player, it) }
            3 -> when (options("Allow guests", "Refuse guests", title = "Guests in the clan channel")) {
                1 -> Clans.setAllowGuests(player, true)
                2 -> Clans.setAllowGuests(player, false)
            }
        }
    }
}

suspend fun rankChoice(task: QueueTask, title: String): ClanRank? {
    val ranks = listOf(ClanRank.MEMBER, ClanRank.CORPORAL, ClanRank.GENERAL, ClanRank.ADMIN, ClanRank.DEPUTY)
    val choice = task.options(*ranks.map { "${it.label}+" }.toTypedArray(), title = title)
    return ranks.getOrNull(choice - 1)
}

on_command("clan") {
    val args = player.getCommandArgs()
    when (args.getOrNull(0)?.lowercase()) {
        "create" -> {
            val name = args.drop(1).joinToString(" ")
            if (name.isBlank()) player.filterableMessage("Usage: clan create <name>") else Clans.create(player, name)
        }
        "invite" -> {
            val targetName = args.drop(1).joinToString(" ")
            val target = player.world.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target == null) player.filterableMessage("That player isn't online.") else Clans.invite(player, target)
        }
        "leave" -> Clans.leave(player)
        "rank" -> {
            val rank = args.lastOrNull()?.let { r -> ClanRank.values().firstOrNull { it.name.equals(r, true) || it.label.replace(" ", "").equals(r, true) } }
            val name = args.drop(1).dropLast(1).joinToString(" ")
            if (rank == null || name.isBlank() || rank == ClanRank.OWNER) {
                player.filterableMessage("Usage: clan rank <player> <recruit|corporal|sergeant|lieutenant|captain|general|admin|deputy>")
            } else {
                Clans.setRank(player, name, rank)
            }
        }
        else -> player.filterableMessage("Usage: clan create <name> | clan invite <name> | clan rank <name> <rank> | clan leave")
    }
}

on_command("cc") {
    val message = player.getCommandArgs().joinToString(" ")
    if (message.isBlank()) player.filterableMessage("Usage: cc <message> (or start a chat line with //)") else Clans.chat(player, message)
}

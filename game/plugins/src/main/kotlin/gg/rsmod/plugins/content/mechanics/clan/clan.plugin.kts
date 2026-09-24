package gg.rsmod.plugins.content.mechanics.clan

/*
 * Clan Chat (owner 2026-09-24 "clanchat ... volledig werkend maken"): the clan channel lives in [Clans]; the net layer reaches it
 * through the game module's SocialHooks ("//" own clan, "///" guest clan, clan quick chat, guest kick). The Clan Chat tab (1110)
 * buttons: Clan Details (76), Clan Settings (80), Join Clan Channel (85 / 95), Leave Clan (115).
 */
world.socialHooks.clanTalk = { player, text -> Clans.talk(player, text, guest = false) }
world.socialHooks.clanGuestTalk = { player, text -> Clans.talk(player, text, guest = true) }
world.socialHooks.clanQuickChat = { player, payload -> Clans.talkQuickChat(player, payload) }
world.socialHooks.clanGuestQuickChat = { player, payload -> Clans.talkQuickChat(player, payload, guest = true) }
world.socialHooks.clanKick = { player, affined, name -> Clans.kickGuest(player, affined, name) }
world.socialHooks.isClanmate = { observer, other -> Clans.sameClan(observer, other) }
world.socialHooks.clanBanFromChannel = { player, name -> Clans.ban(player, name) }

val CLAN_TAB = 1110

on_login {
    Clans.connect(player)
}

on_logout {
    Clans.onLogout(player)
}

world.socialHooks.clanInviteClicked = { player, inviter -> ClanScreens.openInvite(player, inviter) }
world.socialHooks.hslColourChosen = { player, hsl -> ClanScreens.colourChosen(player, hsl) }

on_button(interfaceId = CLAN_TAB, component = 76) {
    ClanScreens.openDetails(player)
}

/* "Invite": the cache bakes 1110:90 as a player-targeted button (events 0x4000); the chosen player arrives as OPPLAYERT. */
on_spell_on_player(CLAN_TAB, 90) {
    val target = player.getInteractingPlayer()
    Clans.recruit(player, target)
}

/* Invitation screen (1095): Accept is its pause button (ClanScreens.openInvite waits for it); these refuse. */
ClanScreens.INVITE_REFUSE.forEach { component ->
    on_button(interfaceId = ClanScreens.INVITE, component = component) { ClanScreens.refuseInvite(player) }
}

/* National flag (1096 "Select Flag" -> 1089). */
on_button(interfaceId = ClanSettingsInterface.INTERFACE, component = ClanSettingsInterface.SELECT_FLAG) { ClanScreens.openFlags(player) }
on_button(interfaceId = ClanScreens.FLAGS, component = ClanScreens.FLAG_LIST) { ClanScreens.selectFlag(player, player.getInteractingSlot()) }
on_button(interfaceId = ClanScreens.FLAGS, component = ClanScreens.FLAG_SAVE) { ClanScreens.saveFlag(player) }
on_button(interfaceId = ClanScreens.FLAGS, component = ClanScreens.FLAG_CLOSE) { ClanScreens.backToSettings(player, ClanScreens.FLAGS) }

/* Motif Designer (1096 "Edit motif" -> 1105, colours through the HSL picker 1106). */
on_button(interfaceId = ClanSettingsInterface.INTERFACE, component = ClanSettingsInterface.EDIT_MOTIF) { ClanScreens.openMotif(player) }
on_button(interfaceId = ClanScreens.MOTIF, component = ClanScreens.MOTIF_TOP_LIST) { ClanScreens.selectSymbol(player, top = true, slot = player.getInteractingSlot()) }
on_button(interfaceId = ClanScreens.MOTIF, component = ClanScreens.MOTIF_BOTTOM_LIST) { ClanScreens.selectSymbol(player, top = false, slot = player.getInteractingSlot()) }
ClanScreens.MOTIF_COLOUR_BUTTONS.forEachIndexed { part, component ->
    on_button(interfaceId = ClanScreens.MOTIF, component = component) { ClanScreens.editColour(player, part) }
}
on_button(interfaceId = ClanScreens.MOTIF, component = ClanScreens.MOTIF_DONE) { ClanScreens.backToSettings(player, ClanScreens.MOTIF) }
on_button(interfaceId = ClanScreens.MOTIF, component = ClanScreens.MOTIF_CLOSE) { player.closeInterface(ClanScreens.MOTIF) }

/* Own clan channel: join / leave; without a clan it offers to found one (Novite ClanCreateDialogue). */
on_button(interfaceId = CLAN_TAB, component = 85) {
    if (Clans.clanOf(player) != null) {
        Clans.toggleOwnChannel(player)
        return@on_button
    }
    player.queue {
        if (options("Yes, found a clan.", "No thanks.", title = "You are not in a clan. Found one?") != 1) return@queue
        val name = inputString("Enter the name of your new clan:")
        if (name.isNotBlank()) Clans.create(player, name)
    }
}

/* Another clan's channel as a guest ("///"); clicking again while listening leaves it. */
on_button(interfaceId = CLAN_TAB, component = 95) {
    if (Clans.isListening(player)) {
        Clans.stopListening(player)
        player.message("You have left the guest clan chat channel.")
        return@on_button
    }
    player.queue {
        val name = inputString("What clan would you like to enter?")
        if (name.isNotBlank()) Clans.listen(player, name)
    }
}

on_button(interfaceId = CLAN_TAB, component = 100) {
    player.queue {
        val name = inputString("Enter the name of the player you wish to ban:")
        if (name.isNotBlank()) Clans.ban(player, name)
    }
}

on_button(interfaceId = CLAN_TAB, component = 105) {
    player.queue {
        val name = inputString("Enter the name of the player you wish to unban:")
        if (name.isNotBlank()) Clans.unban(player, name)
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
    ClanSettingsInterface.open(player)
}

/* Clan Settings (1096) - see [ClanSettingsInterface]. */
val SETTINGS = ClanSettingsInterface.INTERFACE
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.MEMBER_ROWS) { ClanSettingsInterface.showMember(player, player.getInteractingSlot()) }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.RANK_OPTIONS) { ClanSettingsInterface.chooseRank(player, player.getInteractingSlot()) }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.JOB_OPTIONS) { ClanSettingsInterface.chooseJob(player, player.getInteractingSlot()) }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.SAVE) { ClanSettingsInterface.save(player) }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.KICK) { ClanSettingsInterface.kick(player) }
ClanSettingsInterface.MEMBER_TOGGLES.keys.forEach { component ->
    on_button(interfaceId = SETTINGS, component = component) { ClanSettingsInterface.toggleMemberFlag(player, component) }
}
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.GUESTS_ENTER) { Clans.editSettings(player) { allowGuests = !allowGuests } }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.GUESTS_TALK) { Clans.editSettings(player) { guestsCanTalk = !guestsCanTalk } }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.RECRUITING) { Clans.editSettings(player) { recruiting = !recruiting } }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.CLAN_TIME) { Clans.editSettings(player) { clanTime = !clanTime } }
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.TIMEZONE_OPTIONS) {
    val key = player.getInteractingSlot()
    if (key in 0..144) Clans.editSettings(player) { timeZone = (key - 72) * 10 }
}
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.WORLD_OPTIONS) {
    val world = player.getInteractingSlot()
    if (world in 0..200) Clans.editSettings(player) { worldId = world }
}
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.EDIT_MOTTO) {
    player.queue {
        val text = inputString("Enter your clan motto:").replace('|', ' ').replace('\n', ' ').trim().take(80)
        Clans.editSettings(player) { motto = text.ifEmpty { null } }
    }
}
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.EDIT_KEYWORDS) {
    player.queue {
        val text = inputString("Enter keywords to identify with your clan:").replace('|', ' ').replace('\n', ' ').trim().take(80)
        Clans.editSettings(player) { keywords = text.ifEmpty { null } }
    }
}
on_button(interfaceId = SETTINGS, component = ClanSettingsInterface.EDIT_THREAD) {
    player.queue {
        val text = inputString("Enter the Thread ID of your official clan thread:").lowercase().filter { it.isLetterOrDigit() }.take(12)
        Clans.editSettings(player) { threadId = text.ifEmpty { null } }
    }
}
ClanSettingsInterface.RANK_TABS.forEach { (component, rank) ->
    on_button(interfaceId = SETTINGS, component = component) { ClanSettingsInterface.selectRank(player, rank) }
}
(ClanPermission.values().map { it.row } + ClanPermission.TALK_ROW + ClanPermission.KICK_ROW).forEach { row ->
    on_button(interfaceId = SETTINGS, component = row) { ClanSettingsInterface.permissionRow(player, row) }
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
            // Same path as the tab's Invite button: the target clicks the invitation line, then Accept on 1095.
            if (target == null) player.filterableMessage("That player isn't online.") else Clans.recruit(player, target)
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

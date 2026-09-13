import gg.rsmod.util.Misc

on_login {
    player.updateIgnoreList()
}

on_add_ignore {
    val ignoredUsername = Misc.formatForDisplay(player.getAddedIgnore())
    val playerExists = world.characterExists(ignoredUsername)

    if (!playerExists) {
        player.message("Unable to add name - unknown player.")
        return@on_add_ignore
    }

    gg.rsmod.plugins.content.inter.friends.SocialListPolicy.addRefusal(
        gg.rsmod.plugins.content.inter.friends.SocialListPolicy.ListType.IGNORES, ignoredUsername, player.ignoredPlayers,
    )?.let {
        player.message(it)
        return@on_add_ignore
    }
    player.ignoredPlayers.add(ignoredUsername)
    player.updateIgnoreList()
    player.updateOthersFriendLists()
}

on_delete_ignore {
    val deletedIgnore = Misc.formatForDisplay(player.getDeletedIgnore())

    player.ignoredPlayers.remove(deletedIgnore)
    player.updateIgnoreList()
    player.updateOthersFriendLists()
}

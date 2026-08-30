package gg.rsmod.plugins.content.mechanics.clan

on_command("clan") {
    val args = player.getCommandArgs()
    when (args.getOrNull(0)?.lowercase()) {
        "create" -> {
            val name = args.drop(1).joinToString(" ")
            if (name.isBlank()) {
                player.filterableMessage("Usage: ::clan create <name>")
            } else {
                Clans.create(player, name)
            }
        }
        "invite" -> {
            val targetName = args.drop(1).joinToString(" ")
            val target = player.world.players.firstOrNull { it.username.equals(targetName, ignoreCase = true) }
            if (target == null) {
                player.filterableMessage("That player isn't online.")
            } else {
                Clans.invite(player, target)
            }
        }
        "leave" -> Clans.leave(player)
        else -> player.filterableMessage("Usage: ::clan create <name> | ::clan invite <name> | ::clan leave")
    }
}

on_command("cc") {
    val message = player.getCommandArgs().joinToString(" ")
    if (message.isBlank()) {
        player.filterableMessage("Usage: ::cc <message>")
    } else {
        Clans.chat(player, message)
    }
}

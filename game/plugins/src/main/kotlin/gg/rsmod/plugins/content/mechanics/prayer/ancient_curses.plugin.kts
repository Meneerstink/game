package gg.rsmod.plugins.content.mechanics.prayer

on_command("curse") {
    val arg = player.getCommandArgs().getOrNull(0)?.lowercase()
    when (arg) {
        "turmoil" -> AncientCurses.toggleTurmoil(player)
        else -> player.filterableMessage("Usage: ::curse turmoil")
    }
}

package gg.rsmod.plugins.content.mechanics.prayer

on_command("curse") {
    val arg = player.getCommandArgs().getOrNull(0)?.lowercase()
    when (arg) {
        "unlock" -> AncientCurses.unlock(player)
        "turmoil" -> AncientCurses.toggleTurmoil(player)
        else -> player.filterableMessage("Usage: ::curse unlock | ::curse turmoil")
    }
}

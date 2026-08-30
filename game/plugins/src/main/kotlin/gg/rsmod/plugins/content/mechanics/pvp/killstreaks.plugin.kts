package gg.rsmod.plugins.content.mechanics.pvp

on_command("killstreaks") {
    val top = Killstreaks.top(10)
    if (top.isEmpty()) {
        player.filterableMessage("No killstreaks recorded yet.")
    } else {
        player.filterableMessage("Top killstreaks:")
        top.forEachIndexed { i, (name, streak) -> player.filterableMessage("${i + 1}. $name - $streak") }
    }
    val mine = player.attr[BEST_KILLSTREAK_ATTR] ?: 0
    val current = player.attr[CURRENT_KILLSTREAK_ATTR] ?: 0
    player.filterableMessage("Your best: $mine, current: $current.")
}

on_command("pkpoints") {
    val points = player.attr[PK_POINTS_ATTR] ?: 0
    player.filterableMessage("You have $points PK points.")
}

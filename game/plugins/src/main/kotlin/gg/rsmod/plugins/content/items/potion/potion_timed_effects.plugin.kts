package gg.rsmod.plugins.content.items.potion

// OSRS Wiki antifire potion pages: warning about 15 seconds before the protection ends, then the expiry message ([AntifirePotions]).
on_timer(AntifirePotions.ANTIFIRE_WARNING_TIMER) {
    if (player.timers.has(gg.rsmod.game.model.timer.ANTIFIRE_TIMER)) player.message(AntifirePotions.ANTIFIRE_WARNING)
}

on_timer(gg.rsmod.game.model.timer.ANTIFIRE_TIMER) {
    player.message(AntifirePotions.ANTIFIRE_EXPIRED)
}

on_timer(AntifirePotions.SUPER_ANTIFIRE_WARNING_TIMER) {
    if (player.timers.has(gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER)) player.message(AntifirePotions.SUPER_ANTIFIRE_WARNING)
}

on_timer(gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER) {
    player.message(AntifirePotions.SUPER_ANTIFIRE_EXPIRED)
}

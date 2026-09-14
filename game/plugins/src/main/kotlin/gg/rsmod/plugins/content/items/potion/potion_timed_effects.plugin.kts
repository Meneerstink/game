package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.timer.OVERLOAD_TIMER
import gg.rsmod.game.model.timer.PRAYER_RENEWAL_TIMER

/** RCV-010 A3: Overload refreshes and Prayer renewal ticks (see [PotionEffects]). */
on_timer(OVERLOAD_TIMER) {
    PotionEffects.tickOverload(player)
}

on_timer(PRAYER_RENEWAL_TIMER) {
    PotionEffects.tickPrayerRenewal(player)
}

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

on_player_death {
    PotionEffects.onDeath(player)
}

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

on_player_death {
    PotionEffects.onDeath(player)
}

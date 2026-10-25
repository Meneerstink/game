package gg.rsmod.plugins.content.mechanics.prayer

PrayerScrolls.SCROLLS.forEach { scroll ->
    on_item_option(item = scroll, option = "read") {
        val prayer = if (scroll == Items.DEXTEROUS_PRAYER_SCROLL) "Rigour" else "Augury"
        if (PrayerScrolls.read(player, scroll)) {
            PrayerScrolls.syncUnlocks(player)
            player.playSound(Sfx.TRAILBLAZER_UNLOCK_POWER)
            player.message("<col=ef1020>You study the scroll and learn the prayer $prayer.</col> (It also needs ${PrayerScrolls.DEFENCE_LEVEL} Defence.)")
        } else {
            player.message("You already know the prayer $prayer.")
        }
    }
}

// N358: the prayer book keeps Rigour and Augury grey until their scroll is read.
on_login {
    PrayerScrolls.syncUnlocks(player)
}

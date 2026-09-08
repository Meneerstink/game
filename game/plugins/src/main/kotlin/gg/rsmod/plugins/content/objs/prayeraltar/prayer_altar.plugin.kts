package gg.rsmod.plugins.content.objs.prayeraltar

import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.areas.home.FeroxObjects

private val ALTARS_PRAY_AT =
    setOf(
        Objs.ALTAR_27661, Objs.CHAOS_ALTAR, Objs.ALTAR_24343, Objs.ALTAR_2640, Objs.ALTAR, Objs.ALTAR_19145,
        // Ferox Enclave altar (imported LocType, option [Pray-at]).
        gg.rsmod.plugins.content.areas.home.FeroxObjects.ALTAR,
    )

private val ALTARS_PRAY =
    setOf(Objs.ALTAR_36972, Objs.ALTAR_OF_GUTHIX, Objs.ALTAR_34616, Objs.ALTAR_18254, Objs.ALTAR_39842)

/**
 * @author Kevin Senez <ksenez94@gmail.com>
 */
ALTARS_PRAY_AT.forEach { altar ->
    on_obj_option(obj = altar, "pray-at") {
        player.queue {
            player.animate(Anims.ALTAR_PRAY)
            player.filterableMessage("You recharge your Prayer points.")
            player.playSound(Sfx.PRAYER_RECHARGE)
            Prayers.rechargePrayerPoints(player)
            if (altar == FeroxObjects.ALTAR) {
                when (options("Use Normal Prayers.", "Use Ancient Curses.", "Unlock Ancient Curses (50,000 coins).", "Keep my prayer book.")) {
                    1 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
                    2 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
                    3 -> AncientCurses.unlock(player)
                }
            }
        }
    }
}

ALTARS_PRAY.forEach { altar ->
    on_obj_option(obj = altar, "pray") {
        player.queue {
            player.animate(Anims.ALTAR_PRAY)
            player.filterableMessage("You recharge your Prayer points.")
            player.playSound(Sfx.PRAYER_RECHARGE)
            Prayers.rechargePrayerPoints(player)
        }
    }
}

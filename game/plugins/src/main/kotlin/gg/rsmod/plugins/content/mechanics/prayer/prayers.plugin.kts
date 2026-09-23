package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.plugins.content.mechanics.prayer.Prayers.toggleQuickPrayers

on_player_death {
    Prayers.deactivateAll(player)
}

/**
 * Retribution's explosion must fire before life points are restored/reset by
 * [gg.rsmod.game.action.PlayerDeathAction] - see Retribution.kt for sourcing.
 */
on_player_pre_death {
    Retribution.onPlayerDeath(player)
}

/**
 * Restores prayer on death.
 */
on_player_death {
    Prayers.rechargePrayerPoints(player)
}

/**
 * Prayer drain.
 */
/*
 * Prayer drain. [Prayers.PRAYER_DRAIN] is declared `removeOnZero = false`, so once it reaches zero
 * `Pawn.timerCycle` keeps finding it expired and re-runs this handler every game tick - which is
 * exactly the cadence the authentic drain model needs, and why nothing re-arms it here.
 *
 * `Prayer.drainEffect` is the real RS drain-rate table (30 / 60 / 120 / 180 / 240 ...) and
 * [Prayers.drainPrayer] applies the real counter model to it: add the total drain effect once per
 * tick, spend one tenth of a Prayer point each time the counter reaches `60 + 2 * prayerBonus`.
 * The anchor is Protect from Melee (`drainEffect = 120`), which at zero prayer bonus costs two
 * tenths of a point per tick, i.e. one whole point every five ticks - the well-known three
 * seconds. The old `= 2` re-arm inside the early-return branch below only slowed down the
 * *idle* check, not the drain itself, so it is simply dropped.
 */
on_login {
    player.timers[Prayers.PRAYER_DRAIN] = 1
    Prayers.init(player)
    AncientCurses.syncBookVarbit(player)
}

on_timer(Prayers.PRAYER_DRAIN) {
    Prayers.drainPrayer(player)
}

/**
 * Toggle quick-prayers.
 */

on_button(interfaceId = 749, component = 1) {
    val option = player.getInteractingOption()
    toggleQuickPrayers(player, option)
}

/**
 * Activate prayers.
 */
on_button(interfaceId = 271, component = 8) {
    player.queue(TaskPriority.STRONG) {
        val buttonSlot = player.getInteractingSlot()
        // Curse book: the same grid shows the 20 curse slots (enum 862 order) while varbit 6840 is set.
        if (AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT) {
            AncientCurses.onBookButton(this, buttonSlot)
            return@queue
        }
        val prayer = Prayer.values().firstOrNull { it.slot == buttonSlot }

        if (prayer != null) {
            Prayers.toggle(this, prayer)
        }
    }
}

/**
 * Select quick-prayer.
 */
on_button(interfaceId = 271, component = 42) {
    val slot = player.getInteractingSlot()
    if (AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT) {
        AncientCurses.selectQuickCurse(player, slot)
        return@on_button
    }
    val prayer = Prayer.values.firstOrNull { prayer -> prayer.slot == slot } ?: return@on_button
    Prayers.selectQuickPrayer(this, prayer)
}

/**
 * Accept selected quick-prayer.
 */
on_button(interfaceId = 271, component = 43) {
    Prayers.confirmQuickPrayerSelection(player)
    player.openInterface(InterfaceDestination.PRAYER_TAB)
}

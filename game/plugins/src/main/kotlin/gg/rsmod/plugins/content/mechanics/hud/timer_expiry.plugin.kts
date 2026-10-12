package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.STAFF_OF_LIGHT_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.game.model.timer.VENOM_IMMUNITY
import gg.rsmod.plugins.content.items.potion.StaminaPotions

// Owner A7: a chat line when a timer runs out (texts and sources in TimerExpiry). Only a natural expiry fires these - a cure,
// death or a fresh dose that removes or resets the timer says nothing. The skull, Charge, Magic Imbue and divine potion
// timers already had an expiry hook; they write TimerExpiry's texts there.

on_timer(StaminaPotions.STAMINA_TIMER) {
    (ctx as? Player)?.message(TimerExpiry.STAMINA_EXPIRED)
}

on_timer(TimerExpiry.TELEBLOCK_WARNING_TIMER) {
    val p = ctx as? Player ?: return@on_timer
    if (p.timers.has(TELEBLOCK_TIMER)) p.message(TimerExpiry.TELEBLOCK_WARNING)
}

on_timer(TELEBLOCK_TIMER) {
    val p = ctx as? Player ?: return@on_timer
    p.timers.remove(TimerExpiry.TELEBLOCK_WARNING_TIMER)
    p.message(TimerExpiry.TELEBLOCK_EXPIRED)
}

on_timer(POISON_IMMUNITY) {
    (ctx as? Player)?.message(TimerExpiry.POISON_IMMUNITY_EXPIRED)
}

on_timer(VENOM_IMMUNITY) {
    (ctx as? Player)?.message(TimerExpiry.VENOM_IMMUNITY_EXPIRED)
}

// Pawn timers: an npc can carry these too (a monster's Vengeance, immunity) - only a player gets the line.
on_timer(VENGEANCE_COOLDOWN) {
    (ctx as? Player)?.message(TimerExpiry.VENGEANCE_READY)
}

on_timer(STAFF_OF_LIGHT_TIMER) {
    (ctx as? Player)?.message(TimerExpiry.SOTD_EXPIRED)
}

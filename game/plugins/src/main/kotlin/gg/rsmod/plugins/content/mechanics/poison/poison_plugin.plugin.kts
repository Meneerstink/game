package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.timer.POISON_TIMER

val poisonTickDelay = 30

/**
 * Callback for the poison timer. Deals damage to the pawn and decrements the number of ticks left for the poison effect.
 */
on_timer(POISON_TIMER) {
    val pawn = pawn // The pawn being affected by the poison effect
    // Ticks left = severity - 1 (Poison.poisonSeverity): 0 is the last hit (severity 1, damage 1), below 0 the poison has worn off.
    val ticksLeft = pawn.attr[POISON_TICKS_LEFT_ATTR] ?: -1

    // If the pawn is a player, and they have a modal open, reset the timer to 1 tick
    // Resetting the timer to 1 tick ensures that when the modal is closed, poison will continue
    if (pawn is Player) {
        if (pawn.interfaces.currentModal != -1) {
            pawn.timers[POISON_TIMER] = 1
            return@on_timer
        }
    }

    // Severity 0: the poison has worn off (OSRS Wiki "Poison": it expires "once the poison severity value reaches zero").
    if (ticksLeft < 0) {
        if (pawn is Player) {
            pawn.message("The poison has worn off.")
        }
        Poison.cure(pawn)
        return@on_timer
    }

    val poisonDamage = Poison.getDamageForTicks(ticksLeft)
    pawn.hit(damage = poisonDamage, type = HitType.POISON)
    gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDotDamage(pawn, poisonDamage)
    if (ticksLeft == 0) {
        // That was the severity-1 hit: the severity is now zero and the poison ends (orb back to normal) right away.
        if (pawn is Player) {
            pawn.message("The poison has worn off.")
        }
        Poison.cure(pawn)
        return@on_timer
    }
    pawn.attr[POISON_TICKS_LEFT_ATTR] = ticksLeft - 1

    // Set the timer for the next tick of the poison effect.
    pawn.timers[POISON_TIMER] = poisonTickDelay
}

// Reset the players poison varp on death
on_player_death {
    Poison.cure(player)
}

// Interface 748, component 2: the cache's real "Use Cure" HP-orb action.
on_button(748, 2) {
    Poison.cureFromInventory(player)
}

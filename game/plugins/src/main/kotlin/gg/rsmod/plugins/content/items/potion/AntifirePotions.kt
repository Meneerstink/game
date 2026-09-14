package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.heal

/**
 * Owner answer Q8 (2026-09-14, "Super antifire exactly like OSRS") and OSRS-IMPORT batch potions-antifire (OSRS Wiki raw wikitext):
 * - Antifire potion: "a single dose will provide complete immunity against dragonfire for 6 minutes" (with a shield); warning "after
 *   approximately 5 minutes, 45 seconds: Your antifire potion is about to expire." and "Your antifire potion has expired." at 6 minutes.
 * - Extended antifire: "12 minutes (1200 game ticks) per dose"; the same two messages "approximately 15 seconds" apart.
 * - Super antifire potion: "complete immunity to regular dragonfire for three minutes".
 * - Extended super antifire: "complete immunity against dragonfire for exactly six minutes"; "Your super antifire potion is about to
 *   expire." then "Your super antifire potion has expired." approximately 15 seconds later.
 * - Mixes: antifire / super antifire 3 minutes / extended antifire 12 minutes / extended super antifire 6 minutes of protection, "heals
 *   6 Hitpoints", "You drink the lumpy potion".
 * Messages are coloured #7f007f on the wiki. The dragonfire damage rules stay in DragonfireTable (a super timer outranks a regular one).
 * SOURCE_GAP: whether a new dose while protected restarts or extends the timer (the timer is restarted); the "approximately 15 seconds"
 * warning is taken as 25 ticks.
 */
object AntifirePotions {
    const val ANTIFIRE_TICKS = 600
    const val EXTENDED_ANTIFIRE_TICKS = 1200
    const val SUPER_ANTIFIRE_TICKS = 300
    const val EXTENDED_SUPER_ANTIFIRE_TICKS = 600
    const val WARNING_TICKS = 25
    const val MIX_HEAL = 6
    const val MIX_MESSAGE = "You drink the lumpy potion"

    const val ANTIFIRE_WARNING = "<col=7f007f>Your antifire potion is about to expire.</col>"
    const val ANTIFIRE_EXPIRED = "<col=7f007f>Your antifire potion has expired.</col>"
    const val SUPER_ANTIFIRE_WARNING = "<col=7f007f>Your super antifire potion is about to expire.</col>"
    const val SUPER_ANTIFIRE_EXPIRED = "<col=7f007f>Your super antifire potion has expired.</col>"

    val ANTIFIRE_WARNING_TIMER = TimerKey()
    val SUPER_ANTIFIRE_WARNING_TIMER = TimerKey()

    /** A regular antifire never replaces a running super antifire (it would downgrade the protection). */
    fun drinkAntifire(
        p: Player,
        ticks: Int,
    ) {
        if (p.timers.has(SUPER_ANTIFIRE_TIMER)) return
        p.timers[ANTIFIRE_TIMER] = ticks
        p.timers[ANTIFIRE_WARNING_TIMER] = ticks - WARNING_TICKS
    }

    fun drinkSuperAntifire(
        p: Player,
        ticks: Int,
    ) {
        p.timers[SUPER_ANTIFIRE_TIMER] = ticks
        p.timers[SUPER_ANTIFIRE_WARNING_TIMER] = ticks - WARNING_TICKS
    }

    fun heal(p: Player) {
        p.heal(MIX_HEAL)
    }
}

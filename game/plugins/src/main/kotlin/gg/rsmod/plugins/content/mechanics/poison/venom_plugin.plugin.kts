package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.timer.VENOM_TIMER

/**
 * Callback for the venom timer. Deals escalating damage to the pawn (see
 * [Venom.damageForTick]) and increments the number of venom ticks elapsed. Unlike poison,
 * venom never wears off on its own - it only ends via [Venom.cure] or
 * [Venom.downgradeToPoison] (real RS rule, sourced from the OSRS Wiki: "Venom will not
 * dissipate on its own").
 */
on_timer(VENOM_TIMER) {
    val pawn = pawn // The pawn being affected by the venom effect

    // Audit C-08: OSRS venom keeps hitting while an interface (bank, shop, ...) is open; the old modal guard paused it.

    val ticksElapsed = pawn.attr[VENOM_TICKS_ELAPSED_ATTR] ?: 0
    // Owner 2026-09-18: OSRS venom shows a black splat (HitType.VENOM -> client VenomHitmarkType), not the green poison one.
    val venomDamage = Venom.damageForTick(ticksElapsed)
    pawn.hit(damage = venomDamage, type = HitType.VENOM)
    // Audit X-10: damage over time also holds a disconnected player in the world (no x-log escape while poisoned).
    if (pawn is Player) gg.rsmod.plugins.content.combat.Combat.holdLogout(pawn)
    gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDotDamage(pawn, venomDamage)
    pawn.attr[VENOM_TICKS_ELAPSED_ATTR] = ticksElapsed + 1

    pawn.timers[VENOM_TIMER] = Venom.VENOM_TICK_DELAY
}

// Reset the player's poison orb (shared with venom) and venom state on death.
on_player_death {
    player.attr.remove(VENOM_TICKS_ELAPSED_ATTR)
    Poison.setPoisonVarp(player, Poison.OrbState.NONE)
}

package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.content.mechanics.run.RunEnergy

/**
 * OSRS-IMPORT step 4 potions-stamina (OSRS Wiki raw wikitext 2026-09-14):
 * - Stamina potion: "restores 20% of the player's run energy per dose, and offers a 70% reduction in run energy depletion for 2 minutes.
 *   This effect does not stack, and drinking additional doses before the timer has run out will simply reset it back to 2 minutes."
 *   Recipe: super energy(n) + n amylase crystals, 77 Herblore, 25.5 experience per dose.
 * - Stamina mix: "restores 20% of a player's run energy, reduces the rate at which it depletes by 70% for 2 minutes, and heals 6
 *   Hitpoints"; drinking gives "You drink the lumpy potion"; stamina potion(2) + caviar, 86 Herblore, 60 experience.
 * - Extended stamina potion: "restores 40% of the player's run energy per dose, and offers a 70% reduction in run energy depletion for 4
 *   minutes" (the same reset rule).
 * - Extreme energy potion: "restores 40% of the player's run energy per dose".
 * BLOCKED: extended stamina (marlin scales) and extreme energy (yellow fin) recipes - ingredients absent in 667; the Amylase crystal is
 * imported but has no source here (Mark of grace shop absent). SOURCE_GAP / ADAPTED: the orange run orb (no 667 client support), the ring of
 * endurance (absent), the Barbarian Herblore training gate (this server gates no mix recipe).
 */
object StaminaPotions {
    val STAMINA_TIMER = TimerKey()

    const val STAMINA_TICKS = 200
    const val EXTENDED_STAMINA_TICKS = 400
    const val DEPLETION_MULTIPLIER = 0.3
    const val STAMINA_RESTORE = 20.0
    const val EXTENDED_RESTORE = 40.0
    const val MIX_HEAL = 6
    const val MIX_MESSAGE = "You drink the lumpy potion"

    fun drink(
        p: Player,
        restore: Double,
        ticks: Int,
    ) {
        RunEnergy.renew(p, restore)
        if (ticks > 0) p.timers[STAMINA_TIMER] = ticks
    }

    fun drainMultiplier(p: Player): Double = if (p.timers.has(STAMINA_TIMER)) DEPLETION_MULTIPLIER else 1.0

    fun drinkMix(p: Player) {
        drink(p, STAMINA_RESTORE, STAMINA_TICKS)
        p.heal(MIX_HEAL)
    }
}

package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey

/**
 * PROJECT48 SIGILS - what the sigils do to eating, drinking and potions (OSRS Wiki Deadman: Armageddon/Sigils). One function per hook; the
 * food, potion and prayer code calls them in a single line.
 */
object SigilConsumables {
    /** S13 Food Master and S16 Potion Master: "this effect has a 6 second cooldown" (10 ticks). */
    private val FOOD_SAVED = TimerKey()
    private val POTION_SAVED = TimerKey()
    private const val SAVE_COOLDOWN_TICKS = 10

    /** S09 The Well Fed: a 30% chance to gain twice the number of hitpoints from any food or potion. */
    fun wellFed(
        player: Player,
        heal: Int,
    ): Int = if (heal > 0 && Sigils.effective(player, Sigil.THE_WELL_FED) && player.world.randomDouble() < 0.30) heal * 2 else heal

    /** S13 The Food Master: a 40% chance not to consume the food; after it triggered it cannot trigger again for 6 seconds. */
    fun keepsFood(player: Player): Boolean {
        if (!Sigils.effective(player, Sigil.THE_FOOD_MASTER) || player.timers.has(FOOD_SAVED)) return false
        if (player.world.randomDouble() >= 0.40) return false
        player.timers[FOOD_SAVED] = SAVE_COOLDOWN_TICKS
        return true
    }

    /** S16 The Potion Master: a 20% chance not to consume the potion dose; once triggered not again within 6 seconds. */
    fun keepsPotionDose(player: Player): Boolean {
        if (!Sigils.effective(player, Sigil.THE_POTION_MASTER) || player.timers.has(POTION_SAVED)) return false
        if (player.world.randomDouble() >= 0.20) return false
        player.timers[POTION_SAVED] = SAVE_COOLDOWN_TICKS
        return true
    }

    /** S22 Faith: a 40% chance to gain twice the prayer points from a prayer-restoring potion. */
    fun faith(
        player: Player,
        points: Int,
    ): Int = if (points > 0 && Sigils.effective(player, Sigil.FAITH) && player.world.randomDouble() < 0.40) points * 2 else points
}

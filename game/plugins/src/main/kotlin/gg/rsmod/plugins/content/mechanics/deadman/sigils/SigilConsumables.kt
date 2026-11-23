package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.message

/**
 * PROJECT48 SIGILS - what the sigils do to eating, drinking and potions (OSRS Wiki Deadman: Armageddon/Sigils). One function per hook; the
 * food, potion and prayer code calls them in a single line.
 */
object SigilConsumables {
    /** S13 Food Master and S16 Potion Master: "this effect has a 6 second cooldown" (10 ticks). */
    private val FOOD_SAVED = TimerKey()
    private val POTION_SAVED = TimerKey()
    private const val SAVE_COOLDOWN_TICKS = 10

    /**
     * S09 The Well Fed: a 30% chance to gain twice the number of hitpoints from any food or potion. Chat line = OCR of DMM video
     * z_PHEY6D9XY 23:18 / 23:56 / 24:14 (D01 PROJECT48 23-11): 'Your Sigil of the Well Fed doubles your healing!'.
     */
    fun wellFed(
        player: Player,
        heal: Int,
    ): Int {
        if (heal <= 0 || !Sigils.effective(player, Sigil.THE_WELL_FED) || player.world.randomDouble() >= 0.30) return heal
        player.message("Your Sigil of the Well Fed doubles your healing!")
        return heal * 2
    }

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
        // Chat line = OCR of DMM video z_PHEY6D9XY 24:26 / 24:42 (D01 PROJECT48 23-11).
        player.message("Your Sigil of the Potion Master saves a dose of your potion.")
        return true
    }

    /**
     * S22 Faith: a 40% chance to gain twice the prayer points from a prayer-restoring potion. The chat line is the real one
     * (D01 PROJECT48 23-11: OCR of DMM video z_PHEY6D9XY 18:04 / 18:10 / 24:00, 'Your Sigil of Faith doubles your prayer restoration!').
     */
    fun faith(
        player: Player,
        points: Int,
    ): Int {
        if (points <= 0 || !Sigils.effective(player, Sigil.FAITH) || player.world.randomDouble() >= 0.40) return points
        player.message("Your Sigil of Faith doubles your prayer restoration!")
        return points * 2
    }
}
